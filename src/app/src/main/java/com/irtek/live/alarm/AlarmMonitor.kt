package com.irtek.live.alarm

import android.content.Context
import android.util.Log
import com.irtek.live.R
import com.irtek.live.data.AppDatabase
import com.irtek.live.data.entity.AlarmMessage
import com.irtek.live.data.entity.DeviceEntity
import com.irtek.live.settings.AlarmLabels
import com.irtek.live.settings.AlarmNotifier
import com.irtek.netsdk.NativeSDK
import com.irtek.netsdk.NetSDKManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps one RTP alarm listener per device IP for the app lifetime.
 * Incoming alarms are persisted into Room [AlarmMessage].
 */
object AlarmMonitor {
    private const val TAG = "AlarmMonitor"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val listeners = ConcurrentHashMap<String, Long>() // ip -> sessionId

    @Volatile
    private var db: AppDatabase? = null

    @Volatile
    private var appContext: Context? = null

    fun init(database: AppDatabase, context: Context) {
        db = database
        appContext = context.applicationContext
        NetSDKManager.setAlarmListener(object : NativeSDK.AlarmListener {
            override fun onAlarm(sessionId: Long, ip: String, json: String) {
                val payloadIp = runCatching {
                    JSONObject(json)
                        .optJSONObject("thermal_temp")
                        ?.optJSONObject("dev_info")
                        ?.optString("ip")
                        .orEmpty()
                }.getOrDefault("")
                val resolvedIp = payloadIp.ifBlank { ip }.trim()
                Log.i(TAG, "onAlarm sid=$sessionId ip=$resolvedIp (cb=$ip) json=$json")
                scope.launch { handleAlarm(resolvedIp, json) }
            }
        })
    }

    suspend fun ensureListening(entity: DeviceEntity) {
        val ip = entity.ip.trim()
        if (ip.isEmpty()) return
        mutex.withLock {
            if (listeners.containsKey(ip)) return
        }

        val login = NetSDKManager.ensureLogin(
            entity.ip,
            entity.port,
            entity.userName,
            entity.password.ifBlank { "admin" }
        )
        val handle = login.data
        if (!login.isSuccess || handle == null || handle == 0L) {
            Log.w(TAG, "ensureListening login failed: $ip")
            return
        }

        mutex.withLock {
            if (listeners.containsKey(ip)) return
            val start = NetSDKManager.startAlarmListener(handle)
            val sid = start.data
            if (start.isSuccess && sid != null && sid > 0L) {
                listeners[ip] = sid
                Log.i(TAG, "alarm listener started: $ip sid=$sid")
            } else {
                Log.w(TAG, "startAlarmListener failed: $ip ${start.message}")
            }
        }
    }

    suspend fun stopListening(ip: String) {
        val key = ip.trim()
        val sid = mutex.withLock { listeners.remove(key) } ?: return
        NetSDKManager.stopAlarmListener(sid)
        Log.i(TAG, "alarm listener stopped: $key")
    }

    suspend fun stopAll() {
        val snapshot = mutex.withLock {
            val copy = listeners.toMap()
            listeners.clear()
            copy
        }
        snapshot.values.forEach { sid ->
            NetSDKManager.stopAlarmListener(sid)
        }
    }

    private suspend fun handleAlarm(ip: String, json: String) = withContext(Dispatchers.IO) {
        val database = db
        if (database == null) {
            Log.w(TAG, "handleAlarm skipped: db not ready")
            return@withContext
        }
        try {
            val root = JSONObject(json)
            val thermal = root.optJSONObject("thermal_temp")
            val device = database.deviceDao().getByIp(ip)
            if (device == null) {
                Log.w(TAG, "handleAlarm skipped: no device for ip=$ip")
                return@withContext
            }

            val message = thermal?.optString("message").orEmpty()
            // Device timestamp_us is not wall-clock; show local receive time.
            val timestamp = System.currentTimeMillis()

            var markerName = ""
            var level = 0
            thermal?.optJSONArray("marker_alarms")?.let { alarms ->
                for (i in 0 until alarms.length()) {
                    val a = alarms.optJSONObject(i) ?: continue
                    val lv = a.optInt("alarm_level", 0)
                    if (lv > 0) {
                        markerName = a.optString("marker_name")
                        level = lv
                        break
                    }
                }
            }

            val ctx = appContext
            val config = resolveMarkerAlarmConfig(device.ip, device.port, markerName)
            val alarmTypeCode = config?.optInt("alarm_type", 0) ?: 0
            val conditionLabel = (ctx?.let { AlarmLabels.condition(it, alarmTypeCode) } ?: "").ifBlank {
                ctx?.let {
                    if (root.optInt("type", 0) == 1) AlarmLabels.tempAlarm(it) else AlarmLabels.deviceAlarm(it)
                } ?: ""
            }
            val levelLabel = ctx?.let { AlarmLabels.level(it, level) } ?: ""
            val threshold = when (level) {
                1 -> config?.optDouble("alert_temp", 0.0) ?: 0.0
                2 -> config?.optDouble("warning_temp", 0.0) ?: 0.0
                3 -> config?.optDouble("alarm_temp", 0.0) ?: 0.0
                else -> 0.0
            }

            val tempInfo = findTempInfo(thermal, markerName)
            var temperature = pickTemperature(tempInfo, alarmTypeCode)
            if (temperature == 0.0) {
                temperature = pickTemperature(
                    thermal?.optJSONObject("global_temp_info"),
                    alarmTypeCode
                )
            }

            val title = buildString {
                append(conditionLabel)
                if (levelLabel.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(levelLabel)
                }
                if (markerName.isNotBlank() && markerName != "global") {
                    if (isNotEmpty()) append(" · ")
                    append(markerName)
                }
            }.ifBlank {
                message.take(32).ifBlank { ctx?.let { AlarmLabels.tempAlarm(it) } ?: "" }
            }

            val content = message.ifBlank {
                buildString {
                    append(conditionLabel)
                    if (levelLabel.isNotBlank()) append(" · $levelLabel")
                    if (markerName.isNotBlank()) append(" · $markerName")
                    if (temperature != 0.0) append(" · ${"%.1f".format(temperature)}")
                    if (threshold != 0.0) append(ctx?.getString(R.string.alarm_threshold_suffix, threshold) ?: "")
                }
            }

            val insertedId = database.alarmDao().insert(
                AlarmMessage(
                    deviceId = device.id,
                    type = conditionLabel,
                    title = title,
                    content = content,
                    temperature = temperature,
                    threshold = threshold,
                    markerName = markerName,
                    timestamp = timestamp
                )
            )
            appContext?.let { ctx ->
                AlarmNotifier.notifyAlarm(
                    context = ctx,
                    alarmId = insertedId,
                    title = title,
                    content = content,
                    deviceName = device.name
                )
            }
            database.deviceDao().updateStatus(device.id, DeviceEntity.STATUS_ALARM)
            Log.i(TAG, "alarm saved from $ip: $title")
        } catch (e: Exception) {
            Log.e(TAG, "handleAlarm failed ip=$ip", e)
        }
    }

    private suspend fun resolveMarkerAlarmConfig(
        ip: String,
        port: Int,
        markerName: String
    ): JSONObject? {
        if (markerName.isBlank()) return null
        val handle = NetSDKManager.findHandle(ip, port) ?: return null
        val result = NetSDKManager.getThermalAlarms(handle)
        val arr = result.data ?: return null
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("marker_name") == markerName) return obj
        }
        return null
    }

    private fun findTempInfo(thermal: JSONObject?, markerName: String): JSONObject? {
        if (thermal == null) return null
        if (markerName == "global" || markerName.isBlank()) {
            return thermal.optJSONObject("global_temp_info")
        }
        thermal.optJSONArray("marker_temps")?.let { temps ->
            for (i in 0 until temps.length()) {
                val t = temps.optJSONObject(i) ?: continue
                if (t.optString("marker_name") == markerName) {
                    return t.optJSONObject("temp_point_info")
                }
            }
        }
        return thermal.optJSONObject("global_temp_info")
    }

    private fun pickTemperature(tempInfo: JSONObject?, alarmType: Int): Double {
        if (tempInfo == null) return 0.0
        val max = tempInfo.optJSONObject("max")?.optDouble("temperature", 0.0) ?: 0.0
        val min = tempInfo.optJSONObject("min")?.optDouble("temperature", 0.0) ?: 0.0
        val avg = tempInfo.optDouble("avg_temp", 0.0)
        return when (alarmType) {
            1, 2 -> max
            3, 4 -> min
            5, 6 -> avg
            7, 8 -> max - min
            else -> max
        }
    }

}
