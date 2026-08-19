package com.irtek.live.ui.alarm

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.irtek.live.R
import com.irtek.live.ui.theme.AppColors
import com.irtek.netsdk.NetSDKManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmConfigScreen(
    deviceIp: String,
    deviceName: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val markerTypeLabels = mapOf(
        1 to stringResource(R.string.marker_type_point),
        3 to stringResource(R.string.marker_type_rect),
        4 to stringResource(R.string.marker_type_ellipse),
        5 to stringResource(R.string.marker_type_polygon),
        6 to stringResource(R.string.marker_type_polyline)
    )

    var isLoading by remember { mutableStateOf(true) }

    // Data from SDK
    var markers by remember { mutableStateOf<JSONArray?>(null) }
    var allAlarms by remember { mutableStateOf<JSONArray?>(null) }
    var alarmInterval by remember { mutableStateOf("10") }
    var savedInterval by remember { mutableStateOf("10") }
    var tempUnitSuffix by remember { mutableStateOf("℃") }

    // Internal navigation: null = list page, String = marker detail
    var selectedMarker by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val markersResult = NetSDKManager.getThermalMarkers()
            if (markersResult.isSuccess && markersResult.data != null) {
                markers = markersResult.data
            }
            val alarmsResult = NetSDKManager.getThermalAlarms()
            if (alarmsResult.isSuccess && alarmsResult.data != null) {
                allAlarms = alarmsResult.data
            }
            val intervalResult = NetSDKManager.getThermalAlarmInterval()
            Log.i(
                "AlarmInterval",
                "config get ok=${intervalResult.isSuccess} value=${intervalResult.data} " +
                    "code=${intervalResult.code} msg=${intervalResult.message}"
            )
            if (intervalResult.isSuccess && intervalResult.data != null) {
                val v = intervalResult.data!!.toString()
                alarmInterval = v
                savedInterval = v
            }
            val unitResult = NetSDKManager.getThermalUnit()
            if (unitResult.isSuccess && unitResult.data != null) {
                tempUnitSuffix = when (unitResult.data!!.optInt("temp_unit", 0)) {
                    1 -> "K"
                    2 -> "℉"
                    else -> "℃"
                }
            }
        }
        isLoading = false
    }

    val markerNames = remember(markers) {
        val list = mutableListOf("global")
        if (markers != null) {
            for (i in 0 until markers!!.length()) {
                list.add(markers!!.getJSONObject(i).optString("name", context.getString(R.string.alarm_marker_fmt, i)))
            }
        }
        list
    }

    suspend fun saveIntervalNow() {
        val v = alarmInterval.toIntOrNull() ?: return
        if (v.toString() == savedInterval) return
        val r = withContext(Dispatchers.IO) {
            NetSDKManager.setThermalAlarmInterval(v)
        }
        Log.i(
            "AlarmInterval",
            "config set interval=$v ok=${r.isSuccess} code=${r.code} msg=${r.message}"
        )
        if (r.isSuccess) savedInterval = v.toString()
    }

    LaunchedEffect(alarmInterval) {
        if (alarmInterval == savedInterval) return@LaunchedEffect
        delay(800)
        saveIntervalNow()
    }

    if (selectedMarker != null) {
        MarkerAlarmDetailScreen(
            markerName = selectedMarker!!,
            allAlarms = allAlarms,
            tempUnit = tempUnitSuffix,
            onBack = { selectedMarker = null },
            onSave = { updatedAlarms ->
                allAlarms = updatedAlarms
                scope.launch {
                    val r = NetSDKManager.setThermalAlarms(updatedAlarms.toString())
                    val msg = if (r.isSuccess) context.getString(R.string.common_save_success) else context.getString(R.string.common_save_failed)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (r.isSuccess) selectedMarker = null
                }
            }
        )
        return
    }

    // ── Level 1: List page ──

    BackHandler {
        scope.launch {
            saveIntervalNow()
            onBack()
        }
    }

    Scaffold(
        containerColor = Color(0xFFF4F5F9),
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.alarm_config_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            saveIntervalNow()
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back),
                            tint = AppColors.TextPrimary, modifier = Modifier.size(22.dp))
                    }
                },
                actions = { Spacer(Modifier.width(48.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF4F5F9))
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF2673F9), strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(8.dp))

                Text(stringResource(R.string.alarm_interval), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    InputRow(
                        stringResource(R.string.alarm_interval),
                        alarmInterval,
                        stringResource(R.string.common_seconds)
                    ) { alarmInterval = it }
                }

                Spacer(Modifier.height(16.dp))

                // Card 2: Marker list
                Text(stringResource(R.string.alarm_markers), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Global alarm entry
                        MarkerRow(
                            name = stringResource(R.string.common_global),
                            typeLabel = null,
                            alarmEnabled = isAlarmEnabled(allAlarms, "global"),
                            onClick = { selectedMarker = "global" }
                        )

                        if (markers != null && markers!!.length() > 0) {
                            for (i in 0 until markers!!.length()) {
                                SettingDivider()
                                val m = markers!!.getJSONObject(i)
                                val mName = m.optString("name", stringResource(R.string.alarm_marker_fmt, i))
                                val mType = markerTypeLabels[m.optInt("type", 0)] ?: stringResource(R.string.common_unknown)
                                MarkerRow(
                                    name = mName,
                                    typeLabel = mType,
                                    alarmEnabled = isAlarmEnabled(allAlarms, mName),
                                    onClick = { selectedMarker = mName }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun isAlarmEnabled(alarms: JSONArray?, markerName: String): Boolean {
    if (alarms == null) return false
    for (i in 0 until alarms.length()) {
        val obj = alarms.getJSONObject(i)
        if (obj.optString("marker_name") == markerName) {
            return obj.optInt("enabled", 0) == 1
        }
    }
    return false
}

@Composable
private fun MarkerRow(name: String, typeLabel: String?, alarmEnabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFF0F2F5)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Notifications, contentDescription = null,
                tint = if (alarmEnabled) Color(0xFF2673F9) else AppColors.TextSecondary,
                modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, color = AppColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (typeLabel != null) {
                Text(typeLabel, fontSize = 12.sp, color = AppColors.TextSecondary)
            }
        }
        Text(
            if (alarmEnabled) stringResource(R.string.common_enabled) else stringResource(R.string.common_disabled),
            fontSize = 13.sp,
            color = if (alarmEnabled) Color(0xFF2673F9) else AppColors.TextSecondary
        )
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}

// ── Level 2: Marker alarm detail ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkerAlarmDetailScreen(
    markerName: String,
    allAlarms: JSONArray?,
    tempUnit: String,
    onBack: () -> Unit,
    onSave: (JSONArray) -> Unit
) {
    BackHandler { onBack() }

    val alarmTypeLabels = listOf(
        stringResource(R.string.alarm_cond_high_gt), stringResource(R.string.alarm_cond_high_lt),
        stringResource(R.string.alarm_cond_low_gt), stringResource(R.string.alarm_cond_low_lt),
        stringResource(R.string.alarm_cond_avg_gt), stringResource(R.string.alarm_cond_avg_lt),
        stringResource(R.string.alarm_cond_diff_gt), stringResource(R.string.alarm_cond_diff_lt)
    )

    val displayName = if (markerName == "global") stringResource(R.string.common_global) else markerName

    var alarmEnabled by remember { mutableStateOf(false) }
    var alarmTypeIndex by remember { mutableIntStateOf(0) }
    var alarmTemp by remember { mutableStateOf("80.0") }
    var alarmDelay by remember { mutableStateOf("0") }
    var thresholdTemp by remember { mutableStateOf("75.0") }
    var thresholdDelay by remember { mutableStateOf("0") }
    var triggerTemp by remember { mutableStateOf("90.0") }
    var triggerDelay by remember { mutableStateOf("0") }
    var showTypeMenu by remember { mutableStateOf(false) }

    fun fmt1(v: Double) = "%.1f".format(v)

    LaunchedEffect(Unit) {
        if (allAlarms != null) {
            for (i in 0 until allAlarms.length()) {
                val obj = allAlarms.getJSONObject(i)
                if (obj.optString("marker_name") == markerName) {
                    alarmEnabled = obj.optInt("enabled", 0) == 1
                    alarmTypeIndex = (obj.optInt("alarm_type", 1) - 1).coerceIn(0, 7)
                    alarmTemp = fmt1(obj.optDouble("alarm_temp", 80.0))
                    alarmDelay = obj.optInt("alarm_delay_time", 0).toString()
                    thresholdTemp = fmt1(
                        obj.optDouble(
                            "warning_temp",
                            obj.optDouble("threshold_temp", 75.0)
                        )
                    )
                    thresholdDelay = obj.optInt(
                        "warning_delay_time",
                        obj.optInt("threshold_delay_time", 0)
                    ).toString()
                    triggerTemp = fmt1(
                        obj.optDouble(
                            "alert_temp",
                            obj.optDouble("trigger_temp", 90.0)
                        )
                    )
                    triggerDelay = obj.optInt(
                        "alert_delay_time",
                        obj.optInt("trigger_delay_time", 0)
                    ).toString()
                    break
                }
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFFF4F5F9),
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(displayName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
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
                    SwitchRow(stringResource(R.string.alarm_switch), alarmEnabled) { alarmEnabled = it }
                    SettingDivider()
                    Box {
                        SettingRow(stringResource(R.string.alarm_type), alarmTypeLabels[alarmTypeIndex]) { showTypeMenu = true }
                        DropdownMenu(
                            expanded = showTypeMenu,
                            onDismissRequest = { showTypeMenu = false }
                        ) {
                            alarmTypeLabels.forEachIndexed { i, label ->
                                DropdownMenuItem(
                                    text = { Text(label, fontSize = 14.sp) },
                                    onClick = {
                                        alarmTypeIndex = i
                                        showTypeMenu = false
                                    }
                                )
                            }
                        }
                    }
                    SettingDivider()
                    InputRow(stringResource(R.string.alarm_temp), alarmTemp, tempUnit) { alarmTemp = it }
                    SettingDivider()
                    InputRow(stringResource(R.string.alarm_delay), alarmDelay, stringResource(R.string.common_seconds)) { alarmDelay = it }
                    SettingDivider()
                    InputRow(stringResource(R.string.alarm_warning_temp), thresholdTemp, tempUnit) { thresholdTemp = it }
                    SettingDivider()
                    InputRow(stringResource(R.string.alarm_warning_delay), thresholdDelay, stringResource(R.string.common_seconds)) { thresholdDelay = it }
                    SettingDivider()
                    InputRow(stringResource(R.string.alarm_alert_temp), triggerTemp, tempUnit) { triggerTemp = it }
                    SettingDivider()
                    InputRow(stringResource(R.string.alarm_alert_delay), triggerDelay, stringResource(R.string.common_seconds)) { triggerDelay = it }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val result = JSONArray()
                    if (allAlarms != null) {
                        for (i in 0 until allAlarms.length()) {
                            val obj = allAlarms.getJSONObject(i)
                            if (obj.optString("marker_name") == markerName) continue
                            result.put(obj)
                        }
                    }
                    result.put(JSONObject().apply {
                        put("marker_name", markerName)
                        put("enabled", if (alarmEnabled) 1 else 0)
                        put("alarm_type", alarmTypeIndex + 1)
                        put("alarm_temp", alarmTemp.toDoubleOrNull() ?: 80.0)
                        put("alarm_delay_time", alarmDelay.toIntOrNull() ?: 0)
                        put("warning_temp", thresholdTemp.toDoubleOrNull() ?: 75.0)
                        put("warning_delay_time", thresholdDelay.toIntOrNull() ?: 0)
                        put("alert_temp", triggerTemp.toDoubleOrNull() ?: 90.0)
                        put("alert_delay_time", triggerDelay.toIntOrNull() ?: 0)
                    })
                    onSave(result)
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2673F9))
            ) {
                Text(stringResource(R.string.alarm_save_settings), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Shared composables ──

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFF2673F9),
                uncheckedTrackColor = Color(0xFFE0E0E0)
            )
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun InputRow(label: String, value: String, suffix: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.End
            ),
            modifier = Modifier.width(80.dp),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    innerTextField()
                }
            }
        )
        Spacer(Modifier.width(4.dp))
        Text(suffix, fontSize = 13.sp, color = AppColors.TextSecondary)
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 0.5.dp,
        color = Color(0x0F1D2129)
    )
}
