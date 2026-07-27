package com.irtek.live.ui.edit

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irtek.live.ui.theme.AppColors
import com.irtek.netsdk.NetSDKManager
import com.irtek.netsdk.NetSDKResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Display label for UI; value is SDK format e.g. "UTC+08:00" */
private data class TimeZoneOption(val value: String, val label: String)

/** Standard timezone list — UI like Windows/GMT Chinese picker; API uses UTC±HH:MM */
private val standardTimeZones = listOf(
    TimeZoneOption("UTC-12:00", "(GMT-12:00) 日界线西"),
    TimeZoneOption("UTC-11:00", "(GMT-11:00) 中途岛，萨摩亚群岛"),
    TimeZoneOption("UTC-10:00", "(GMT-10:00) 夏威夷"),
    TimeZoneOption("UTC-09:00", "(GMT-09:00) 阿拉斯加"),
    TimeZoneOption("UTC-08:00", "(GMT-08:00) 太平洋时间(美国和加拿大)"),
    TimeZoneOption("UTC-07:00", "(GMT-07:00) 山地时间(美国和加拿大)"),
    TimeZoneOption("UTC-06:00", "(GMT-06:00) 中部时间(美国和加拿大)"),
    TimeZoneOption("UTC-05:00", "(GMT-05:00) 东部时间(美国和加拿大)"),
    TimeZoneOption("UTC-04:00", "(GMT-04:00) 大西洋时间(加拿大)"),
    TimeZoneOption("UTC-03:30", "(GMT-03:30) 纽芬兰"),
    TimeZoneOption("UTC-03:00", "(GMT-03:00) 巴西利亚"),
    TimeZoneOption("UTC-02:00", "(GMT-02:00) 中大西洋"),
    TimeZoneOption("UTC-01:00", "(GMT-01:00) 亚速尔群岛"),
    TimeZoneOption("UTC+00:00", "(GMT+00:00) 格林威治标准时间：都柏林，爱丁堡，伦敦"),
    TimeZoneOption("UTC+01:00", "(GMT+01:00) 阿姆斯特丹，柏林，伯尔尼，罗马，斯德哥尔摩，维也纳"),
    TimeZoneOption("UTC+02:00", "(GMT+02:00) 雅典，布加勒斯特，伊斯坦布尔"),
    TimeZoneOption("UTC+03:00", "(GMT+03:00) 莫斯科，圣彼得堡，伏尔加格勒"),
    TimeZoneOption("UTC+03:30", "(GMT+03:30) 德黑兰"),
    TimeZoneOption("UTC+04:00", "(GMT+04:00) 阿布扎比，马斯喀特"),
    TimeZoneOption("UTC+04:30", "(GMT+04:30) 喀布尔"),
    TimeZoneOption("UTC+05:00", "(GMT+05:00) 叶卡捷琳堡"),
    TimeZoneOption("UTC+05:30", "(GMT+05:30) 钦奈，加尔各答，孟买，新德里"),
    TimeZoneOption("UTC+05:45", "(GMT+05:45) 加德满都"),
    TimeZoneOption("UTC+06:00", "(GMT+06:00) 阿拉木图，新西伯利亚"),
    TimeZoneOption("UTC+06:30", "(GMT+06:30) 仰光"),
    TimeZoneOption("UTC+07:00", "(GMT+07:00) 曼谷，河内，雅加达"),
    TimeZoneOption("UTC+08:00", "(GMT+08:00) 北京，重庆，香港特别行政区，乌鲁木齐"),
    TimeZoneOption("UTC+09:00", "(GMT+09:00) 大阪，札幌，东京"),
    TimeZoneOption("UTC+09:30", "(GMT+09:30) 阿德莱德"),
    TimeZoneOption("UTC+10:00", "(GMT+10:00) 堪培拉，墨尔本，悉尼"),
    TimeZoneOption("UTC+11:00", "(GMT+11:00) 马加丹，所罗门群岛，新喀里多尼亚"),
    TimeZoneOption("UTC+12:00", "(GMT+12:00) 奥克兰，惠灵顿"),
    TimeZoneOption("UTC+13:00", "(GMT+13:00) 努库阿洛法")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceEditScreen(
    deviceIp: String,
    deviceName: String,
    onBack: () -> Unit
) {
    var subPage by remember { mutableStateOf<String?>(null) }

    when (subPage) {
        "time" -> TimeConfigPage(onBack = { subPage = null })
        "unit" -> UnitConfigPage(onBack = { subPage = null })
        "network" -> NetworkConfigPage(onBack = { subPage = null })
        "display" -> DisplayConfigPage(onBack = { subPage = null })
        "osd" -> OsdConfigPage(onBack = { subPage = null })
        "thermal_basic" -> ThermalBasicConfigPage(onBack = { subPage = null })
        "thermal_osd" -> ThermalOsdConfigPage(onBack = { subPage = null })
        "roi" -> RoiConfigPage(onBack = { subPage = null })
        null -> {
            DeviceEditMainPage(onBack = onBack, onNavigate = { subPage = it })
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Main list page
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceEditMainPage(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    BackHandler { onBack() }

    Scaffold(
        containerColor = Color(0xFFF4F5F9),
        topBar = { ConfigTopBar("设备配置", onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            SectionLabel("基本配置")
            ConfigCard {
                NavRow("时间配置") { onNavigate("time") }
                SettingDivider()
                NavRow("单位配置") { onNavigate("unit") }
                SettingDivider()
                NavRow("网络配置") { onNavigate("network") }
            }

            Spacer(Modifier.height(12.dp))

            SectionLabel("图像配置")
            ConfigCard {
                NavRow("显示配置") { onNavigate("display") }
                SettingDivider()
                NavRow("OSD 配置") { onNavigate("osd") }
            }

            Spacer(Modifier.height(12.dp))

            SectionLabel("热像配置")
            ConfigCard {
                NavRow("基本配置") { onNavigate("thermal_basic") }
                SettingDivider()
                NavRow("OSD 配置") { onNavigate("thermal_osd") }
                SettingDivider()
                NavRow("ROI 配置") { onNavigate("roi") }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Time Config
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TimeConfigPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }

    var timeMode by remember { mutableIntStateOf(0) }
    var localTime by remember { mutableStateOf("") }
    var timeZone by remember { mutableStateOf("") }
    var ntpHost by remember { mutableStateOf("") }
    var ntpPort by remember { mutableStateOf("123") }
    var ntpInterval by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val tr = NetSDKManager.getTime()
            if (tr.isSuccess && tr.data != null) {
                timeMode = tr.data!!.optInt("time_mode", 0)
                localTime = tr.data!!.optString("local_time")
                val tz = tr.data!!.optString("time_zone")
                timeZone = when {
                    standardTimeZones.any { it.value == tz } -> tz
                    tz.startsWith("GMT") -> tz.replaceFirst("GMT", "UTC")
                        .takeIf { v -> standardTimeZones.any { it.value == v } } ?: "UTC+08:00"
                    else -> "UTC+08:00"
                }
            }
            val nr = NetSDKManager.getNtpServers()
            if (nr.isSuccess && nr.data != null && nr.data!!.length() > 0) {
                val ntp = nr.data!!.getJSONObject(0)
                ntpHost = ntp.optString("host_name")
                ntpPort = ntp.optInt("port_no", 123).toString()
                ntpInterval = ntp.optInt("syn_interval", 60).toString()
            }
        }
        isLoading = false
    }

    BackHandler { onBack() }
    SubPageScaffold("时间配置", onBack, isLoading) {
        SectionLabel("时区配置")
        ConfigCard {
            val selectedTz = standardTimeZones.find { it.value == timeZone }
                ?: standardTimeZones.find { it.value == "UTC+08:00" }!!
            TimeZoneDropdownRow(
                selected = selectedTz,
                options = standardTimeZones,
                onSelect = { timeZone = it.value }
            )
        }

        Spacer(Modifier.height(12.dp))

        val timeModes = listOf("手动", "NTP")
        SectionLabel("时间模式")
        ConfigCard {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                timeModes.forEachIndexed { idx, label ->
                    FilterChip(
                        selected = timeMode == idx,
                        onClick = { timeMode = idx },
                        label = { Text(label, fontSize = 13.sp) },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2673F9),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (timeMode == 0) {
            SectionLabel("本地时间")
            ConfigCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("日期时间", fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                    BasicTextField(
                        value = localTime,
                        onValueChange = { localTime = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = AppColors.TextPrimary, textAlign = TextAlign.End),
                        modifier = Modifier.width(160.dp),
                        decorationBox = { inner ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFF4F5F9))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) { inner() }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            localTime = sdf.format(java.util.Date())
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("同步", fontSize = 13.sp, color = Color(0xFF2673F9))
                    }
                }
            }
        } else {
            SectionLabel("NTP 服务器")
            ConfigCard {
                EditRow("服务器地址", ntpHost) { ntpHost = it }
                SettingDivider()
                EditRow("端口号", ntpPort, KeyboardType.Number) { ntpPort = it }
                SettingDivider()
                EditRow("同步间隔(秒)", ntpInterval, KeyboardType.Number) { ntpInterval = it }
            }
        }

        Spacer(Modifier.height(16.dp))

        SaveButton {
            scope.launch {
                val timeJson = JSONObject().apply {
                    put("time_mode", timeMode)
                    put("local_time", localTime)
                    put("time_zone", timeZone)
                }
                val tr = NetSDKManager.setTime(timeJson.toString())
                if (timeMode == 1 && ntpHost.isNotBlank()) {
                    val ntpJson = JSONObject().apply {
                        put("host_name", ntpHost)
                        put("port_no", ntpPort.toIntOrNull() ?: 123)
                        put("syn_interval", ntpInterval.toIntOrNull() ?: 60)
                    }
                    NetSDKManager.setNtpServer(0, ntpJson.toString())
                }
                Toast.makeText(context, if (tr.isSuccess) "保存成功" else "保存失败", Toast.LENGTH_SHORT).show()
                if (tr.isSuccess) onBack()
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Unit Config
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun UnitConfigPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }

    var tempUnit by remember { mutableIntStateOf(0) }
    var distUnit by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val r = NetSDKManager.getThermalUnit()
            if (r.isSuccess && r.data != null) {
                tempUnit = r.data!!.optInt("temp_unit", 0)
                distUnit = r.data!!.optInt("distance_unit", 0)
            }
        }
        isLoading = false
    }

    BackHandler { onBack() }
    SubPageScaffold("单位配置", onBack, isLoading) {
        val tempLabels = listOf("℃ 摄氏度", "K 开尔文", "℉ 华氏度")
        val distLabels = listOf("米 (m)", "英尺 (ft)")

        SectionLabel("温度单位")
        ConfigCard {
            tempLabels.forEachIndexed { idx, label ->
                RadioRow(label, tempUnit == idx) { tempUnit = idx }
                if (idx < tempLabels.lastIndex) SettingDivider()
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionLabel("距离单位")
        ConfigCard {
            distLabels.forEachIndexed { idx, label ->
                RadioRow(label, distUnit == idx) { distUnit = idx }
                if (idx < distLabels.lastIndex) SettingDivider()
            }
        }

        Spacer(Modifier.height(16.dp))

        SaveButton {
            scope.launch {
                val json = JSONObject().apply {
                    put("temp_unit", tempUnit)
                    put("distance_unit", distUnit)
                }
                val r = NetSDKManager.setThermalUnit(json.toString())
                Toast.makeText(context, if (r.isSuccess) "保存成功" else "保存失败", Toast.LENGTH_SHORT).show()
                if (r.isSuccess) onBack()
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Network Config
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NetworkConfigPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }

    var networkId by remember { mutableIntStateOf(0) }
    var dhcp by remember { mutableIntStateOf(0) }
    var ip by remember { mutableStateOf("") }
    var netmask by remember { mutableStateOf("") }
    var gateway by remember { mutableStateOf("") }
    var dns by remember { mutableStateOf("") }
    var httpPort by remember { mutableStateOf("") }
    var rtspPort by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val nr = NetSDKManager.getNetworks()
            if (nr.isSuccess && nr.data != null && nr.data!!.length() > 0) {
                val n = nr.data!!.getJSONObject(0)
                networkId = n.optInt("id", 0)
                dhcp = n.optInt("dhcp", 0)
                ip = n.optString("ip")
                netmask = n.optString("netmask")
                val gwArr = n.optJSONArray("gateway")
                gateway = if (gwArr != null && gwArr.length() > 0) gwArr.optString(0) else ""
                val dnsArr = n.optJSONArray("dns")
                dns = if (dnsArr != null && dnsArr.length() > 0) dnsArr.optString(0) else ""
            }
            val pr = NetSDKManager.getPort()
            if (pr.isSuccess && pr.data != null) {
                httpPort = pr.data!!.optInt("http_port_no", 80).toString()
                rtspPort = pr.data!!.optInt("rtsp_port_no", 554).toString()
            }
        }
        isLoading = false
    }

    BackHandler { onBack() }
    SubPageScaffold("网络配置", onBack, isLoading) {
        SectionLabel("网络")
        ConfigCard {
            val netEditable = dhcp != 1
            SwitchRow("DHCP", dhcp == 1) { dhcp = if (it) 1 else 0 }
            SettingDivider()
            EditRow("IP 地址", ip, enabled = netEditable) { ip = it }
            SettingDivider()
            EditRow("子网掩码", netmask, enabled = netEditable) { netmask = it }
            SettingDivider()
            EditRow("网关", gateway, enabled = netEditable) { gateway = it }
            SettingDivider()
            EditRow("DNS", dns, enabled = netEditable) { dns = it }
        }

        Spacer(Modifier.height(12.dp))

        SectionLabel("端口")
        ConfigCard {
            EditRow("HTTP 端口", httpPort, KeyboardType.Number) { httpPort = it }
            SettingDivider()
            EditRow("RTSP 端口", rtspPort, KeyboardType.Number) { rtspPort = it }
        }

        Spacer(Modifier.height(16.dp))

        SaveButton {
            scope.launch {
                val netJson = JSONObject().apply {
                    put("id", networkId)
                    put("dhcp", dhcp)
                    put("ip", ip)
                    put("netmask", netmask)
                    put("gateway", JSONArray().apply { if (gateway.isNotBlank()) put(gateway) })
                    put("dns", JSONArray().apply { if (dns.isNotBlank()) put(dns) })
                }
                val nr = NetSDKManager.setNetwork(networkId, netJson.toString())
                val portJson = JSONObject().apply {
                    put("http_port_no", httpPort.toIntOrNull() ?: 80)
                    put("rtsp_port_no", rtspPort.toIntOrNull() ?: 554)
                }
                val pr = NetSDKManager.setPort(portJson.toString())
                val ok = nr.isSuccess && pr.isSuccess
                Toast.makeText(context, if (ok) "保存成功" else "保存失败", Toast.LENGTH_SHORT).show()
                if (ok) onBack()
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Display Config (Image Params)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DisplayConfigPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }

    var contrast by remember { mutableStateOf("") }
    var brightness by remember { mutableStateOf("") }
    var ddeEnable by remember { mutableIntStateOf(0) }
    var ddeValue by remember { mutableStateOf("") }
    var flipMode by remember { mutableIntStateOf(0) }
    var channelId by remember { mutableIntStateOf(1) }

    // Gain / 成像方式 — SDK: FIXED=1, AUTO=2
    var gainMode by remember { mutableIntStateOf(2) }
    var gainMax by remember { mutableStateOf("") }
    var gainMin by remember { mutableStateOf("") }
    var tempUnitSuffix by remember { mutableStateOf("℃") }
    // Color dist TWB — SDK: T_TWB=1, TEMP_LINEAR=2, AD_LINEAR=3
    var twbEnabled by remember { mutableStateOf(false) }
    var colorDistType by remember { mutableIntStateOf(2) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // 先通过 SDK 拉取通道列表（对应 GET /video/channels），再使用真实 channel id
            var cid = 1
            val ch = NetSDKManager.getChannels()
            if (ch.isSuccess && ch.data != null && ch.data!!.length() > 0) {
                cid = ch.data!!.getJSONObject(0).optInt("id", 1)
            }
            channelId = cid

            val ir = NetSDKManager.getImageParams(cid)
            if (ir.isSuccess && ir.data != null) {
                contrast = ir.data!!.optInt("contrast", 50).toString()
                brightness = ir.data!!.optInt("brightness", 50).toString()
                ddeEnable = if (ir.data!!.optBoolean("dde_enable", false) || ir.data!!.optInt("dde_enable", 0) == 1) 1 else 0
                ddeValue = ir.data!!.optInt("dde_value", 0).toString()
            }
            val fr = NetSDKManager.getImageFlip(cid)
            if (fr.isSuccess && fr.data != null) {
                flipMode = fr.data!!
            }
            val gr = NetSDKManager.getThermalGain()
            if (gr.isSuccess && gr.data != null) {
                val g = gr.data!!
                gainMode = g.optInt("mode", 2)
                gainMax = String.format("%.1f", g.optDouble("max", 100.0))
                gainMin = String.format("%.1f", g.optDouble("min", 0.0))
            }
            val ur = NetSDKManager.getThermalUnit()
            if (ur.isSuccess && ur.data != null) {
                tempUnitSuffix = when (ur.data!!.optInt("temp_unit", 0)) {
                    1 -> "K"
                    2 -> "℉"
                    else -> "℃"
                }
            }
            val cr = NetSDKManager.getThermalColorDist()
            if (cr.isSuccess && cr.data != null) {
                colorDistType = cr.data!!
                twbEnabled = colorDistType == 1
            }
        }
        isLoading = false
    }

    BackHandler { onBack() }
    SubPageScaffold("显示配置", onBack, isLoading) {
        SectionLabel("图像参数")
        ConfigCard {
            SliderRow("对比度", contrast.toIntOrNull() ?: 50, 0, 100) { contrast = it.toString() }
            SettingDivider()
            SliderRow("亮度", brightness.toIntOrNull() ?: 50, 0, 100) { brightness = it.toString() }
            SettingDivider()
            SwitchRow("DDE 增强", ddeEnable == 1) { ddeEnable = if (it) 1 else 0 }
            if (ddeEnable == 1) {
                SettingDivider()
                SliderRow("DDE 强度", ddeValue.toIntOrNull() ?: 0, 0, 100) { ddeValue = it.toString() }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionLabel("成像方式")
        ConfigCard {
            val gainModes = listOf(2 to "自动温宽", 1 to "手动温宽")
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                gainModes.forEach { (mode, label) ->
                    FilterChip(
                        selected = gainMode == mode,
                        onClick = { gainMode = mode },
                        label = { Text(label, fontSize = 13.sp) },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2673F9),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            if (gainMode == 1) {
                SettingDivider()
                EditRow("温宽上限", gainMax, KeyboardType.Decimal, suffix = tempUnitSuffix) { gainMax = it }
                SettingDivider()
                EditRow("温宽下限", gainMin, KeyboardType.Decimal, suffix = tempUnitSuffix) { gainMin = it }
            }
            SettingDivider()
            SwitchRow("高低温均衡成像 (TWB)", twbEnabled) {
                twbEnabled = it
                colorDistType = if (it) 1 else 2
            }
        }

        Spacer(Modifier.height(12.dp))

        // SDK: NONE=0, UP_DOWN=1, LEFT_RIGHT=2, CENTER=3
        val flipLabels = listOf("正常", "上下翻转", "左右翻转", "中心翻转")
        SectionLabel("画面翻转")
        ConfigCard {
            flipLabels.forEachIndexed { idx, label ->
                RadioRow(label, flipMode == idx) { flipMode = idx }
                if (idx < flipLabels.lastIndex) SettingDivider()
            }
        }

        Spacer(Modifier.height(16.dp))

        SaveButton {
            scope.launch {
                val imgJson = JSONObject().apply {
                    put("contrast", contrast.toIntOrNull() ?: 50)
                    put("brightness", brightness.toIntOrNull() ?: 50)
                    put("dde_enable", ddeEnable)
                    put("dde_value", ddeValue.toIntOrNull() ?: 0)
                }
                val ir = NetSDKManager.setImageParams(channelId, imgJson.toString())
                val fr = NetSDKManager.setImageFlip(channelId, flipMode)
                val gainJson = JSONObject().apply {
                    put("mode", gainMode)
                    put("max", gainMax.toDoubleOrNull() ?: 100.0)
                    put("min", gainMin.toDoubleOrNull() ?: 0.0)
                    put("fixedSpan", 0)
                    put("span", 2.0)
                }
                val gr = NetSDKManager.setThermalGain(gainJson.toString())
                val cr = NetSDKManager.setThermalColorDist(if (twbEnabled) 1 else 2)
                val ok = ir.isSuccess && fr.isSuccess && gr.isSuccess && cr.isSuccess
                Toast.makeText(context, if (ok) "保存成功" else "保存失败", Toast.LENGTH_SHORT).show()
                if (ok) onBack()
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ══════════════════════════════════════════════════════════════════════════════
// Thermal Basic Config (two-level)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ThermalBasicConfigPage(onBack: () -> Unit) {
    var detail by remember { mutableStateOf<String?>(null) }
    when (detail) {
        "range" -> ThermalRangeDetailPage(onBack = { detail = null })
        "shutter" -> ThermalShutterDetailPage(onBack = { detail = null })
        "params" -> ThermalParamsDetailPage(onBack = { detail = null })
        else -> {
            BackHandler { onBack() }
            SubPageScaffold("热像基本配置", onBack, false) {
                ConfigCard {
                    NavRow("测温量程") { detail = "range" }
                    SettingDivider()
                    NavRow("校正模式") { detail = "shutter" }
                    SettingDivider()
                    NavRow("全局测温参数") { detail = "params" }
                }
            }
        }
    }
}

@Composable
private fun ThermalRangeDetailPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var selectedRangeId by remember { mutableIntStateOf(0) }
    var rangeOptions by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var tempUnit by remember { mutableStateOf("℃") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val ur = NetSDKManager.getThermalUnit()
            if (ur.isSuccess && ur.data != null) {
                tempUnit = when (ur.data!!.optInt("temp_unit", 0)) {
                    1 -> "K"
                    2 -> "℉"
                    else -> "℃"
                }
            }
            val lens = NetSDKManager.getThermalLensInfo()
            if (lens.isSuccess && lens.data != null) {
                val map = lens.data!!.optJSONArray("range_map")
                val list = mutableListOf<Pair<Int, String>>()
                if (map != null) {
                    for (i in 0 until map.length()) {
                        val item = map.getJSONObject(i)
                        val id = item.optInt("id", i)
                        val range = item.optJSONObject("range")
                        val min = range?.optDouble("min", 0.0) ?: 0.0
                        val max = range?.optDouble("max", 0.0) ?: 0.0
                        // round to Int avoids float "-0" from negative zero / tiny negatives
                        list.add(id to "${formatTempValue(min)} ~ ${formatTempValue(max)} $tempUnit")
                    }
                }
                rangeOptions = list
            }
            val rr = NetSDKManager.getThermalRange()
            if (rr.isSuccess && rr.data != null) {
                selectedRangeId = rr.data!!
            }
        }
        isLoading = false
    }

    BackHandler { onBack() }
    SubPageScaffold("测温量程", onBack, isLoading) {
        if (rangeOptions.isEmpty()) {
            ConfigCard {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("暂无可用量程", fontSize = 14.sp, color = AppColors.TextSecondary)
                }
            }
        } else {
            SectionLabel("选择量程")
            ConfigCard {
                rangeOptions.forEachIndexed { idx, (id, label) ->
                    RadioRow(label, selectedRangeId == id) { selectedRangeId = id }
                    if (idx < rangeOptions.lastIndex) SettingDivider()
                }
            }
            Spacer(Modifier.height(16.dp))
            SaveButton {
                scope.launch {
                    val r = NetSDKManager.setThermalRange(selectedRangeId)
                    Toast.makeText(context, if (r.isSuccess) "保存成功" else "保存失败", Toast.LENGTH_SHORT).show()
                    if (r.isSuccess) onBack()
                }
            }
        }
    }
}

@Composable
private fun ThermalShutterDetailPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    // SDK: AUTOMATIC=0, MANUAL=1
    var shutterMode by remember { mutableIntStateOf(0) }
    var shutterInterval by remember { mutableStateOf("10") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val mr = NetSDKManager.getThermalShutterMode()
            if (mr.isSuccess && mr.data != null) shutterMode = mr.data!!
            val tr = NetSDKManager.getThermalShutterTime()
            if (tr.isSuccess && tr.data != null) shutterInterval = tr.data!!.toString()
        }
        isLoading = false
    }

    BackHandler { onBack() }
    SubPageScaffold("校正模式", onBack, isLoading) {
        SectionLabel("校正模式")
        ConfigCard {
            val modes = listOf(0 to "自动", 1 to "手动")
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                modes.forEach { (mode, label) ->
                    FilterChip(
                        selected = shutterMode == mode,
                        onClick = { shutterMode = mode },
                        label = { Text(label, fontSize = 13.sp) },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2673F9),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            if (shutterMode == 0) {
                SettingDivider()
                EditRow("自动校正间隔", shutterInterval, KeyboardType.Number, suffix = "秒") {
                    shutterInterval = it
                }
            }
        }

        if (shutterMode == 1) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        val r = NetSDKManager.thermalCalibrate()
                        Toast.makeText(
                            context,
                            if (r.isSuccess) "已执行校正" else "校正失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2673F9))
            ) {
                Text("立即校正", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(16.dp))
        SaveButton {
            scope.launch {
                val mr = NetSDKManager.setThermalShutterMode(shutterMode)
                val tr = if (shutterMode == 0) {
                    NetSDKManager.setThermalShutterTime(shutterInterval.toIntOrNull() ?: 10)
                } else {
                    NetSDKResult(0, Unit)
                }
                val ok = mr.isSuccess && tr.isSuccess
                Toast.makeText(context, if (ok) "保存成功" else "保存失败", Toast.LENGTH_SHORT).show()
                if (ok) onBack()
            }
        }
    }
}

@Composable
private fun ThermalParamsDetailPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }

    var emissivity by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var reflTemp by remember { mutableStateOf("") }
    var atmTemp by remember { mutableStateOf("") }
    var relHumidity by remember { mutableStateOf("") }
    var tempUnit by remember { mutableStateOf("℃") }
    var distUnit by remember { mutableStateOf("m") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val ur = NetSDKManager.getThermalUnit()
            if (ur.isSuccess && ur.data != null) {
                tempUnit = when (ur.data!!.optInt("temp_unit", 0)) {
                    1 -> "K"
                    2 -> "℉"
                    else -> "℃"
                }
                distUnit = when (ur.data!!.optInt("distance_unit", 0)) {
                    1 -> "ft"
                    else -> "m"
                }
            }
            val r = NetSDKManager.getThermalParams()
            if (r.isSuccess && r.data != null) {
                val d = r.data!!
                emissivity = String.format("%.2f", d.optDouble("emissivity", 0.95))
                distance = String.format("%.1f", d.optDouble("distance", 2.0))
                reflTemp = String.format("%.1f", d.optDouble("refl_temp", 23.0))
                atmTemp = String.format("%.1f", d.optDouble("atm_temp", 23.0))
                // 设备返回 0~1，UI 显示为百分比
                val humidity = d.optDouble("rel_humidity", 0.5)
                relHumidity = String.format("%.0f", if (humidity <= 1.0) humidity * 100.0 else humidity)
            }
        }
        isLoading = false
    }

    BackHandler { onBack() }
    SubPageScaffold("全局测温参数", onBack, isLoading) {
        SectionLabel("测温参数")
        ConfigCard {
            EditRow("发射率", emissivity, KeyboardType.Decimal) { emissivity = it }
            SettingDivider()
            EditRow("距离", distance, KeyboardType.Decimal, suffix = distUnit) { distance = it }
            SettingDivider()
            EditRow("反射温度", reflTemp, KeyboardType.Decimal, suffix = tempUnit) { reflTemp = it }
            SettingDivider()
            EditRow("大气温度", atmTemp, KeyboardType.Decimal, suffix = tempUnit) { atmTemp = it }
            SettingDivider()
            EditRow("相对湿度", relHumidity, KeyboardType.Decimal, suffix = "%") { relHumidity = it }
        }

        Spacer(Modifier.height(16.dp))

        SaveButton {
            scope.launch {
                val humidityPct = relHumidity.toDoubleOrNull() ?: 50.0
                val json = JSONObject().apply {
                    put("emissivity", emissivity.toDoubleOrNull() ?: 0.95)
                    put("distance", distance.toDoubleOrNull() ?: 2.0)
                    put("refl_temp", reflTemp.toDoubleOrNull() ?: 23.0)
                    put("atm_temp", atmTemp.toDoubleOrNull() ?: 23.0)
                    put("rel_humidity", (humidityPct / 100.0).coerceIn(0.0, 1.0))
                }
                val r = NetSDKManager.setThermalParams(json.toString())
                Toast.makeText(context, if (r.isSuccess) "保存成功" else "保存失败", Toast.LENGTH_SHORT).show()
                if (r.isSuccess) onBack()
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Shared components
// ══════════════════════════════════════════════════════════════════════════════

/** Format temperature for display; Int avoids float "-0". */
private fun formatTempValue(v: Double): String = kotlin.math.round(v).toInt().toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubPageScaffold(
    title: String,
    onBack: () -> Unit,
    isLoading: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        containerColor = Color(0xFFF4F5F9),
        topBar = { ConfigTopBar(title, onBack) }
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
                content()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        color = AppColors.TextSecondary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
private fun ConfigCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column { content() }
    }
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TimeZoneDropdownRow(
    selected: TimeZoneOption,
    options: List<TimeZoneOption>,
    onSelect: (TimeZoneOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("时区", fontSize = 15.sp, color = AppColors.TextPrimary)
            Spacer(Modifier.width(12.dp))
            Text(
                selected.label,
                fontSize = 14.sp,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 360.dp)
                .background(Color.White)
        ) {
            options.forEach { option ->
                val isSelected = option.value == selected.value
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            fontSize = 14.sp,
                            color = AppColors.TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = AppColors.TextPrimary
                    ),
                    modifier = if (isSelected) {
                        Modifier.background(Color(0xFFF0F2F5), RoundedCornerShape(4.dp))
                    } else Modifier
                )
            }
        }
    }
}

@Composable
private fun DropdownRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFF4F5F9))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selected, fontSize = 14.sp, color = AppColors.TextPrimary)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                    tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option,
                                fontSize = 14.sp,
                                color = if (option == selected) Color(0xFF2673F9) else AppColors.TextPrimary,
                                fontWeight = if (option == selected) FontWeight.Medium else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditRow(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    suffix: String = "",
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = if (enabled) AppColors.TextPrimary else AppColors.TextSecondary,
                textAlign = TextAlign.End
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.width(if (suffix.isNotEmpty()) 100.dp else 180.dp),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (enabled) Color(0xFFF4F5F9) else Color(0xFFE8EAED))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) { inner() }
            }
        )
        if (suffix.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(suffix, fontSize = 13.sp, color = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF2673F9))
        )
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF2673F9))
        )
    }
}

@Composable
private fun SliderRow(label: String, value: Int, min: Int, max: Int, onValueChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
            Text(value.toString(), fontSize = 14.sp, color = Color(0xFF2673F9), fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF2673F9),
                activeTrackColor = Color(0xFF2673F9)
            )
        )
    }
}

@Composable
private fun SaveButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2673F9))
    ) {
        Text("保存", fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 0.5.dp, color = Color(0x0F1D2129)
    )
}
