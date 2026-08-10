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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irtek.live.R
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
            title = { Text(stringResource(R.string.maint_restart)) },
            text = { Text(stringResource(R.string.maint_restart_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    scope.launch {
                        val r = NetSDKManager.deviceRestart()
                        Toast.makeText(context,
                            if (r.isSuccess) context.getString(R.string.maint_restarting) else context.getString(R.string.common_operation_failed),
                            Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.common_ok), color = Color(0xFFDC2626)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.maint_reset)) },
            text = { Text(stringResource(R.string.maint_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    scope.launch {
                        val r = NetSDKManager.deviceReset()
                        Toast.makeText(context,
                            if (r.isSuccess) context.getString(R.string.maint_resetting) else context.getString(R.string.common_operation_failed),
                            Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.common_ok), color = Color(0xFFDC2626)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    Scaffold(
        containerColor = Color(0xFFF4F5F9),
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.maint_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back),
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
                        title = stringResource(R.string.maint_restart),
                        subtitle = stringResource(R.string.maint_restart_desc)
                    ) { showRestartDialog = true }

                    SettingDivider()

                    MaintenanceRow(
                        title = stringResource(R.string.maint_upgrade),
                        subtitle = stringResource(R.string.maint_upgrade_desc)
                    ) {
                        Toast.makeText(context, context.getString(R.string.common_not_supported), Toast.LENGTH_SHORT).show()
                    }

                    SettingDivider()

                    MaintenanceRow(
                        title = stringResource(R.string.maint_reset),
                        subtitle = stringResource(R.string.maint_reset_desc)
                    ) { showResetDialog = true }

                    SettingDivider()

                    MaintenanceRow(
                        title = stringResource(R.string.maint_device_info),
                        subtitle = stringResource(R.string.maint_info_desc)
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
                        Text(stringResource(R.string.common_warm_tip), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.maint_tip_body),
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
                        Text(stringResource(R.string.maint_device_info), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back),
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
                        InfoRow(stringResource(R.string.common_name), info.optString("name"))
                        SettingDivider()
                        InfoRow(stringResource(R.string.common_model), info.optString("model"))
                        SettingDivider()
                        InfoRow(stringResource(R.string.common_serial), info.optString("serial_no"))
                        SettingDivider()
                        InfoRow(stringResource(R.string.common_brand), info.optString("brand"))
                        SettingDivider()
                        InfoRow(stringResource(R.string.common_company), info.optString("company"))
                        SettingDivider()
                        InfoRow(stringResource(R.string.common_firmware_version), info.optString("firmware_version"))
                        SettingDivider()
                        val sdkVer = remember {
                            val r = NetSDKManager.getSdkVersion()
                            if (r.isSuccess && r.data != null) {
                                val d = r.data!!
                                "${d.optInt("major")}.${d.optInt("minor")}.${d.optInt("patch")}.${d.optInt("build")}"
                            } else "-"
                        }
                        InfoRow(stringResource(R.string.maint_sdk_version), sdkVer)
                    } else {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.maint_no_info), fontSize = 14.sp, color = AppColors.TextSecondary)
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
