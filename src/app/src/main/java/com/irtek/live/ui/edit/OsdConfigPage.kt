package com.irtek.live.ui.edit

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.irtek.live.R
import com.irtek.live.ui.theme.AppColors
import com.irtek.netsdk.NativeSDK
import com.irtek.netsdk.NetSDKManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class OsdKind { NAME, TIME, CUSTOM }

private data class OverlayBlockState(
    var enabled: Boolean = false,
    var align: Int = 0,
    var mode: Int = 0,
    var x: Int = 0,
    var y: Int = 0,
    var text: String = ""
) {
    fun toJson(includeText: Boolean): JSONObject = JSONObject().apply {
        put("enabled", if (enabled) 1 else 0)
        put("align", 0) // 固定左上
        put("mode", 0)  // 固定常亮
        put("x", x)
        put("y", y)
        if (includeText) put("text", text)
    }

    companion object {
        fun fromJson(j: JSONObject?, includeText: Boolean = false): OverlayBlockState {
            if (j == null) return OverlayBlockState()
            return OverlayBlockState(
                enabled = j.optInt("enabled", 0) != 0,
                align = 0,
                mode = 0,
                x = j.optInt("x", 0).coerceAtLeast(0),
                y = j.optInt("y", 0).coerceAtLeast(0),
                text = if (includeText) j.optString("text", "") else ""
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OsdConfigPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current

    var isLoading by remember { mutableStateOf(true) }
    var configReady by remember { mutableStateOf(false) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var videoAspectRatio by remember { mutableFloatStateOf(4f / 3f) }

    var channelId by remember { mutableIntStateOf(1) }
    var channelName by remember { mutableStateOf("") }
    var coordW by remember { mutableIntStateOf(640) }
    var coordH by remember { mutableIntStateOf(480) }

    var nameOv by remember { mutableStateOf(OverlayBlockState()) }
    var timeOv by remember { mutableStateOf(OverlayBlockState()) }
    var customOv by remember { mutableStateOf(OverlayBlockState(text = "")) }
    var selected by remember { mutableStateOf(OsdKind.NAME) }

    val timePreview = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    fun pushOverlaySilent(
        name: OverlayBlockState = nameOv,
        time: OverlayBlockState = timeOv,
        custom: OverlayBlockState = customOv
    ) {
        if (!configReady) return
        val json = JSONObject().apply {
            put("channel_name_overlay", name.toJson(false))
            put("time_overlay", time.toJson(false))
            put("custom_overlay", custom.toJson(true))
        }.toString()
        scope.launch {
            val r = withContext(Dispatchers.IO) { NetSDKManager.setOverlay(channelId, json) }
            if (!r.isSuccess) {
                Toast.makeText(context, context.getString(R.string.common_set_failed_fmt, r.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun pushChannelName(name: String) {
        if (!configReady) return
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                NetSDKManager.setChannel(channelId, JSONObject().put("name", name).toString())
            }
            if (!r.isSuccess) {
                Toast.makeText(context, context.getString(R.string.edit_channel_name_fail, r.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(Unit) {
        NetSDKManager.setStreamFrameListener(object : NativeSDK.StreamFrameListener {
            override fun onStreamFrame(
                handle: Long, streamId: Int, streamType: Int,
                width: Int, height: Int, data: ByteArray,
                timestampUs: Long, keyFrame: Boolean
            ) {
                if (width <= 0 || height <= 0 || data.isEmpty()) return
                try {
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.copyPixelsFromBuffer(ByteBuffer.wrap(data))
                    currentBitmap = bmp
                    videoAspectRatio = width.toFloat() / height.toFloat()
                } catch (_: Exception) {
                }
            }
        })
        onDispose {
            NetSDKManager.setStreamFrameListener(null)
            scope.launch(Dispatchers.IO) { NetSDKManager.stopStream() }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val ch = NetSDKManager.getChannels()
            if (ch.isSuccess && ch.data != null && ch.data!!.length() > 0) {
                channelId = ch.data!!.getJSONObject(0).optInt("id", 1)
            }

            val cn = NetSDKManager.getChannel(channelId)
            if (cn.isSuccess && cn.data != null) {
                channelName = cn.data!!.optString("name", "")
            }

            // OSD x/y 归一化基准：热像分辨率（thermal size）
            val res = NetSDKManager.getThermalResolution()
            if (res.isSuccess && res.data != null) {
                coordW = res.data!!.optInt("width", 640).coerceAtLeast(1)
                coordH = res.data!!.optInt("height", 480).coerceAtLeast(1)
            }

            val streams = NetSDKManager.getStreams()
            if (streams.isSuccess && streams.data != null && streams.data!!.length() > 0) {
                val s = streams.data!!.getJSONObject(0)
                val w = s.optInt("resolution_width", 0)
                val h = s.optInt("resolution_height", 0)
                if (w > 0 && h > 0) {
                    videoAspectRatio = w.toFloat() / h.toFloat()
                }
            }

            val ov = NetSDKManager.getOverlay(channelId)
            if (ov.isSuccess && ov.data != null) {
                val d = ov.data!!
                nameOv = clampBlock(OverlayBlockState.fromJson(d.optJSONObject("channel_name_overlay")), coordW, coordH)
                timeOv = clampBlock(OverlayBlockState.fromJson(d.optJSONObject("time_overlay")), coordW, coordH)
                customOv = clampBlock(
                    OverlayBlockState.fromJson(d.optJSONObject("custom_overlay"), includeText = true),
                    coordW, coordH
                )
            }

            var retries = 0
            while (retries < 30) {
                val streams2 = NetSDKManager.getStreams()
                if (streams2.isSuccess && streams2.data != null && streams2.data!!.length() > 0) {
                    val sid = streams2.data!!.getJSONObject(0).optInt("id", 101)
                    NetSDKManager.startStream(sid, 2)
                    break
                }
                delay(200)
                retries++
            }
        }
        isLoading = false
        configReady = true
    }

    fun handleBack() {
        scope.launch {
            withContext(Dispatchers.IO) { NetSDKManager.stopStream() }
            onBack()
        }
    }

    BackHandler { handleBack() }

    Scaffold(
        containerColor = Color(0xFFF4F5F9),
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.edit_osd), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(videoAspectRatio.coerceIn(0.5f, 2.5f))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                val viewW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val viewH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val sx = viewW / coordW.coerceAtLeast(1)
                val sy = viewH / coordH.coerceAtLeast(1)
                // 左上角可拖范围：留一小段热像像素，保证控件仍有可点击区域落在画面内
                val keepPx = 24
                val maxX = (coordW - keepPx).coerceAtLeast(0)
                val maxY = (coordH - keepPx).coerceAtLeast(0)

                val bmp = currentBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                } else if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(36.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else {
                    Text(
                        stringResource(R.string.edit_waiting_video),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                listOf(
                    Triple(OsdKind.NAME, nameOv, channelName.ifBlank { stringResource(R.string.edit_channel_name) }),
                    Triple(OsdKind.TIME, timeOv, timePreview),
                    Triple(OsdKind.CUSTOM, customOv, customOv.text.ifBlank { stringResource(R.string.edit_custom_info) })
                ).forEach { (kind, block, label) ->
                    if (!block.enabled) return@forEach
                    val isSel = selected == kind
                    val px = block.x.coerceIn(0, maxX)
                    val py = block.y.coerceIn(0, maxY)
                    val offsetX = (px * sx).roundToInt()
                    val offsetY = (py * sy).roundToInt()

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX, offsetY) }
                            .pointerInput(kind, maxX, maxY) {
                                detectDragGestures(
                                    onDragStart = { selected = kind },
                                    onDrag = { _, dragAmount ->
                                        val dx = (dragAmount.x / sx).roundToInt()
                                        val dy = (dragAmount.y / sy).roundToInt()
                                        if (dx == 0 && dy == 0) return@detectDragGestures
                                        val cur = when (kind) {
                                            OsdKind.NAME -> nameOv
                                            OsdKind.TIME -> timeOv
                                            OsdKind.CUSTOM -> customOv
                                        }
                                        val updated = cur.copy(
                                            align = 0,
                                            mode = 0,
                                            x = (cur.x + dx).coerceIn(0, maxX),
                                            y = (cur.y + dy).coerceIn(0, maxY)
                                        )
                                        when (kind) {
                                            OsdKind.NAME -> nameOv = updated
                                            OsdKind.TIME -> timeOv = updated
                                            OsdKind.CUSTOM -> customOv = updated
                                        }
                                    },
                                    onDragEnd = { pushOverlaySilent() }
                                )
                            }
                            .background(
                                if (isSel) Color(0xFF2673F9).copy(alpha = 0.85f)
                                else Color.Black.copy(alpha = 0.55f),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = if (isSel) 1.5.dp else 0.dp,
                                color = Color.White,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable { selected = kind }
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = with(density) { (viewW * 0.55f).toDp() })
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (!configReady) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF2673F9), strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            } else {
                val selectedBlock = when (selected) {
                    OsdKind.NAME -> nameOv
                    OsdKind.TIME -> timeOv
                    OsdKind.CUSTOM -> customOv
                }
                Text(
                    stringResource(R.string.edit_osd_pos, selectedBlock.x, selectedBlock.y, coordW, coordH),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OsdElementRow(
                        title = stringResource(R.string.edit_channel_name_short),
                        enabled = nameOv.enabled,
                        selected = selected == OsdKind.NAME,
                        editValue = channelName,
                        onSelect = { selected = OsdKind.NAME },
                        onEnabledChange = { enabled ->
                            val updated = nameOv.copy(enabled = enabled, align = 0, mode = 0)
                            nameOv = updated
                            selected = OsdKind.NAME
                            pushOverlaySilent(name = updated)
                        },
                        onEditChange = { channelName = it },
                        onEditCommit = {
                            val name = channelName.trim()
                            if (name.isNotEmpty()) pushChannelName(name)
                        }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = Color(0x0F1D2129))
                    OsdElementRow(
                        title = stringResource(R.string.edit_time_label),
                        enabled = timeOv.enabled,
                        selected = selected == OsdKind.TIME,
                        editValue = null,
                        subtitle = timePreview,
                        onSelect = { selected = OsdKind.TIME },
                        onEnabledChange = { enabled ->
                            val updated = timeOv.copy(enabled = enabled, align = 0, mode = 0)
                            timeOv = updated
                            selected = OsdKind.TIME
                            pushOverlaySilent(time = updated)
                        }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = Color(0x0F1D2129))
                    OsdElementRow(
                        title = stringResource(R.string.edit_custom_text),
                        enabled = customOv.enabled,
                        selected = selected == OsdKind.CUSTOM,
                        editValue = customOv.text,
                        onSelect = { selected = OsdKind.CUSTOM },
                        onEnabledChange = { enabled ->
                            val updated = customOv.copy(enabled = enabled, align = 0, mode = 0)
                            customOv = updated
                            selected = OsdKind.CUSTOM
                            pushOverlaySilent(custom = updated)
                        },
                        onEditChange = { customOv = customOv.copy(text = it, align = 0, mode = 0) },
                        onEditCommit = { pushOverlaySilent() }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OsdElementRow(
    title: String,
    enabled: Boolean,
    selected: Boolean,
    editValue: String? = null,
    subtitle: String? = null,
    onSelect: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onEditChange: ((String) -> Unit)? = null,
    onEditCommit: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    var wasFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(if (selected) Color(0x142673F9) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                color = AppColors.TextPrimary,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            )
            if (editValue != null && onEditChange != null) {
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = editValue,
                    onValueChange = {
                        onSelect()
                        onEditChange(it)
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = AppColors.TextPrimary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onEditCommit?.invoke()
                            focusManager.clearFocus()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                wasFocused = true
                                onSelect()
                            } else if (wasFocused) {
                                // 仅在真正失焦时提交，避免首次组合触发覆盖设备配置
                                wasFocused = false
                                onEditCommit?.invoke()
                            }
                        }
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF4F5F9))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            } else if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = enabled,
            onCheckedChange = {
                onSelect()
                onEnabledChange(it)
            },
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF2673F9))
        )
    }
}

/** 左上角夹紧到热像范围，并预留一小段可点击宽高。 */
private fun clampBlock(
    block: OverlayBlockState,
    canvasW: Int,
    canvasH: Int,
    keepPx: Int = 24
): OverlayBlockState {
    val maxX = (canvasW - keepPx).coerceAtLeast(0)
    val maxY = (canvasH - keepPx).coerceAtLeast(0)
    return block.copy(
        align = 0,
        mode = 0,
        x = block.x.coerceIn(0, maxX),
        y = block.y.coerceIn(0, maxY)
    )
}
