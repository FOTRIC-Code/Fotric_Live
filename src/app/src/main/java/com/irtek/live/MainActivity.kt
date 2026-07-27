package com.irtek.live

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import com.irtek.live.data.AppDatabase
import com.irtek.live.data.entity.DeviceEntity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Live)
        super.onCreate(savedInstanceState)
        val db = (application as LiveApp).database
        enableEdgeToEdge()
        setContent {
            LiveTheme {
                val scope = rememberCoroutineScope()
                var screen by remember { mutableStateOf<Screen>(Screen.Splash) }
                var selectedNav by remember { mutableIntStateOf(0) }
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
                val messageItems = remember(allAlarms, deviceEntities) {
                    allAlarms.map { alarm ->
                        val device = deviceEntities.find { it.id == alarm.deviceId }
                        MessageItem(
                            alarm = alarm,
                            deviceName = device?.name ?: "未知设备",
                            thumbnailPath = device?.thumbnailPath ?: ""
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        if (db.alarmDao().count() == 0L) {
                            val devices = db.deviceDao().getAllOnce()
                            if (devices.isNotEmpty()) {
                                val now = System.currentTimeMillis()
                                val mockAlarms = listOf(
                                    com.irtek.live.data.entity.AlarmMessage(
                                        deviceId = devices[0].id, type = "温度报警",
                                        title = "温度超限", content = "检测到温度异常",
                                        temperature = 85.2, threshold = 80.0,
                                        timestamp = now - 3600_000
                                    ),
                                    com.irtek.live.data.entity.AlarmMessage(
                                        deviceId = devices[0].id, type = "入侵报警",
                                        title = "移动侦测", content = "检测到移动物体",
                                        timestamp = now - 7200_000
                                    ),
                                    com.irtek.live.data.entity.AlarmMessage(
                                        deviceId = devices.last().id, type = "温度报警",
                                        title = "温度超限", content = "检测到温度异常",
                                        temperature = 92.1, threshold = 80.0,
                                        timestamp = now - 1800_000
                                    ),
                                    com.irtek.live.data.entity.AlarmMessage(
                                        deviceId = devices.last().id, type = "温度报警",
                                        title = "温度超限", content = "高温预警",
                                        temperature = 78.5, threshold = 75.0,
                                        timestamp = now - 600_000
                                    )
                                )
                                mockAlarms.forEach { db.alarmDao().insert(it) }
                            }
                        }
                    }
                }

                when (val s = screen) {
                    is Screen.Splash -> {
                        com.irtek.live.ui.splash.SplashScreen(
                            onFinished = { screen = Screen.DeviceList }
                        )
                    }
                    is Screen.DeviceList -> {
                        DeviceListScreen(
                            devices = devices,
                            isRefreshing = isRefreshing,
                            selectedNav = selectedNav,
                            onRefresh = {
                                scope.launch {
                                    isRefreshing = true
                                    val thumbDir = File(filesDir, "thumbnails")
                                    refreshAllDevices(db, thumbDir)
                                    isRefreshing = false
                                }
                            },
                            onAddDevice = { screen = Screen.AddDevice },
                            onDeviceClick = { device ->
                                val entity = deviceEntities.find { it.id.toString() == device.id }
                                if (entity != null) {
                                    screen = Screen.Preview(
                                        entity.id, entity.name, entity.ip, entity.thumbnailPath
                                    )
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            NetSDKManager.login(
                                                entity.ip, entity.port,
                                                entity.userName,
                                                entity.password.ifBlank { "admin" }
                                            )
                                        }
                                    }
                                }
                            },
                            onDeleteDevice = { device ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        db.deviceDao().deleteById(device.id.toLong())
                                    }
                                }
                            },
                            onAlarmConfig = { device ->
                                val entity = deviceEntities.find { it.id.toString() == device.id }
                                if (entity != null) {
                                    screen = Screen.AlarmConfig(entity.id, entity.name, entity.ip)
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            NetSDKManager.login(
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
                                            NetSDKManager.login(
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
                                            NetSDKManager.login(
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
                                MessageScreen(messages = messageItems)
                            },
                            galleryContent = {
                                GalleryScreen(
                                    captureDir = captureDir,
                                    recordDir = recordDir
                                )
                            },
                            mineContent = { MineScreen() }
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
                            onBack = { screen = Screen.AddDevice },
                            onConnected = { ip, userDeviceName ->
                                scope.launch {
                                    val thumbDir = File(filesDir, "thumbnails")
                                    saveDeviceAfterLogin(db, ip, thumbDir, userDeviceName)
                                    NetSDKManager.logout()
                                }
                                screen = Screen.DeviceList
                            }
                        )
                    }
                    is Screen.OnlineAdd -> {
                        BackHandler { screen = Screen.AddDevice }
                        OnlineAddScreen(
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
                                    screen = Screen.DeviceList
                                }
                            }
                        )
                    }
                    is Screen.Preview -> {
                        val leavePreview = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    NetSDKManager.stopStream()
                                    NetSDKManager.logout()
                                }
                            }
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            screen = Screen.DeviceList
                        }
                        BackHandler { leavePreview() }
                        val previewDevice = PreviewDevice(
                            id = s.deviceId,
                            name = s.name,
                            ip = s.ip,
                            thumbnailPath = s.thumbnailPath
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
                            }
                        )
                    }
                    is Screen.DeviceDetail -> {
                        BackHandler {
                            scope.launch { NetSDKManager.logout() }
                            screen = Screen.DeviceList
                        }
                        DeviceScreen(
                            deviceIp = s.ip,
                            onDisconnect = {
                                scope.launch {
                                    NetSDKManager.logout()
                                }
                                screen = Screen.DeviceList
                            }
                        )
                    }
                    is Screen.AlarmConfig -> {
                        BackHandler {
                            scope.launch { NetSDKManager.logout() }
                            screen = Screen.DeviceList
                        }
                        com.irtek.live.ui.alarm.AlarmConfigScreen(
                            deviceIp = s.ip,
                            deviceName = s.name,
                            onBack = {
                                scope.launch { NetSDKManager.logout() }
                                screen = Screen.DeviceList
                            }
                        )
                    }
                    is Screen.Maintenance -> {
                        BackHandler {
                            scope.launch { NetSDKManager.logout() }
                            screen = Screen.DeviceList
                        }
                        com.irtek.live.ui.maintenance.DeviceMaintenanceScreen(
                            deviceIp = s.ip,
                            deviceName = s.name,
                            onBack = {
                                scope.launch { NetSDKManager.logout() }
                                screen = Screen.DeviceList
                            }
                        )
                    }
                    is Screen.EditDevice -> {
                        val leaveEdit = {
                            if (s.fromPreview) {
                                // 保持登录，返回预览并重新拉流
                                screen = Screen.Preview(s.deviceId, s.name, s.ip, s.thumbnailPath)
                            } else {
                                scope.launch { NetSDKManager.logout() }
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
        thumbPath = captureThumb(thumbDir, ip)
        val existing = db.deviceDao().getByIp(ip)
        if (existing != null) {
            db.deviceDao().update(existing.copy(
                name = name,
                model = model.ifBlank { existing.model },
                serialNo = sn.ifBlank { existing.serialNo },
                firmwareVersion = fw.ifBlank { existing.firmwareVersion },
                status = DeviceEntity.STATUS_ONLINE,
                thumbnailPath = thumbPath.ifBlank { existing.thumbnailPath },
                updatedAt = System.currentTimeMillis()
            ))
        } else {
            db.deviceDao().insert(
                DeviceEntity(
                    name = name, ip = ip, model = model,
                    serialNo = sn, firmwareVersion = fw,
                    status = DeviceEntity.STATUS_ONLINE,
                    thumbnailPath = thumbPath
                )
            )
        }
    }
}

private suspend fun saveDeviceByIp(
    db: AppDatabase, ip: String, discoveredName: String,
    fallbackModel: String, fallbackSn: String, fallbackFw: String,
    thumbDir: File
) {
    withContext(Dispatchers.IO) {
        if (!thumbDir.exists()) thumbDir.mkdirs()
        var name = discoveredName.ifBlank { ip }
        var model = fallbackModel
        var sn = fallbackSn
        var fw = fallbackFw
        var thumbPath = ""
        val loginResult = NetSDKManager.login(ip, 80, "admin", "admin")
        if (loginResult.isSuccess) {
            val info = NetSDKManager.getDeviceInfo()
            if (info.isSuccess && info.data != null) {
                val d = info.data!!
                name = d.optString("name").ifBlank { name }
                model = d.optString("model").ifBlank { fallbackModel }
                sn = d.optString("serial_no").ifBlank { fallbackSn }
                fw = d.optString("firmware_version").ifBlank { fallbackFw }
            }
            thumbPath = captureThumb(thumbDir, ip)
            NetSDKManager.logout()
        }
        val existing = db.deviceDao().getByIp(ip)
        if (existing != null) {
            db.deviceDao().update(existing.copy(
                name = name,
                model = model.ifBlank { existing.model },
                serialNo = sn.ifBlank { existing.serialNo },
                firmwareVersion = fw.ifBlank { existing.firmwareVersion },
                thumbnailPath = thumbPath.ifBlank { existing.thumbnailPath },
                updatedAt = System.currentTimeMillis()
            ))
        } else {
            db.deviceDao().insert(
                DeviceEntity(
                    name = name, ip = ip, model = model,
                    serialNo = sn, firmwareVersion = fw,
                    status = DeviceEntity.STATUS_OFFLINE,
                    thumbnailPath = thumbPath
                )
            )
        }
    }
}

private suspend fun refreshAllDevices(db: AppDatabase, thumbDir: File) {
    withContext(Dispatchers.IO) {
        if (!thumbDir.exists()) thumbDir.mkdirs()
        val allDevices = db.deviceDao().getAllOnce()
        for (entity in allDevices) {
            val loginResult = NetSDKManager.login(
                entity.ip, entity.port, entity.userName, entity.password.ifBlank { "admin" }
            )
            if (loginResult.isSuccess) {
                var name = entity.name
                var model = entity.model
                var sn = entity.serialNo
                var fw = entity.firmwareVersion
                val info = NetSDKManager.getDeviceInfo()
                if (info.isSuccess && info.data != null) {
                    val d = info.data!!
                    name = d.optString("name").ifBlank { entity.name }
                    model = d.optString("model").ifBlank { entity.model }
                    sn = d.optString("serial_no").ifBlank { entity.serialNo }
                    fw = d.optString("firmware_version").ifBlank { entity.firmwareVersion }
                }
                val thumbPath = captureThumb(thumbDir, entity.ip).ifBlank { entity.thumbnailPath }
                db.deviceDao().update(entity.copy(
                    name = name,
                    model = model,
                    serialNo = sn,
                    firmwareVersion = fw,
                    status = DeviceEntity.STATUS_ONLINE,
                    thumbnailPath = thumbPath,
                    updatedAt = System.currentTimeMillis()
                ))
                NetSDKManager.logout()
            } else {
                db.deviceDao().updateStatus(entity.id, DeviceEntity.STATUS_OFFLINE)
            }
        }
    }
}

private suspend fun captureThumb(thumbDir: File, ip: String): String {
    val file = File(thumbDir, "thumb_${ip.replace('.', '_')}.jpg")
    val r = NetSDKManager.getThermalCapture(0, file.absolutePath)
    return if (r.isSuccess) file.absolutePath else ""
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
