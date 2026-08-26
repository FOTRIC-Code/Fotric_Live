package com.irtek.live.alarm

import android.content.Context
import android.util.Log
import com.irtek.live.R
import com.irtek.live.data.AppDatabase
import com.irtek.live.data.entity.AlarmMarkerDetail
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps one RTP alarm listener per device IP for the app lifetime.
 * Incoming alarms are persisted into Room [AlarmMessage].
 */
object AlarmMonitor {
    private const val TAG = "AlarmMonitor"
    private const val CONFIG_CACHE_TTL_MS = 30_000L
    private const val CAPTURE_MIN_INTERVAL_MS = 8_000L
    private const val EXCEPTION_VIDEO_LOST = 3

    private data class ListenerState(val sessionId: Long, val handle: Long)
    private data class ConfigCache(val atMs: Long, val byName: Map<String, JSONObject>)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val captureMutex = Mutex()
    private val listeners = ConcurrentHashMap<String, ListenerState>() // ip -> state
    private val configCache = ConcurrentHashMap<String, ConfigCache>() // ip -> cache
    private val lastCaptureAt = ConcurrentHashMap<String, Long>()

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
                Log.i(TAG, "onAlarm sid=$sessionId ip=$resolvedIp (cb=$ip)")
                scope.launch { handleAlarm(resolvedIp, json) }
            }
        })
        NetSDKManager.setEventListener(object : NativeSDK.EventListener {
            override fun onEvent(handle: Long, eventType: Int, streamId: Int, message: String) {
                Log.w(TAG, "sdk event handle=$handle type=$eventType sid=$streamId msg=$message")
                if (eventType != EXCEPTION_VIDEO_LOST) return
                // Only rebind when the alarm RTP itself dropped (not preview/thermal lost).
                if (!message.contains("alarm", ignoreCase = true)) return
                scope.launch {
                    val session = NetSDKManager.loggedInSessions().find { it.handle == handle }
                    val ip = session?.ip?.trim().orEmpty().ifEmpty {
                        listeners.entries.firstOrNull { it.value.handle == handle }?.key.orEmpty()
                    }
                    if (ip.isEmpty()) return@launch
                    Log.w(TAG, "alarm RTP lost for $ip – restarting listener")
                    restartListening(ip)
                }
            }
        })
    }

    /** Call from Activity.onResume to recover silently dropped alarm RTP sessions. */
    fun onAppResumed() {
        scope.launch { reensureAll(forceRestart = false) }
    }

    /**
     * Ensure RTP alarm listener is running for [entity].
     * Restarts automatically when the login handle changed (e.g. after re-login).
     */
    suspend fun ensureListening(entity: DeviceEntity, forceRestart: Boolean = false) {
        val ip = entity.ip.trim()
        if (ip.isEmpty()) return
        if (db?.deviceDao()?.getByIp(ip) == null) return

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
        if (db?.deviceDao()?.getByIp(ip) == null) {
            NetSDKManager.logoutByIp(entity.ip, entity.port)
            return
        }

        val existing = listeners[ip]
        if (!forceRestart && existing != null && existing.handle == handle) {
            return
        }
        if (existing != null) {
            listeners.remove(ip)
            runCatching { NetSDKManager.stopAlarmListener(existing.sessionId) }
            Log.i(TAG, "ensureListening replacing listener ip=$ip force=$forceRestart")
        }

        // Receive VIDEO_LOST when alarm RTP drops so we can rebind.
        runCatching { NativeSDK.nativeSetEventCallback(handle, true) }

        val start = NetSDKManager.startAlarmListener(handle)
        val sid = start.data
        if (start.isSuccess && sid != null && sid > 0L) {
            val raced = listeners.putIfAbsent(ip, ListenerState(sid, handle))
            if (raced != null) {
                NetSDKManager.stopAlarmListener(sid)
            } else {
                Log.i(TAG, "alarm listener started: $ip sid=$sid handle=$handle")
            }
        } else {
            Log.w(TAG, "startAlarmListener failed: $ip ${start.message}")
        }
    }

    /** Force stop+start for one IP (e.g. after RTP disconnect). */
    suspend fun restartListening(ip: String) {
        val key = ip.trim()
        if (key.isEmpty()) return
        val entity = db?.deviceDao()?.getByIp(key) ?: return
        stopListening(key)
        ensureListening(entity, forceRestart = true)
    }

    /** Re-bind listeners for all saved devices (call on UI resume / messages tab). */
    suspend fun reensureAll(forceRestart: Boolean = false) = withContext(Dispatchers.IO) {
        val database = db ?: return@withContext
        val devices = database.deviceDao().getAllOnce()
        for (entity in devices) {
            try {
                ensureListening(entity, forceRestart = forceRestart)
            } catch (e: Exception) {
                Log.e(TAG, "reensureAll failed ip=${entity.ip}", e)
            }
        }
    }

    suspend fun stopListening(ip: String) {
        val key = ip.trim()
        val state = listeners.remove(key) ?: return
        NetSDKManager.stopAlarmListener(state.sessionId)
        configCache.remove(key)
        Log.i(TAG, "alarm listener stopped: $key")
    }

    suspend fun stopAll() {
        val snapshot = listeners.toMap()
        listeners.clear()
        configCache.clear()
        snapshot.values.forEach { state ->
            NetSDKManager.stopAlarmListener(state.sessionId)
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

            val activeAlarms = mutableListOf<Pair<String, Int>>() // markerName -> level
            thermal?.optJSONArray("marker_alarms")?.let { alarms ->
                for (i in 0 until alarms.length()) {
                    val a = alarms.optJSONObject(i) ?: continue
                    val lv = a.optInt("alarm_level", 0)
                    if (lv > 0) {
                        val name = a.optString("marker_name")
                        if (name.isNotBlank()) activeAlarms.add(name to lv)
                    }
                }
            }
            if (activeAlarms.isEmpty()) {
                Log.w(TAG, "handleAlarm skipped: no active marker_alarms ip=$ip")
                return@withContext
            }

            val ctx = appContext
            val configByName = loadAlarmConfigs(device.ip, device.port)
            val markerDetails = mutableListOf<AlarmMarkerDetail>()
            for ((markerName, level) in activeAlarms) {
                val config = configByName[markerName]
                // Only drop when config is present and explicitly disabled.
                // Missing config (fetch fail / not yet cached) must not suppress alarms.
                if (config != null && config.optInt("enabled", 0) != 1) {
                    Log.i(TAG, "skip marker=$markerName: enabled!=1")
                    continue
                }
                val alarmTypeCode = config?.optInt("alarm_type", 0) ?: 0
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
                markerDetails.add(
                    AlarmMarkerDetail(
                        markerName = markerName,
                        level = level,
                        alarmType = alarmTypeCode,
                        temperature = temperature,
                        threshold = threshold
                    )
                )
            }
            if (markerDetails.isEmpty()) {
                Log.i(TAG, "handleAlarm skipped: all markers disabled ip=$ip")
                return@withContext
            }

            // Primary fields keep the highest-level marker for list/compat.
            val primary = markerDetails.maxByOrNull { it.level } ?: markerDetails.first()
            val alarmTypeCode = primary.alarmType
            val conditionLabel = (ctx?.let { AlarmLabels.condition(it, alarmTypeCode) } ?: "").ifBlank {
                ctx?.let {
                    if (root.optInt("type", 0) == 1) AlarmLabels.tempAlarm(it) else AlarmLabels.deviceAlarm(it)
                } ?: ""
            }
            val levelLabel = ctx?.let { AlarmLabels.level(it, primary.level) } ?: ""
            val markerNamesLabel = markerDetails.joinToString(" · ") { d ->
                if (d.markerName == "global") ctx?.getString(R.string.common_global) ?: d.markerName
                else d.markerName
            }

            val title = buildString {
                append(conditionLabel)
                if (levelLabel.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(levelLabel)
                }
                if (markerNamesLabel.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(markerNamesLabel)
                }
            }.ifBlank {
                message.take(32).ifBlank { ctx?.let { AlarmLabels.tempAlarm(it) } ?: "" }
            }

            val content = message.ifBlank {
                buildString {
                    append(conditionLabel)
                    if (levelLabel.isNotBlank()) append(" · $levelLabel")
                    if (markerNamesLabel.isNotBlank()) append(" · $markerNamesLabel")
                    markerDetails.forEach { d ->
                        if (d.temperature != 0.0) append(" · ${"%.1f".format(d.temperature)}")
                        if (d.threshold != 0.0) {
                            append(ctx?.getString(R.string.alarm_threshold_suffix, d.threshold) ?: "")
                        }
                    }
                }
            }

            val insertedId = database.alarmDao().insert(
                AlarmMessage(
                    deviceId = device.id,
                    type = conditionLabel,
                    title = title,
                    content = content,
                    temperature = primary.temperature,
                    threshold = primary.threshold,
                    markerName = primary.markerName,
                    markersJson = AlarmMessage.markersToJson(markerDetails),
                    timestamp = timestamp
                )
            )
            appContext?.let { c ->
                AlarmNotifier.notifyAlarm(
                    context = c,
                    alarmId = insertedId,
                    title = title,
                    content = content,
                    deviceName = device.name
                )
            }
            database.deviceDao().updateStatus(device.id, DeviceEntity.STATUS_ALARM)
            val imagePath = captureAlarmImage(device, insertedId, timestamp)
            if (imagePath.isNotBlank()) {
                database.alarmDao().updateImagePath(insertedId, imagePath)
            }
            Log.i(TAG, "alarm saved from $ip: $title image=$imagePath")
        } catch (e: Exception) {
            Log.e(TAG, "handleAlarm failed ip=$ip", e)
        }
    }

    private suspend fun captureAlarmImage(
        device: DeviceEntity,
        alarmId: Long,
        timestamp: Long
    ): String {
        val ctx = appContext ?: return ""
        val now = System.currentTimeMillis()
        val last = lastCaptureAt[device.ip] ?: 0L
        if (now - last < CAPTURE_MIN_INTERVAL_MS) {
            return ""
        }
        val dir = File(ctx.filesDir, "alarms")
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "captureAlarmImage: cannot create $dir")
            return ""
        }
        val ipSafe = device.ip.replace('.', '_')
        val file = File(dir, "alarm_${ipSafe}_${timestamp}_${alarmId}.jpg")
        return captureMutex.withLock {
            val gated = System.currentTimeMillis()
            val prev = lastCaptureAt[device.ip] ?: 0L
            if (gated - prev < CAPTURE_MIN_INTERVAL_MS) {
                return@withLock ""
            }
            lastCaptureAt[device.ip] = gated
            try {
                val handle = NetSDKManager.findHandle(device.ip, device.port)
                    ?: NetSDKManager.ensureLogin(
                        device.ip,
                        device.port,
                        device.userName,
                        device.password.ifBlank { "admin" }
                    ).data
                if (handle == null || handle == 0L) {
                    Log.w(TAG, "captureAlarmImage: no session for ${device.ip}")
                    return@withLock ""
                }
                val result = NetSDKManager.getThermalCapture(0, file.absolutePath, handle)
                if (result.isSuccess && file.exists() && file.length() > 0L) {
                    file.absolutePath
                } else {
                    Log.w(TAG, "captureAlarmImage failed: ${device.ip} ${result.message}")
                    if (file.exists()) file.delete()
                    ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "captureAlarmImage exception ip=${device.ip}", e)
                if (file.exists()) file.delete()
                ""
            }
        }
    }

    fun deleteImageFiles(paths: Collection<String>) {
        paths.forEach { path ->
            if (path.isBlank()) return@forEach
            runCatching { File(path).delete() }
        }
    }

    /** marker_name -> config JSON (includes enabled / thresholds / alarm_type). */
    private suspend fun loadAlarmConfigs(ip: String, port: Int): Map<String, JSONObject> {
        val key = ip.trim()
        val cached = configCache[key]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.atMs < CONFIG_CACHE_TTL_MS) {
            return cached.byName
        }
        val handle = NetSDKManager.findHandle(ip, port)
        if (handle == null) {
            return cached?.byName ?: emptyMap()
        }
        val result = NetSDKManager.getThermalAlarms(handle)
        val arr: JSONArray? = result.data
        if (!result.isSuccess || arr == null) {
            Log.w(TAG, "getThermalAlarms failed ip=$ip – using cache")
            return cached?.byName ?: emptyMap()
        }
        val map = LinkedHashMap<String, JSONObject>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("marker_name")
            if (name.isNotBlank()) map[name] = obj
        }
        configCache[key] = ConfigCache(now, map)
        return map
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
