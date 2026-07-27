package com.irtek.live.ui.edit

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** 普通叠加块：单位 / 调色板 / 测温参数 / 报警 */
private data class ThermalOvBlock(
    var enabled: Boolean = false,
    var align: Int = 0,
    var mode: Int = 0,
    var x: Int = 0,
    var y: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", if (enabled) 1 else 0)
        put("align", align)
        put("mode", mode)
        put("x", x)
        put("y", y)
    }

    companion object {
        fun fromJson(j: JSONObject?): ThermalOvBlock {
            if (j == null) return ThermalOvBlock()
            return ThermalOvBlock(
                enabled = j.optInt("enabled", 0) != 0,
                align = j.optInt("align", 0),
                mode = j.optInt("mode", 0),
                x = j.optInt("x", 0),
                y = j.optInt("y", 0)
            )
        }
    }
}

/** 跟随叠加块：全局温度 / 测温区域 */
private data class ThermalFollowBlock(
    var enabled: Boolean = false,
    var align: Int = 0,
    var follow: Boolean = false,
    var maxEnabled: Boolean = false,
    var minEnabled: Boolean = false,
    var avgEnabled: Boolean = false,
    var emissivityEnabled: Boolean = false,
    var x: Int = 0,
    var y: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", if (enabled) 1 else 0)
        put("align", align)
        put("follow", if (follow) 1 else 0)
        put("max_enabled", if (maxEnabled) 1 else 0)
        put("min_enabled", if (minEnabled) 1 else 0)
        put("avg_enabled", if (avgEnabled) 1 else 0)
        put("emissivity_enabled", if (emissivityEnabled) 1 else 0)
        put("x", x)
        put("y", y)
    }

    companion object {
        fun fromJson(j: JSONObject?): ThermalFollowBlock {
            if (j == null) return ThermalFollowBlock()
            return ThermalFollowBlock(
                enabled = j.optInt("enabled", 0) != 0,
                align = j.optInt("align", 0),
                follow = j.optInt("follow", 0) != 0,
                maxEnabled = j.optInt("max_enabled", 0) != 0,
                minEnabled = j.optInt("min_enabled", 0) != 0,
                avgEnabled = j.optInt("avg_enabled", 0) != 0,
                emissivityEnabled = j.optInt("emissivity_enabled", 0) != 0,
                x = j.optInt("x", 0),
                y = j.optInt("y", 0)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermalOsdConfigPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var configReady by remember { mutableStateOf(false) }

    var unitOv by remember { mutableStateOf(ThermalOvBlock()) }
    var pltOv by remember { mutableStateOf(ThermalOvBlock()) }
    var paramsOv by remember { mutableStateOf(ThermalOvBlock()) }
    var alarmOv by remember { mutableStateOf(ThermalOvBlock()) }
    var globalOv by remember { mutableStateOf(ThermalFollowBlock()) }
    var markerOv by remember { mutableStateOf(ThermalFollowBlock()) }

    fun pushThermalOverlay(
        unit: ThermalOvBlock = unitOv,
        plt: ThermalOvBlock = pltOv,
        params: ThermalOvBlock = paramsOv,
        alarm: ThermalOvBlock = alarmOv,
        global: ThermalFollowBlock = globalOv,
        marker: ThermalFollowBlock = markerOv
    ) {
        if (!configReady) return
        val json = JSONObject().apply {
            put("unit_overlay", unit.toJson())
            put("plt_overlay", plt.toJson())
            put("thermal_params_overlay", params.toJson())
            put("alarm_overlay", alarm.toJson())
            put("global_overlay", global.toJson())
            put("marker_overlay", marker.toJson())
        }.toString()
        scope.launch {
            val r = withContext(Dispatchers.IO) { NetSDKManager.setThermalOverlay(json) }
            if (!r.isSuccess) {
                Toast.makeText(context, "设置失败: ${r.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val r = NetSDKManager.getThermalOverlay()
            if (r.isSuccess && r.data != null) {
                val d = r.data!!
                unitOv = ThermalOvBlock.fromJson(d.optJSONObject("unit_overlay"))
                pltOv = ThermalOvBlock.fromJson(d.optJSONObject("plt_overlay"))
                paramsOv = ThermalOvBlock.fromJson(
                    d.optJSONObject("thermal_params_overlay") ?: d.optJSONObject("params_overlay")
                )
                alarmOv = ThermalOvBlock.fromJson(d.optJSONObject("alarm_overlay"))
                globalOv = ThermalFollowBlock.fromJson(d.optJSONObject("global_overlay"))
                markerOv = ThermalFollowBlock.fromJson(d.optJSONObject("marker_overlay"))
            }
        }
        isLoading = false
        configReady = true
    }

    BackHandler { onBack() }

    Scaffold(
        containerColor = Color(0xFFF4F5F9),
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("热像 OSD", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = AppColors.TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
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

                SectionTitle("显示开关")
                ThermalCard {
                    SwitchRow("单位", unitOv.enabled) {
                        val updated = unitOv.copy(enabled = it)
                        unitOv = updated
                        pushThermalOverlay(unit = updated)
                    }
                    ThermalDivider()
                    SwitchRow("调色板", pltOv.enabled) {
                        val updated = pltOv.copy(enabled = it)
                        pltOv = updated
                        pushThermalOverlay(plt = updated)
                    }
                    ThermalDivider()
                    SwitchRow("测温参数", paramsOv.enabled) {
                        val updated = paramsOv.copy(enabled = it)
                        paramsOv = updated
                        pushThermalOverlay(params = updated)
                    }
                    ThermalDivider()
                    SwitchRow("报警信息", alarmOv.enabled) {
                        val updated = alarmOv.copy(enabled = it)
                        alarmOv = updated
                        pushThermalOverlay(alarm = updated)
                    }
                }

                Spacer(Modifier.height(12.dp))

                SectionTitle("全局温度")
                ThermalCard {
                    SwitchRow("显示全局温度", globalOv.enabled) {
                        val updated = globalOv.copy(enabled = it)
                        globalOv = updated
                        pushThermalOverlay(global = updated)
                    }
                    if (globalOv.enabled) {
                        ThermalDivider()
                        SwitchRow("跟随标识", globalOv.follow) {
                            val updated = globalOv.copy(follow = it)
                            globalOv = updated
                            pushThermalOverlay(global = updated)
                        }
                        ThermalDivider()
                        SwitchRow("最高温", globalOv.maxEnabled) {
                            val updated = globalOv.copy(maxEnabled = it)
                            globalOv = updated
                            pushThermalOverlay(global = updated)
                        }
                        ThermalDivider()
                        SwitchRow("最低温", globalOv.minEnabled) {
                            val updated = globalOv.copy(minEnabled = it)
                            globalOv = updated
                            pushThermalOverlay(global = updated)
                        }
                        ThermalDivider()
                        SwitchRow("平均温", globalOv.avgEnabled) {
                            val updated = globalOv.copy(avgEnabled = it)
                            globalOv = updated
                            pushThermalOverlay(global = updated)
                        }
                        ThermalDivider()
                        SwitchRow("发射率", globalOv.emissivityEnabled) {
                            val updated = globalOv.copy(emissivityEnabled = it)
                            globalOv = updated
                            pushThermalOverlay(global = updated)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                SectionTitle("测温区域")
                ThermalCard {
                    SwitchRow("显示测温区域温度", markerOv.enabled) {
                        val updated = markerOv.copy(enabled = it)
                        markerOv = updated
                        pushThermalOverlay(marker = updated)
                    }
                    if (markerOv.enabled) {
                        ThermalDivider()
                        SwitchRow("跟随标识", markerOv.follow) {
                            val updated = markerOv.copy(follow = it)
                            markerOv = updated
                            pushThermalOverlay(marker = updated)
                        }
                        ThermalDivider()
                        SwitchRow("最高温", markerOv.maxEnabled) {
                            val updated = markerOv.copy(maxEnabled = it)
                            markerOv = updated
                            pushThermalOverlay(marker = updated)
                        }
                        ThermalDivider()
                        SwitchRow("最低温", markerOv.minEnabled) {
                            val updated = markerOv.copy(minEnabled = it)
                            markerOv = updated
                            pushThermalOverlay(marker = updated)
                        }
                        ThermalDivider()
                        SwitchRow("平均温", markerOv.avgEnabled) {
                            val updated = markerOv.copy(avgEnabled = it)
                            markerOv = updated
                            pushThermalOverlay(marker = updated)
                        }
                        ThermalDivider()
                        SwitchRow("发射率", markerOv.emissivityEnabled) {
                            val updated = markerOv.copy(emissivityEnabled = it)
                            markerOv = updated
                            pushThermalOverlay(marker = updated)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = AppColors.TextSecondary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
private fun ThermalCard(content: @Composable ColumnScope.() -> Unit) {
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
private fun ThermalDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 0.5.dp,
        color = Color(0x0F1D2129)
    )
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
