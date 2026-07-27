package com.irtek.live.ui.maintenance

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irtek.live.ui.theme.AppColors
import com.irtek.netsdk.NetSDKManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceMaintenanceScreen(
    deviceIp: String,
    deviceName: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showRestartDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var subPage by remember { mutableStateOf<String?>(null) }

    var deviceInfo by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val di = NetSDKManager.getDeviceInfo()
            if (di.isSuccess) deviceInfo = di.data
        }
    }

    if (subPage == "device_info") {
        DeviceInfoSubPage(deviceInfo, onBack = { subPage = null })
        return
    }

    BackHandler { onBack() }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("重启设备") },
            text = { Text("确定要重启设备吗？重启后设备将暂时无法访问。") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    scope.launch {
                        val r = NetSDKManager.deviceRestart()
                        Toast.makeText(context,
                            if (r.isSuccess) "设备正在重启" else "操作失败",
                            Toast.LENGTH_SHORT).show()
                    }
                }) { Text("确定", color = Color(0xFFDC2626)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) { Text("取消") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("恢复出厂设置") },
            text = { Text("恢复出厂设置将清除所有配置，确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    scope.launch {
                        val r = NetSDKManager.deviceReset()
                        Toast.makeText(context,
                            if (r.isSuccess) "设备正在恢复出厂设置" else "操作失败",
                            Toast.LENGTH_SHORT).show()
                    }
                }) { Text("确定", color = Color(0xFFDC2626)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        containerColor = Color(0xFFF4F5F9),
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("设备维护", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                            tint = AppColors.TextPrimary, modifier = Modifier.size(22.dp))
                    }
                },
                actions = { Spacer(Modifier.width(48.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF4F5F9))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    MaintenanceRow(
                        title = "重启设备",
                        subtitle = "重新启动设备便设置生效或恢复运行"
                    ) { showRestartDialog = true }

                    SettingDivider()

                    MaintenanceRow(
                        title = "固件升级",
                        subtitle = "检测并升级固件以获取新功能"
                    ) {
                        Toast.makeText(context, "暂不支持", Toast.LENGTH_SHORT).show()
                    }

                    SettingDivider()

                    MaintenanceRow(
                        title = "恢复出厂设置",
                        subtitle = "将设备恢复到出厂默认设置"
                    ) { showResetDialog = true }

                    SettingDivider()

                    MaintenanceRow(
                        title = "设备信息",
                        subtitle = "查看设备详细信息与运行状态"
                    ) { subPage = "device_info" }
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Color(0xFF2673F9),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("温馨提醒", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "部分维护操作可能会导致设备重启或中断服务。请在合适时间进行操作。",
                            fontSize = 13.sp, color = AppColors.TextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MaintenanceRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 13.sp, color = AppColors.TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 0.5.dp, color = Color(0x0F1D2129)
    )
}

// ── Device Info Sub-page ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceInfoSubPage(info: JSONObject?, onBack: () -> Unit) {
    BackHandler { onBack() }

    Scaffold(
        containerColor = Color(0xFFF4F5F9),
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("设备信息", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                            tint = AppColors.TextPrimary, modifier = Modifier.size(22.dp))
                    }
                },
                actions = { Spacer(Modifier.width(48.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF4F5F9))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    if (info != null) {
                        InfoRow("名称", info.optString("name"))
                        SettingDivider()
                        InfoRow("型号", info.optString("model"))
                        SettingDivider()
                        InfoRow("序列号", info.optString("serial_no"))
                        SettingDivider()
                        InfoRow("品牌", info.optString("brand"))
                        SettingDivider()
                        InfoRow("厂商", info.optString("company"))
                        SettingDivider()
                        InfoRow("固件版本", info.optString("firmware_version"))
                        SettingDivider()
                        val sdkVer = remember {
                            val r = NetSDKManager.getSdkVersion()
                            if (r.isSuccess && r.data != null) {
                                val d = r.data!!
                                "${d.optInt("major")}.${d.optInt("minor")}.${d.optInt("patch")}.${d.optInt("build")}"
                            } else "-"
                        }
                        InfoRow("SDK 版本", sdkVer)
                    } else {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("暂无设备信息", fontSize = 14.sp, color = AppColors.TextSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Text(value.ifBlank { "-" }, fontSize = 14.sp, color = AppColors.TextSecondary)
    }
}
