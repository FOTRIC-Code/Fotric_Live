package com.irtek.live.ui.adddevice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irtek.live.R
import com.irtek.live.ui.theme.AppColors
import com.irtek.netsdk.NetSDKManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineAddScreen(
    onBack: () -> Unit,
    onDeviceAdd: (JSONObject) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSearching by remember { mutableStateOf(true) }
    var devices by remember { mutableStateOf<JSONArray?>(null) }

    fun doSearch() {
        isSearching = true
        scope.launch {
            val result = NetSDKManager.searchDevices(3000)
            devices = if (result.isSuccess) result.data else null
            isSearching = false
        }
    }

    LaunchedEffect(Unit) { doSearch() }

    val deviceCount = devices?.length() ?: 0

    Scaffold(
        containerColor = Color(0xFFF4F5F9),
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.add_online), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = AppColors.TextPrimary, modifier = Modifier.size(22.dp))
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isSearching) stringResource(R.string.add_searching) else String.format(stringResource(R.string.add_search_done), deviceCount),
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { doSearch() }
                ) {
                    Text(stringResource(R.string.common_refresh), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2673F9))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.common_refresh), tint = Color(0xFF2673F9), modifier = Modifier.size(16.dp))
                }
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(top = 14.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    Text(stringResource(R.string.add_search_results), fontSize = 14.sp, fontWeight = FontWeight.Normal, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))

                    if (isSearching) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF2673F9), strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                        }
                    } else if (deviceCount == 0) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.add_no_device), fontSize = 14.sp, color = AppColors.TextSecondary)
                        }
                    } else {
                        val arr = devices!!
                        for (i in 0 until arr.length()) {
                            val device = arr.getJSONObject(i)
                            DeviceResultRow(device) { onDeviceAdd(device) }
                            if (i < arr.length() - 1) {
                                HorizontalDivider(thickness = 0.5.dp, color = Color(0x0F1D2129))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            TipCard()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DeviceResultRow(device: JSONObject, onAdd: () -> Unit) {
    val ip = device.optString("ip")
    val name = device.optString("model").ifBlank { device.optString("name").ifBlank { ip } }
    val serialNo = device.optString("serial_no")
    val fwVersion = device.optString("firmware_version")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF3D4F7C)),
            contentAlignment = Alignment.Center
        ) {
            Text("IR", fontSize = 20.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(String.format(stringResource(R.string.add_ip_prefix), ip), fontSize = 13.sp, color = AppColors.TextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(String.format(stringResource(R.string.add_serial_prefix), serialNo), fontSize = 13.sp, color = AppColors.TextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(String.format(stringResource(R.string.add_firmware_prefix), fwVersion), fontSize = 13.sp, color = AppColors.TextSecondary)
        }

        Box(
            modifier = Modifier
                .height(26.dp)
                .clip(CircleShape)
                .background(Color(0x142673F9))
                .clickable(onClick = onAdd)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.common_add), fontSize = 13.sp, color = Color(0xFF2673F9), fontWeight = FontWeight.Medium)
        }
    }
}
