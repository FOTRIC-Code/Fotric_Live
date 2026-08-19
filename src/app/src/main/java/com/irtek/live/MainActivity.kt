package com.irtek.live

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.irtek.live.alarm.AlarmMonitor
import com.irtek.live.data.AppDatabase
import com.irtek.live.data.entity.DeviceEntity
import com.irtek.live.settings.LocaleHelper
import com.irtek.live.ui.adddevice.AddDeviceScreen
import com.irtek.live.ui.adddevice.ManualAddScreen
import com.irtek.live.ui.adddevice.OnlineAddScreen
import com.irtek.live.ui.device.DeviceScreen
import com.irtek.live.ui.devicelist.*
import com.irtek.live.ui.gallery.GalleryScreen
import com.irtek.live.ui.message.MessageItem
import com.irtek.live.ui.message.MessageScreen
import com.irtek.live.ui.mine.MineScreen
import com.irtek.live.ui.preview.PreviewDevice
import com.irtek.live.ui.preview.PreviewScreen
import com.irtek.live.ui.theme.LiveTheme
import com.irtek.netsdk.NetSDKManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Live)
        super.onCreate(savedInstanceState)
        val db = (application as LiveApp).database
        enableEdgeToEdge()
        setContent {
            LiveTheme {
                val scope = rememberCoroutineScope()
                var screen by remember { mutableStateOf<Screen>(Screen.Splash) }
                var didWarmLogin by remember { mutableStateOf(false) }
                var selectedNav by remember {
                    mutableIntStateOf(if (intent?.getBooleanExtra("open_messages", false) == true) 1 else 0)
                }
                val captureDir = remember { File(filesDir, "captures") }
                val recordDir = remember { File(filesDir, "records") }

                val deviceEntities by db.deviceDao().getAll().collectAsState(initial = emptyList())

                val devices = remember(deviceEntities) {
                    deviceEntities.map { e ->
                        DeviceItem(
                            id = e.id.toString(),
                            name = e.name,
                            status = when (e.status) {
                                DeviceEntity.STATUS_ONLINE -> DeviceStatus.ONLINE
                                DeviceEntity.STATUS_ALARM -> DeviceStatus.ALARM
                                else -> DeviceStatus.OFFLINE
                            },
                            model = e.model,
                            ip = e.ip,
                            sn = e.serialNo,
                            thumbnailPath = e.thumbnailPath,
                            updatedAt = e.updatedAt
                        )
                    }
                }

                var isRefreshing by remember { mutableStateOf(false) }

                val allAlarms by db.alarmDao().getAll().collectAsState(initial = emptyList())
                val unknownDeviceName = stringResource(R.string.common_unknown_device)
                val messageItems = remember(allAlarms, deviceEntities, unknownDeviceName) {
                    allAlarms.map { alarm ->
                        val device = deviceEntities.find { it.id == alarm.deviceId }
                        MessageItem(
                            alarm = alarm,
                            deviceName = device?.name ?: unknownDeviceName,
                            thumbnailPath = alarm.imagePath.ifBlank { device?.thumbnailPath ?: "" }
                        )
                    }
                }

                when (val s = screen) {
                    is Screen.Splash -> {
                        com.irtek.live.ui.splash.SplashScreen(
                            onFinished = { screen = Screen.DeviceList }
                        )
                    }
                    is Screen.DeviceList -> {
                        // Keep all devices logged in for the app lifetime.
                        LaunchedEffect(Unit) {
                            if (!didWarmLogin) {
                                didWarmLogin = true
                                val thumbDir = File(filesDir, "thumbnails")
                                runListRefresh(db, thumbDir) { refreshing -> isRefreshing = refreshing }
                            }
                        }
                        DeviceListScreen(
                            devices = devices,
                            isRefreshing = isRefreshing,
                            selectedNav = selectedNav,
                            onRefresh = {
                                isRefreshing = true
                                scope.launch {
                                    runListRefresh(db, File(filesDir, "thumbnails")) { refreshing ->
                                        isRefreshing = refreshing
                                    }
                                }
                            },
                            onAddDevice = { screen = Screen.AddDevice },
                            onDeviceClick = { device ->
                                if (device.status != DeviceStatus.OFFLINE) {
                                    val entity = deviceEntities.find { it.id.toString() == device.id }
                                    if (entity != null) {
                                        screen = Screen.Preview(
                                            entity.id, entity.name, entity.ip, entity.thumbnailPath
                                        )
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                NetSDKManager.ensureLogin(
                                                    entity.ip, entity.port,
                                                    entity.userName,
                                                    entity.password.ifBlank { "admin" }
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            onDeleteDevice = { device ->
                                val id = device.id.toLongOrNull() ?: return@DeviceListScreen
                                scope.launch {
                                    val entity = withContext(Dispatchers.IO) {
                                        val e = db.deviceDao().getById(id)
                                        val paths = e?.let {
                                            db.alarmDao().getImagePathsByDevice(it.id)
                                        }.orEmpty()
                                        // Remove the row first so the list updates even if JNI logout blocks.
                                        db.deviceDao().deleteById(id)
                                        AlarmMonitor.deleteImageFiles(paths)
                                        e
                                    }
                                    if (entity != null) {
                                        launch(Dispatchers.IO) {
                                            runCatching {
                                                AlarmMonitor.stopListening(entity.ip)
                                                NetSDKManager.logoutByIp(entity.ip, entity.port)
                                            }
                                        }
                                    }
                                }
                            },
                            onAlarmConfig = { device ->
                                val entity = deviceEntities.find { it.id.toString() == device.id }
                                if (entity != null) {
                                    screen = Screen.AlarmConfig(entity.id, entity.name, entity.ip)
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            NetSDKManager.ensureLogin(
                                                entity.ip, entity.port,
                                                entity.userName,
                                                entity.password.ifBlank { "admin" }
                                            )
                                        }
                                    }
                                }
                            },
                            onMaintenance = { device ->
                                val entity = deviceEntities.find { it.id.toString() == device.id }
                                if (entity != null) {
                                    screen = Screen.Maintenance(entity.id, entity.name, entity.ip)
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            NetSDKManager.ensureLogin(
                                                entity.ip, entity.port,
                                                entity.userName,
                                                entity.password.ifBlank { "admin" }
                                            )
                                        }
                                    }
                                }
                            },
                            onEditDevice = { device ->
                                val entity = deviceEntities.find { it.id.toString() == device.id }
                                if (entity != null) {
                                    screen = Screen.EditDevice(entity.id, entity.name, entity.ip)
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            NetSDKManager.ensureLogin(
                                                entity.ip, entity.port,
                                                entity.userName,
                                                entity.password.ifBlank { "admin" }
                                            )
                                        }
                                    }
                                }
                            },
                            onNavSelect = { selectedNav = it },
                            messageContent = {
                                MessageScreen(
                                    messages = messageItems,
                                    onDelete = { ids ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                if (ids.isNotEmpty()) {
                                                    AlarmMonitor.deleteImageFiles(
                                                        db.alarmDao().getByIds(ids).map { it.imagePath }
                                                    )
                                                    db.alarmDao().deleteByIds(ids)
                                                }
                                            }
                                        }
                                    },
                                    onMarkRead = { id ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                db.alarmDao().markRead(id)
                                            }
                                        }
                                    }
                                )
                            },
                            galleryContent = {
                                GalleryScreen(
                                    captureDir = captureDir,
                                    recordDir = recordDir
                                )
                            },
                            mineContent = {
                                MineScreen(
                                    onLanguageChanged = { recreate() }
                                )
                            }
                        )
                    }
                    is Screen.AddDevice -> {
                        BackHandler { screen = Screen.DeviceList }
                        AddDeviceScreen(
                            onBack = { screen = Screen.DeviceList },
                            onManualAdd = { screen = Screen.ManualAdd },
                            onOnlineAdd = { screen = Screen.OnlineAdd }
                        )
                    }
                    is Screen.ManualAdd -> {
                        BackHandler { screen = Screen.AddDevice }
                        ManualAddScreen(
                            existingIps = deviceEntities.map { it.ip.trim() }.toSet(),
                            onBack = { screen = Screen.AddDevice },
                            onConnected = { ip, userDeviceName ->
                                scope.launch {
                                    val thumbDir = File(filesDir, "thumbnails")
                                    // Keep session logged in for the rest of the app lifetime.
                                    saveDeviceAfterLogin(db, ip, thumbDir, userDeviceName)
                                }
                                screen = Screen.DeviceList
                            }
                        )
                    }
                    is Screen.OnlineAdd -> {
                        BackHandler { screen = Screen.AddDevice }
                        OnlineAddScreen(
                            addedIps = deviceEntities.map { it.ip.trim() }.toSet(),
                            addedSerials = deviceEntities.map { it.serialNo.trim() }.filter { it.isNotEmpty() }.toSet(),
                            onBack = { screen = Screen.AddDevice },
                            onDeviceAdd = { deviceJson ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        val thumbDir = File(filesDir, "thumbnails")
                                        saveDeviceByIp(
                                            db,
                                            deviceJson.optString("ip"),
                                            deviceJson.optString("name"),
                                            deviceJson.optString("model"),
                                            deviceJson.optString("serial_no"),
                                            deviceJson.optString("firmware_version"),
                                            thumbDir
                                        )
                                    }
                                }
                            }
                        )
                    }
                    is Screen.Preview -> {
                        val leavePreview = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    // Stop preview stream only; keep device login until app exit.
                                    NetSDKManager.stopStream()
                                }
                            }
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            screen = Screen.DeviceList
                        }
                        BackHandler { leavePreview() }
                        val entity = deviceEntities.find { it.id == s.deviceId }
                        val previewDevice = PreviewDevice(
                            id = s.deviceId,
                            name = s.name,
                            ip = s.ip,
                            thumbnailPath = s.thumbnailPath,
                            port = entity?.port ?: 80,
                            userName = entity?.userName ?: "admin",
                            password = entity?.password ?: ""
                        )
                        val recentAlarms by db.alarmDao()
                            .getRecentByDevice(s.deviceId, 3)
                            .collectAsState(initial = emptyList())

                        PreviewScreen(
                            device = previewDevice,
                            alarms = recentAlarms,
                            captureDir = captureDir,
                            recordDir = recordDir,
                            onBack = { leavePreview() },
                            onCalibrate = {
                                scope.launch { NetSDKManager.thermalCalibrate() }
                            },
                            onEditDevice = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        NetSDKManager.stopStream()
                                    }
                                }
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                screen = Screen.EditDevice(
                                    deviceId = s.deviceId,
                                    name = s.name,
                                    ip = s.ip,
                                    fromPreview = true,
                                    thumbnailPath = s.thumbnailPath
                                )
                            },
                            onMarkAlarmRead = { id ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        db.alarmDao().markRead(id)
                                    }
                                }
                            }
                        )
                    }
                    is Screen.DeviceDetail -> {
                        BackHandler { screen = Screen.DeviceList }
                        DeviceScreen(
                            deviceIp = s.ip,
                            onDisconnect = { screen = Screen.DeviceList }
                        )
                    }
                    is Screen.AlarmConfig -> {
                        BackHandler { screen = Screen.DeviceList }
                        com.irtek.live.ui.alarm.AlarmConfigScreen(
                            deviceIp = s.ip,
                            deviceName = s.name,
                            onBack = { screen = Screen.DeviceList }
                        )
                    }
                    is Screen.Maintenance -> {
                        BackHandler { screen = Screen.DeviceList }
                        com.irtek.live.ui.maintenance.DeviceMaintenanceScreen(
                            deviceIp = s.ip,
                            deviceName = s.name,
                            onBack = { screen = Screen.DeviceList }
                        )
                    }
                    is Screen.EditDevice -> {
                        val leaveEdit = {
                            if (s.fromPreview) {
                                screen = Screen.Preview(s.deviceId, s.name, s.ip, s.thumbnailPath)
                            } else {
                                screen = Screen.DeviceList
                            }
                        }
                        BackHandler { leaveEdit() }
                        com.irtek.live.ui.edit.DeviceEditScreen(
                            deviceIp = s.ip,
                            deviceName = s.name,
                            onBack = { leaveEdit() }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // Application.onTerminate is unreliable on real devices; release all logins here.
        if (isFinishing && !isChangingConfigurations) {
            kotlinx.coroutines.runBlocking {
                AlarmMonitor.stopAll()
            }
            NetSDKManager.cleanup()
        }
        super.onDestroy()
    }
}

private suspend fun saveDeviceAfterLogin(db: AppDatabase, ip: String, thumbDir: File, userDeviceName: String = "") {
    withContext(Dispatchers.IO) {
        if (!thumbDir.exists()) thumbDir.mkdirs()
        var name = userDeviceName.ifBlank { ip }
        var model = ""
        var sn = ""
        var fw = ""
        var thumbPath = ""
        val info = NetSDKManager.getDeviceInfo()
        if (info.isSuccess && info.data != null) {
            val d = info.data!!
            if (userDeviceName.isBlank()) {
                name = d.optString("name").ifBlank { ip }
            }
            model = d.optString("model")
            sn = d.optString("serial_no")
            fw = d.optString("firmware_version")
        }
        if (db.deviceDao().getByIp(ip.trim()) != null) return@withContext
        thumbPath = captureThumb(thumbDir, ip)
        db.deviceDao().insert(
            DeviceEntity(
                name = name, ip = ip.trim(), model = model,
                serialNo = sn, firmwareVersion = fw,
                status = DeviceEntity.STATUS_ONLINE,
                thumbnailPath = thumbPath
            )
        )
    }
}

private suspend fun saveDeviceByIp(
    db: AppDatabase, ip: String, discoveredName: String,
    fallbackModel: String, fallbackSn: String, fallbackFw: String,
    thumbDir: File
) {
    withContext(Dispatchers.IO) {
        if (!thumbDir.exists()) thumbDir.mkdirs()
        val ipKey = ip.trim()
        if (ipKey.isEmpty() || db.deviceDao().getByIp(ipKey) != null) return@withContext
        if (fallbackSn.isNotBlank() &&
            db.deviceDao().getAllOnce().any { it.serialNo.equals(fallbackSn.trim(), ignoreCase = true) }
        ) {
            return@withContext
        }
        var name = discoveredName.ifBlank { ipKey }
        var model = fallbackModel
        var sn = fallbackSn
        var fw = fallbackFw
        var thumbPath = ""
        val loginResult = NetSDKManager.login(ipKey, 80, "admin", "admin")
        if (loginResult.isSuccess) {
            val info = NetSDKManager.getDeviceInfo()
            if (info.isSuccess && info.data != null) {
                val d = info.data!!
                name = d.optString("name").ifBlank { name }
                model = d.optString("model").ifBlank { fallbackModel }
                sn = d.optString("serial_no").ifBlank { fallbackSn }
                fw = d.optString("firmware_version").ifBlank { fallbackFw }
            }
            thumbPath = captureThumb(thumbDir, ipKey)
            // Keep login; logout only on app exit or device delete.
        }
        db.deviceDao().insert(
            DeviceEntity(
                name = name, ip = ipKey, model = model,
                serialNo = sn, firmwareVersion = fw,
                status = DeviceEntity.STATUS_OFFLINE,
                thumbnailPath = thumbPath
            )
        )
    }
}

/**
 * Always clears the PTR spinner, even if native HTTP/JNI never returns.
 * Status work runs on a sibling job; joining it with a timeout is cancellable.
 */
private suspend fun CoroutineScope.runListRefresh(
    db: AppDatabase,
    thumbDir: File,
    setRefreshing: (Boolean) -> Unit
) {
    setRefreshing(true)
    val statusJob = launch(Dispatchers.IO) { refreshAllDevices(db) }
    try {
        withTimeoutOrNull(12_000) { statusJob.join() }
    } finally {
        setRefreshing(false)
    }
    launch(Dispatchers.IO) { refreshAllThumbs(db, thumbDir) }
}

private suspend fun refreshAllDevices(db: AppDatabase) {
    withContext(Dispatchers.IO) {
        val allDevices = db.deviceDao().getAllOnce()
        if (allDevices.isEmpty()) return@withContext

        for (entity in allDevices) {
            try {
                refreshOneDevice(db, entity)
            } catch (_: Exception) {
                db.deviceDao().updateStatus(entity.id, DeviceEntity.STATUS_OFFLINE)
            }
        }
    }
}

private suspend fun refreshAllThumbs(db: AppDatabase, thumbDir: File) {
    withContext(Dispatchers.IO) {
        if (!thumbDir.exists()) thumbDir.mkdirs()
        for (entity in db.deviceDao().getAllOnce()) {
            val handle = NetSDKManager.findHandle(entity.ip, entity.port) ?: continue
            val thumbPath = captureThumb(thumbDir, entity.ip, handle)
            if (thumbPath.isNotBlank() && thumbPath != entity.thumbnailPath &&
                db.deviceDao().getById(entity.id) != null
            ) {
                db.deviceDao().update(entity.copy(
                    thumbnailPath = thumbPath,
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    }
}

private suspend fun refreshOneDevice(db: AppDatabase, entity: DeviceEntity) {
    if (db.deviceDao().getById(entity.id) == null) return

    var handle = NetSDKManager.ensureLogin(
        entity.ip, entity.port, entity.userName, entity.password.ifBlank { "admin" }
    ).data

    if (db.deviceDao().getById(entity.id) == null) {
        NetSDKManager.logoutByIp(entity.ip, entity.port)
        return
    }

    if (handle == null || handle == 0L) {
        db.deviceDao().updateStatus(entity.id, DeviceEntity.STATUS_OFFLINE)
        return
    }

    var info = NetSDKManager.getDeviceInfo(handle)
    if (!info.isSuccess) {
        if (db.deviceDao().getById(entity.id) == null) {
            NetSDKManager.logoutByIp(entity.ip, entity.port)
            return
        }
        NetSDKManager.logout(handle)
        handle = NetSDKManager.login(
            entity.ip, entity.port, entity.userName, entity.password.ifBlank { "admin" }
        ).data
        if (handle == null || handle == 0L) {
            db.deviceDao().updateStatus(entity.id, DeviceEntity.STATUS_OFFLINE)
            return
        }
        info = NetSDKManager.getDeviceInfo(handle)
    }

    var name = entity.name
    var model = entity.model
    var sn = entity.serialNo
    var fw = entity.firmwareVersion
    if (info.isSuccess && info.data != null) {
        val d = info.data!!
        name = d.optString("name").ifBlank { entity.name }
        model = d.optString("model").ifBlank { entity.model }
        sn = d.optString("serial_no").ifBlank { entity.serialNo }
        fw = d.optString("firmware_version").ifBlank { entity.firmwareVersion }
    }

    if (db.deviceDao().getById(entity.id) == null) {
        NetSDKManager.logoutByIp(entity.ip, entity.port)
        return
    }

    // Mark online before capture so a hung snapshot cannot leave the spinner forever.
    db.deviceDao().update(
        entity.copy(
            name = name,
            model = model,
            serialNo = sn,
            firmwareVersion = fw,
            status = DeviceEntity.STATUS_ONLINE,
            thumbnailPath = entity.thumbnailPath,
            updatedAt = System.currentTimeMillis()
        )
    )
    if (db.deviceDao().getById(entity.id) == null) {
        NetSDKManager.logoutByIp(entity.ip, entity.port)
        return
    }
    AlarmMonitor.ensureListening(entity)
}

private suspend fun captureThumb(
    thumbDir: File,
    ip: String,
    handle: Long = NetSDKManager.getActiveHandle()
): String {
    return try {
        withTimeout(6_000) {
            val file = File(thumbDir, "thumb_${ip.replace('.', '_')}.jpg")
            val r = NetSDKManager.getThermalCapture(0, file.absolutePath, handle)
            if (r.isSuccess) file.absolutePath else ""
        }
    } catch (_: Exception) {
        ""
    }
}

private sealed class Screen {
    data object Splash : Screen()
    data object DeviceList : Screen()
    data object AddDevice : Screen()
    data object ManualAdd : Screen()
    data object OnlineAdd : Screen()
    data class Preview(
        val deviceId: Long,
        val name: String,
        val ip: String,
        val thumbnailPath: String
    ) : Screen()
    data class DeviceDetail(val ip: String) : Screen()
    data class AlarmConfig(val deviceId: Long, val name: String, val ip: String) : Screen()
    data class Maintenance(val deviceId: Long, val name: String, val ip: String) : Screen()
    data class EditDevice(
        val deviceId: Long,
        val name: String,
        val ip: String,
        val fromPreview: Boolean = false,
        val thumbnailPath: String = ""
    ) : Screen()
}
