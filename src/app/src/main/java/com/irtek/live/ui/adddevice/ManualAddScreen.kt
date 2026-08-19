package com.irtek.live.ui.adddevice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irtek.live.R
import com.irtek.live.ui.theme.AppColors
import com.irtek.netsdk.NetSDKManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAddScreen(
    existingIps: Set<String> = emptySet(),
    onBack: () -> Unit,
    onConnected: (ip: String, deviceName: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var deviceName by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("80") }
    var userName by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val ipRequiredMsg = stringResource(R.string.add_error_ip_required)
    val alreadyAddedMsg = stringResource(R.string.add_error_already_added)
    val connectFailedFmt = stringResource(R.string.add_error_connect_failed)

    Scaffold(
        containerColor = Color(0xFFF4F5F9),
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.add_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
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
            Spacer(Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    FormRow(stringResource(R.string.common_device_name), deviceName, stringResource(R.string.add_hint_name)) { deviceName = it }
                    FormDivider()
                    FormRow(stringResource(R.string.common_ip_address), ipAddress, stringResource(R.string.add_hint_ip)) { ipAddress = it }
                    FormDivider()
                    FormRow(stringResource(R.string.common_port), port, stringResource(R.string.add_hint_port)) { port = it }
                    FormDivider()
                    FormRow(stringResource(R.string.common_username), userName, stringResource(R.string.add_hint_user)) { userName = it }
                    FormDivider()
                    FormRow(stringResource(R.string.common_password), password, stringResource(R.string.add_hint_password)) { password = it }
                }
            }

            Spacer(Modifier.height(12.dp))
            TipCard()
            Spacer(Modifier.height(12.dp))

            if (errorMsg != null) {
                Text(
                    errorMsg!!,
                    fontSize = 13.sp,
                    color = Color(0xFFDC2626),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            Button(
                onClick = {
                    if (ipAddress.isBlank()) { errorMsg = ipRequiredMsg; return@Button }
                    val ip = ipAddress.trim()
                    if (existingIps.any { it.equals(ip, ignoreCase = true) }) {
                        errorMsg = alreadyAddedMsg
                        return@Button
                    }
                    isConnecting = true
                    errorMsg = null
                    scope.launch {
                        val result = NetSDKManager.login(
                            ip,
                            port.toIntOrNull() ?: 80,
                            userName,
                            password.ifBlank { "admin" }
                        )
                        if (result.isSuccess) {
                            if (deviceName.isNotBlank()) {
                                NetSDKManager.setDeviceInfo(
                                    """{"name":"${deviceName.replace("\"", "\\\"")}"}"""
                                )
                            }
                            onConnected(ip, deviceName)
                        } else {
                            errorMsg = String.format(connectFailedFmt, result.message)
                        }
                        isConnecting = false
                    }
                },
                enabled = !isConnecting,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2673F9))
            ) {
                Text(
                    if (isConnecting) stringResource(R.string.add_connecting) else stringResource(R.string.add_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FormRow(label: String, value: String, placeholder: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 15.sp,
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.width(96.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.End
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) {
                        Text(placeholder, fontSize = 14.sp, color = AppColors.TextSecondary, textAlign = TextAlign.End)
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun FormDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 24.dp),
        thickness = 0.5.dp,
        color = Color(0x0F1D2129)
    )
}

@Composable
internal fun TipCard() {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFF2673F9),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(stringResource(R.string.common_warm_tip), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.add_lan_tip),
                    fontSize = 13.sp, color = AppColors.TextSecondary, lineHeight = 20.sp
                )
            }
        }
    }
}
