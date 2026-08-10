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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irtek.live.R
import com.irtek.live.ui.components.SectionCard
import com.irtek.netsdk.NetSDKManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(deviceIp: String, onDisconnect: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val loadingText = stringResource(R.string.common_loading)
    var deviceInfoText by remember { mutableStateOf(loadingText) }
    var lensInfoText by remember { mutableStateOf(loadingText) }
    var streamsText by remember { mutableStateOf(loadingText) }

    fun refreshAll() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val r = NetSDKManager.getDeviceInfo()
                deviceInfoText = if (r.isSuccess && r.data != null) {
                    val d = r.data!!
                    buildString {
                        append("${context.getString(R.string.common_name)}: ${d.optString("name")}")
                        append("\n${context.getString(R.string.common_model)}: ${d.optString("model")}")
                        append("\n${context.getString(R.string.detail_spec)}: ${d.optString("spec")}")
                        append("\n${context.getString(R.string.common_serial)}: ${d.optString("serial_no")}")
                        append("\n${context.getString(R.string.detail_device_id)}: ${d.optString("id")}")
                        append("\n${context.getString(R.string.common_brand)}: ${d.optString("brand")}")
                        append("\n${context.getString(R.string.common_company)}: ${d.optString("company")}")
                        append("\n${context.getString(R.string.common_firmware)}: ${d.optString("firmware_name")} ${d.optString("firmware_version")}")
                    }
                } else context.getString(R.string.detail_fetch_failed, r.message)

                val lr = NetSDKManager.getThermalLensInfo()
                lensInfoText = if (lr.isSuccess && lr.data != null) {
                    val d = lr.data!!
                    buildString {
                        append("${context.getString(R.string.common_name)}: ${d.optString("name")}")
                        append("\n${context.getString(R.string.detail_focal)}: ${d.optDouble("focal", 0.0)}mm")
                        append("\n${context.getString(R.string.detail_hfov)}: ${d.optDouble("hfov", 0.0)}°")
                        append("\n${context.getString(R.string.detail_vfov)}: ${d.optDouble("vfov", 0.0)}°")
                        append("\n${context.getString(R.string.detail_band)}: ${d.optString("band")}")
                    }
                } else context.getString(R.string.detail_fetch_failed, lr.message)

                val sr = NetSDKManager.getStreams()
                streamsText = if (sr.isSuccess && sr.data != null) {
                    val arr = sr.data!!
                    if (arr.length() == 0) context.getString(R.string.detail_no_stream)
                    else (0 until arr.length()).joinToString("\n") { i ->
                        val s = arr.getJSONObject(i)
                        "${context.getString(R.string.common_video)} ${s.optInt("id")}: ${s.optInt("resolution_width")}x${s.optInt("resolution_height")} ${s.optString("codec_type")} ${s.optInt("bit_rate") / 1024}kbps ${s.optInt("max_frame_rate")}fps"
                    }
                } else context.getString(R.string.detail_fetch_failed, sr.message)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_disconnect))
                    }
                },
                actions = {
                    IconButton(onClick = { refreshAll() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.common_refresh))
                    }
                    TextButton(onClick = onDisconnect) {
                        Text(stringResource(R.string.common_disconnect), color = MaterialTheme.colorScheme.error)
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
            SectionCard(stringResource(R.string.detail_device_info)) {
                Text(deviceInfoText, style = MaterialTheme.typography.bodySmall)
            }
            SectionCard(stringResource(R.string.detail_lens_info)) {
                Text(lensInfoText, style = MaterialTheme.typography.bodySmall)
            }
            SectionCard(stringResource(R.string.detail_streams)) {
                Text(streamsText, style = MaterialTheme.typography.bodySmall)
            }
            SectionCard(stringResource(R.string.detail_connection)) {
                Text(stringResource(R.string.detail_device_ip, deviceIp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
