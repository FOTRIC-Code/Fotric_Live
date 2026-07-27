package com.irtek.live.ui.edit

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irtek.live.ui.theme.AppColors
import com.irtek.netsdk.NativeSDK
import com.irtek.netsdk.NetSDKManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer

/** Client-side thermal marker model (Demo DrawMarker + preserved params). */
private data class DrawMarker(
    var id: Int = 0,
    var type: Int = 1,
    var name: String = "",
    var localParams: Int = 0,
    var reflTemp: Double = 0.0,
    var distance: Double = 0.0,
    var emissivity: Double = 0.95,
    var points: MutableList<Pair<Int, Int>> = mutableListOf()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("name", name)
        put("local_params", localParams)
        put("refl_temp", reflTemp)
        put("distance", distance)
        put("emissivity", emissivity)
        val pts = JSONArray()
        points.forEach { (x, y) -> pts.put(JSONObject().put("x", x).put("y", y)) }
        put("points", pts)
        put("points_count", points.size)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoiConfigPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var videoAspectRatio by remember { mutableFloatStateOf(4f / 3f) }
    var activeStreamId by remember { mutableIntStateOf(-1) }

    var markers by remember { mutableStateOf<List<DrawMarker>>(emptyList()) }
    var markerResW by remember { mutableIntStateOf(320) }
    var markerResH by remember { mutableIntStateOf(240) }
    // 0 select, 1 spot, 2 line, 3 area
    var markerMode by remember { mutableIntStateOf(0) }
    var nextMarkerId by remember { mutableIntStateOf(1) }
    var spotCounter by remember { mutableIntStateOf(0) }
    var lineCounter by remember { mutableIntStateOf(0) }
    var areaCounter by remember { mutableIntStateOf(0) }
    var editingMarkerId by remember { mutableStateOf<Int?>(null) }
    var tempUnit by remember { mutableStateOf("℃") }
    var distUnit by remember { mutableStateOf("m") }

    var draggingMarker by remember { mutableStateOf<DrawMarker?>(null) }
    var dragHitType by remember { mutableIntStateOf(-1) }
    var dragMarkerOrigPoints by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }

    fun pushMarkers(list: List<DrawMarker> = markers) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val json = arr.toString()
        scope.launch {
            val r = withContext(Dispatchers.IO) { NetSDKManager.setThermalMarkers(json) }
            if (!r.isSuccess) {
                Toast.makeText(context, "保存失败: ${r.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addMarkerAt(mx: Int, my: Int) {
        when (markerMode) {
            1 -> {
                spotCounter++
                val m = DrawMarker(
                    id = nextMarkerId++,
                    type = 1,
                    name = "S%02d".format(spotCounter),
                    points = mutableListOf(mx to my)
                )
                val list = markers + m
                markers = list
                markerMode = 0
                pushMarkers(list)
            }
            2 -> {
                lineCounter++
                val m = DrawMarker(
                    id = nextMarkerId++,
                    type = 2,
                    name = "L%02d".format(lineCounter),
                    points = mutableListOf(
                        mx to my,
                        (mx + 40).coerceAtMost(markerResW - 1) to
                            (my + 40).coerceAtMost(markerResH - 1)
                    )
                )
                val list = markers + m
                markers = list
                markerMode = 0
                pushMarkers(list)
            }
            3 -> {
                areaCounter++
                val x0 = mx
                val y0 = my
                val x1 = (mx + 30).coerceAtMost(markerResW - 1)
                val y1 = (my + 30).coerceAtMost(markerResH - 1)
                val m = DrawMarker(
                    id = nextMarkerId++,
                    type = 5,
                    name = "A%02d".format(areaCounter),
                    points = mutableListOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1)
                )
                val list = markers + m
                markers = list
                markerMode = 0
                pushMarkers(list)
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
            scope.launch(Dispatchers.IO) {
                NetSDKManager.stopStream()
            }
        }
    }

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

            val res = NetSDKManager.getThermalResolution()
            if (res.isSuccess && res.data != null) {
                markerResW = res.data!!.optInt("width", 320)
                markerResH = res.data!!.optInt("height", 240)
            }

            val mr = NetSDKManager.getThermalMarkers()
            if (mr.isSuccess && mr.data != null) {
                val list = mutableListOf<DrawMarker>()
                var sCnt = 0; var aCnt = 0; var lCnt = 0
                for (i in 0 until mr.data!!.length()) {
                    val m = mr.data!!.getJSONObject(i)
                    val pts = mutableListOf<Pair<Int, Int>>()
                    val ptsArr = m.optJSONArray("points")
                    if (ptsArr != null) {
                        for (j in 0 until ptsArr.length()) {
                            val p = ptsArr.getJSONObject(j)
                            pts.add(p.optInt("x") to p.optInt("y"))
                        }
                    }
                    val dm = DrawMarker(
                        id = m.optInt("id"),
                        type = m.optInt("type"),
                        name = m.optString("name", ""),
                        localParams = m.optInt("local_params", 0),
                        reflTemp = m.optDouble("refl_temp", 0.0),
                        distance = m.optDouble("distance", 0.0),
                        emissivity = m.optDouble("emissivity", 0.95),
                        points = pts
                    )
                    list.add(dm)
                    when (dm.type) {
                        1 -> sCnt++
                        2, 6 -> lCnt++
                        3, 4, 5 -> aCnt++
                    }
                }
                markers = list
                spotCounter = sCnt
                lineCounter = lCnt
                areaCounter = aCnt
                nextMarkerId = (list.maxOfOrNull { it.id } ?: 0) + 1
            }

            // Start preview stream for overlay editing
            var retries = 0
            while (retries < 30) {
                val streams = NetSDKManager.getStreams()
                if (streams.isSuccess && streams.data != null && streams.data!!.length() > 0) {
                    val first = streams.data!!.getJSONObject(0)
                    val sid = first.optInt("id", 101)
                    activeStreamId = sid
                    val w = first.optInt("resolution_width", 0)
                    val h = first.optInt("resolution_height", 0)
                    if (w > 0 && h > 0) videoAspectRatio = w.toFloat() / h.toFloat()
                    NetSDKManager.startStream(sid, 2)
                    break
                }
                delay(200)
                retries++
            }
        }
        isLoading = false
    }

    fun handleBack() {
        scope.launch {
            withContext(Dispatchers.IO) { NetSDKManager.stopStream() }
            onBack()
        }
    }

    val editingMarker = editingMarkerId?.let { id -> markers.find { it.id == id } }
    if (editingMarker != null) {
        MarkerParamsEditPage(
            marker = editingMarker,
            onBack = { editingMarkerId = null },
            onSave = { updated ->
                val list = markers.map { if (it.id == updated.id) updated else it }
                markers = list
                pushMarkers(list)
                editingMarkerId = null
            }
        )
        return
    }

    BackHandler { handleBack() }

    Scaffold(
        containerColor = Color(0xFFF4F5F9),
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("ROI 配置", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            // Video + overlay drawing only
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(videoAspectRatio.coerceIn(0.5f, 2.5f))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
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
                        "等待视频画面…",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(markerMode, markerResW, markerResH) {
                            if (markerMode in 1..3) {
                                detectTapGestures { offset ->
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    if (w <= 0f || h <= 0f) return@detectTapGestures
                                    val mx = (offset.x * markerResW / w).toInt().coerceIn(0, markerResW - 1)
                                    val my = (offset.y * markerResH / h).toInt().coerceIn(0, markerResH - 1)
                                    addMarkerAt(mx, my)
                                }
                            } else {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val w = size.width.toFloat()
                                        val h = size.height.toFloat()
                                        if (w <= 0f || h <= 0f) return@detectDragGestures
                                        val mx = (offset.x * markerResW / w).toInt().coerceIn(0, markerResW - 1)
                                        val my = (offset.y * markerResH / h).toInt().coerceIn(0, markerResH - 1)
                                        val hit = hitTestMarker(mx, my, markers, w / markerResW, h / markerResH)
                                        if (hit != null) {
                                            draggingMarker = hit.first
                                            dragHitType = hit.second
                                            dragMarkerOrigPoints = hit.first.points.map { it.copy() }
                                        }
                                    },
                                    onDrag = { _, dragAmount ->
                                        val dm = draggingMarker ?: return@detectDragGestures
                                        val w = size.width.toFloat()
                                        val h = size.height.toFloat()
                                        val dx = (dragAmount.x * markerResW / w).toInt()
                                        val dy = (dragAmount.y * markerResH / h).toInt()
                                        if (dx == 0 && dy == 0) return@detectDragGestures

                                        var newDragging: DrawMarker? = null
                                        markers = markers.map { m ->
                                            if (m.id != dm.id) m
                                            else {
                                                val newPts = m.points.toMutableList()
                                                when (m.type) {
                                                    1 -> {
                                                        newPts[0] = (newPts[0].first + dx).coerceIn(0, markerResW - 1) to
                                                            (newPts[0].second + dy).coerceIn(0, markerResH - 1)
                                                    }
                                                    2, 6 -> {
                                                        when (dragHitType) {
                                                            0 -> newPts[0] =
                                                                (newPts[0].first + dx).coerceIn(0, markerResW - 1) to
                                                                    (newPts[0].second + dy).coerceIn(0, markerResH - 1)
                                                            1 -> newPts[1] =
                                                                (newPts[1].first + dx).coerceIn(0, markerResW - 1) to
                                                                    (newPts[1].second + dy).coerceIn(0, markerResH - 1)
                                                            else -> {
                                                                for (i in newPts.indices) {
                                                                    newPts[i] =
                                                                        (newPts[i].first + dx).coerceIn(0, markerResW - 1) to
                                                                            (newPts[i].second + dy).coerceIn(0, markerResH - 1)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    3, 4, 5 -> {
                                                        if (dragHitType == -2) {
                                                            for (i in newPts.indices) {
                                                                newPts[i] =
                                                                    (newPts[i].first + dx).coerceIn(0, markerResW - 1) to
                                                                        (newPts[i].second + dy).coerceIn(0, markerResH - 1)
                                                            }
                                                        } else {
                                                            val vi = dragHitType.coerceIn(0, newPts.lastIndex)
                                                            newPts[vi] =
                                                                (newPts[vi].first + dx).coerceIn(0, markerResW - 1) to
                                                                    (newPts[vi].second + dy).coerceIn(0, markerResH - 1)
                                                        }
                                                    }
                                                }
                                                val copied = m.copy(points = newPts)
                                                newDragging = copied
                                                copied
                                            }
                                        }
                                        if (newDragging != null) draggingMarker = newDragging
                                    },
                                    onDragEnd = {
                                        if (draggingMarker != null) pushMarkers()
                                        draggingMarker = null
                                        dragHitType = -1
                                    },
                                    onDragCancel = {
                                        val dm = draggingMarker
                                        if (dm != null) {
                                            markers = markers.map { m ->
                                                if (m.id == dm.id) m.copy(points = dragMarkerOrigPoints.toMutableList()) else m
                                            }
                                        }
                                        draggingMarker = null
                                        dragHitType = -1
                                    }
                                )
                            }
                        }
                ) {
                    if (markerResW <= 0 || markerResH <= 0) return@Canvas
                    val sx = size.width / markerResW
                    val sy = size.height / markerResH
                    val markerColor = Color(0xFFFFEB3B)
                    val handleSize = 6f * sx

                    markers.forEach { m ->
                        when (m.type) {
                            1 -> {
                                if (m.points.isNotEmpty()) {
                                    val (mx, my) = m.points[0]
                                    val px = mx * sx
                                    val py = my * sy
                                    val r = 3f * sx
                                    val dist = 14f * sx
                                    drawCircle(markerColor, r, Offset(px, py))
                                    drawLine(markerColor, Offset(px, py - dist), Offset(px, py - r), strokeWidth = 2f)
                                    drawLine(markerColor, Offset(px, py + r), Offset(px, py + dist), strokeWidth = 2f)
                                    drawLine(markerColor, Offset(px - dist, py), Offset(px - r, py), strokeWidth = 2f)
                                    drawLine(markerColor, Offset(px + r, py), Offset(px + dist, py), strokeWidth = 2f)
                                }
                            }
                            2, 6 -> {
                                if (m.points.size >= 2) {
                                    val p0 = Offset(m.points[0].first * sx, m.points[0].second * sy)
                                    val p1 = Offset(m.points[1].first * sx, m.points[1].second * sy)
                                    drawLine(markerColor, p0, p1, strokeWidth = 2f)
                                    drawHandle(p0, handleSize, markerColor)
                                    drawHandle(p1, handleSize, markerColor)
                                }
                            }
                            3, 4, 5 -> {
                                if (m.points.size >= 2) {
                                    val path = Path()
                                    val pts = m.points.map { Offset(it.first * sx, it.second * sy) }
                                    path.moveTo(pts[0].x, pts[0].y)
                                    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                                    path.close()
                                    drawPath(path, markerColor, style = Stroke(2f))
                                    pts.forEach { drawHandle(it, handleSize, markerColor) }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Tools outside video — match edit card style
            Text(
                "绘制工具",
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MarkerToolButton(0, Icons.Outlined.NearMe, "选择", markerMode, Color(0xFF2673F9)) { markerMode = 0 }
                    MarkerToolButton(1, Icons.Outlined.FiberManualRecord, "点", markerMode, Color(0xFF2673F9)) { markerMode = 1 }
                    MarkerToolButton(2, Icons.Outlined.Remove, "线", markerMode, Color(0xFF2673F9)) { markerMode = 2 }
                    MarkerToolButton(3, Icons.Outlined.CropSquare, "框", markerMode, Color(0xFF2673F9)) { markerMode = 3 }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "ROI 列表",
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
                if (markers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无测温标识", fontSize = 14.sp, color = AppColors.TextSecondary)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        markers.forEachIndexed { index, m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingMarkerId = m.id }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        m.name.ifBlank { "标识${m.id}" },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AppColors.TextPrimary
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        buildString {
                                            append("类型: ${markerTypeLabel(m.type)}")
                                            if (m.localParams != 0) {
                                                append("  |  ε=${String.format("%.2f", m.emissivity)}")
                                                append("  d=${String.format("%.1f", m.distance)}$distUnit")
                                                append("  反射=${String.format("%.1f", m.reflTemp)}$tempUnit")
                                            } else {
                                                append("  |  全局参数")
                                            }
                                        },
                                        fontSize = 12.sp,
                                        color = AppColors.TextSecondary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val list = markers.filter { it.id != m.id }
                                        markers = list
                                        pushMarkers(list)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "删除",
                                        tint = AppColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = AppColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (index < markers.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    thickness = 0.5.dp,
                                    color = Color(0x0F1D2129)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkerParamsEditPage(
    marker: DrawMarker,
    onBack: () -> Unit,
    onSave: (DrawMarker) -> Unit
) {
    var name by remember(marker.id) { mutableStateOf(marker.name) }
    var useLocal by remember(marker.id) { mutableStateOf(marker.localParams != 0) }
    var emissivity by remember(marker.id) {
        mutableStateOf(String.format("%.2f", marker.emissivity))
    }
    var distance by remember(marker.id) {
        mutableStateOf(String.format("%.1f", marker.distance))
    }
    var reflTemp by remember(marker.id) {
        mutableStateOf(String.format("%.1f", marker.reflTemp))
    }
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
        }
    }

    BackHandler { onBack() }

    Scaffold(
        containerColor = Color(0xFFF4F5F9),
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("ROI 参数", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                "基本信息",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
            RoiCard {
                RoiEditRow("名称", name, KeyboardType.Text) { name = it }
                RoiDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("类型", fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                    Text(markerTypeLabel(marker.type), fontSize = 14.sp, color = AppColors.TextSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "测温参数",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
            RoiCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用独立参数", fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                    Switch(
                        checked = useLocal,
                        onCheckedChange = { useLocal = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF2673F9))
                    )
                }
                if (useLocal) {
                    RoiDivider()
                    RoiEditRow("发射率", emissivity, KeyboardType.Decimal) { emissivity = it }
                    RoiDivider()
                    RoiEditRow("距离", distance, KeyboardType.Decimal, distUnit) { distance = it }
                    RoiDivider()
                    RoiEditRow("反射温度", reflTemp, KeyboardType.Decimal, tempUnit) { reflTemp = it }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    onSave(
                        marker.copy(
                            name = name.trim().ifBlank { marker.name },
                            localParams = if (useLocal) 1 else 0,
                            emissivity = emissivity.toDoubleOrNull() ?: marker.emissivity,
                            distance = distance.toDoubleOrNull() ?: marker.distance,
                            reflTemp = reflTemp.toDoubleOrNull() ?: marker.reflTemp,
                            points = marker.points.toMutableList()
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2673F9))
            ) {
                Text("保存", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RoiCard(content: @Composable ColumnScope.() -> Unit) {
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
private fun RoiDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 0.5.dp,
        color = Color(0x0F1D2129)
    )
}

@Composable
private fun RoiEditRow(
    label: String,
    value: String,
    keyboardType: KeyboardType,
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
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.End
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.width(if (suffix.isNotEmpty()) 100.dp else 160.dp),
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
        if (suffix.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(suffix, fontSize = 13.sp, color = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun MarkerToolButton(
    mode: Int,
    icon: ImageVector,
    label: String,
    current: Int,
    activeColor: Color,
    onClick: () -> Unit
) {
    val selected = current == mode
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (selected) activeColor.copy(alpha = 0.12f) else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (selected) activeColor else AppColors.TextPrimary,
                    modifier = Modifier.size(if (mode == 1) 14.dp else 20.dp)
                )
            }
        }
        Text(
            label,
            fontSize = 11.sp,
            color = if (selected) activeColor else AppColors.TextSecondary
        )
    }
}

private fun markerTypeLabel(type: Int): String = when (type) {
    1 -> "点"
    2, 6 -> "线"
    3 -> "矩形"
    4 -> "椭圆"
    5 -> "多边形"
    else -> "未知"
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHandle(
    center: Offset,
    handleSize: Float,
    color: Color
) {
    drawRect(
        Color.White,
        Offset(center.x - handleSize / 2, center.y - handleSize / 2),
        Size(handleSize, handleSize)
    )
    drawRect(
        color,
        Offset(center.x - handleSize / 2, center.y - handleSize / 2),
        Size(handleSize, handleSize),
        style = Stroke(1.5f)
    )
}

private fun hitTestMarker(
    mx: Int,
    my: Int,
    markers: List<DrawMarker>,
    scaleX: Float,
    scaleY: Float
): Pair<DrawMarker, Int>? {
    val hitRadius = (8 * scaleX).toInt().coerceAtLeast(6)
    for (i in markers.indices.reversed()) {
        val m = markers[i]
        when (m.type) {
            1 -> {
                if (m.points.isNotEmpty()) {
                    val dx = mx - m.points[0].first
                    val dy = my - m.points[0].second
                    if (kotlin.math.abs(dx) <= hitRadius && kotlin.math.abs(dy) <= hitRadius) {
                        return m to -2
                    }
                }
            }
            2, 6 -> {
                if (m.points.size >= 2) {
                    val (x0, y0) = m.points[0]
                    val (x1, y1) = m.points[1]
                    if (kotlin.math.abs(mx - x0) <= hitRadius && kotlin.math.abs(my - y0) <= hitRadius) {
                        return m to 0
                    }
                    if (kotlin.math.abs(mx - x1) <= hitRadius && kotlin.math.abs(my - y1) <= hitRadius) {
                        return m to 1
                    }
                    val dist = distanceToSegment(
                        mx.toFloat(), my.toFloat(),
                        x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat()
                    )
                    if (dist < hitRadius) return m to -2
                }
            }
            3, 4, 5 -> {
                if (m.points.size >= 3) {
                    for (vi in m.points.indices) {
                        val (vx, vy) = m.points[vi]
                        if (kotlin.math.abs(mx - vx) <= hitRadius / 2 &&
                            kotlin.math.abs(my - vy) <= hitRadius / 2
                        ) {
                            return m to vi
                        }
                    }
                    if (pointInPolygon(mx, my, m.points)) return m to -2
                }
            }
        }
    }
    return null
}

private fun distanceToSegment(px: Float, py: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float {
    val dx = x1 - x0
    val dy = y1 - y0
    val len2 = dx * dx + dy * dy
    if (len2 == 0f) {
        return kotlin.math.hypot((px - x0).toDouble(), (py - y0).toDouble()).toFloat()
    }
    val t = (((px - x0) * dx + (py - y0) * dy) / len2).coerceIn(0f, 1f)
    return kotlin.math.hypot((px - (x0 + t * dx)).toDouble(), (py - (y0 + t * dy)).toDouble()).toFloat()
}

private fun pointInPolygon(x: Int, y: Int, poly: List<Pair<Int, Int>>): Boolean {
    var inside = false
    for (i in poly.indices) {
        val j = (i + 1) % poly.size
        val (xi, yi) = poly[i]
        val (xj, yj) = poly[j]
        val intersect = ((yi > y) != (yj > y)) &&
            (x < (xj - xi) * (y - yi) / (yj - yi).toDouble() + xi)
        if (intersect) inside = !inside
    }
    return inside
}
