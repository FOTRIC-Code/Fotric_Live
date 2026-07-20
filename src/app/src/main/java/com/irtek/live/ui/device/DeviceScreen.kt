package com.irtek.live.ui.device

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.irtek.live.ui.components.SectionCard
import com.irtek.netsdk.NetSDKManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(deviceIp: String, onDisconnect: () -> Unit) {
    val scope = rememberCoroutineScope()
    var deviceInfoText by remember { mutableStateOf("加载中...") }
    var lensInfoText by remember { mutableStateOf("加载中...") }
    var streamsText by remember { mutableStateOf("加载中...") }

    fun refreshAll() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val r = NetSDKManager.getDeviceInfo()
                deviceInfoText = if (r.isSuccess && r.data != null) {
                    val d = r.data!!
                    buildString {
                        append("名称: ${d.optString("name")}")
                        append("\n型号: ${d.optString("model")}")
                        append("\n规格: ${d.optString("spec")}")
                        append("\n序列号: ${d.optString("serial_no")}")
                        append("\n设备ID: ${d.optString("id")}")
                        append("\n品牌: ${d.optString("brand")}")
                        append("\n公司: ${d.optString("company")}")
                        append("\n固件: ${d.optString("firmware_name")} ${d.optString("firmware_version")}")
                    }
                } else "获取失败: ${r.message}"

                val lr = NetSDKManager.getThermalLensInfo()
                lensInfoText = if (lr.isSuccess && lr.data != null) {
                    val d = lr.data!!
                    buildString {
                        append("名称: ${d.optString("name")}")
                        append("\n焦距: ${d.optDouble("focal", 0.0)}mm")
                        append("\n水平视场角: ${d.optDouble("hfov", 0.0)}°")
                        append("\n垂直视场角: ${d.optDouble("vfov", 0.0)}°")
                        append("\n波段: ${d.optString("band")}")
                    }
                } else "获取失败: ${lr.message}"

                val sr = NetSDKManager.getStreams()
                streamsText = if (sr.isSuccess && sr.data != null) {
                    val arr = sr.data!!
                    if (arr.length() == 0) "无视频流"
                    else (0 until arr.length()).joinToString("\n") { i ->
                        val s = arr.getJSONObject(i)
                        "流${s.optInt("id")}: ${s.optInt("resolution_width")}x${s.optInt("resolution_height")} ${s.optString("codec_type")} ${s.optInt("bit_rate") / 1024}kbps ${s.optInt("max_frame_rate")}fps"
                    }
                } else "获取失败: ${sr.message}"
            }
        }
    }

    LaunchedEffect(Unit) { refreshAll() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceIp, style = MaterialTheme.typography.titleSmall) },
                navigationIcon = {
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "断开")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshAll() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    TextButton(onClick = onDisconnect) {
                        Text("断开", color = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SectionCard("设备信息") {
                Text(deviceInfoText, style = MaterialTheme.typography.bodySmall)
            }
            SectionCard("镜头信息") {
                Text(lensInfoText, style = MaterialTheme.typography.bodySmall)
            }
            SectionCard("视频流") {
                Text(streamsText, style = MaterialTheme.typography.bodySmall)
            }
            SectionCard("连接状态") {
                Text("设备IP: $deviceIp", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
