package com.irtek.live.ui.preview

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ShutterSpeed
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.irtek.live.R
import com.irtek.live.data.entity.AlarmMessage
import com.irtek.live.ui.message.MessageDetailScreen
import com.irtek.live.ui.message.MessageItem
import com.irtek.live.ui.theme.AppColors
import com.irtek.netsdk.NativeSDK
import com.irtek.netsdk.NetSDKManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class PreviewDevice(
    val id: Long,
    val name: String,
    val ip: String,
    val thumbnailPath: String,
    val port: Int = 80,
    val userName: String = "admin",
    val password: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    device: PreviewDevice,
    alarms: List<AlarmMessage>,
    captureDir: File,
    recordDir: File,
    onBack: () -> Unit,
    onCalibrate: () -> Unit = {},
    onEditDevice: () -> Unit = {},
    onMarkAlarmRead: (Long) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var videoAspectRatio by remember { mutableFloatStateOf(4f / 3f) }
    val activity = context as? Activity

    var isRecording by remember { mutableStateOf(false) }
    var activeStreamId by remember { mutableIntStateOf(101) }

    var showPaletteMenu by remember { mutableStateOf(false) }
    var paletteList by remember { mutableStateOf<JSONArray?>(null) }
    var currentPaletteRefNo by remember { mutableIntStateOf(-1) }
    var detailAlarm by remember { mutableStateOf<AlarmMessage?>(null) }

    val sdf = remember { java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val streamLock = remember { Mutex() }
    val previewActive = remember { AtomicBoolean(true) }
    val lastFrameAt = remember { AtomicLong(0L) }
    val latestDevice = rememberUpdatedState(device)
    val latestRecording = rememberUpdatedState(isRecording)

    suspend fun ensureStreaming() {
        val dev = latestDevice.value
        if (!previewActive.get()) return
        if (!streamLock.tryLock()) return
        try {
            withContext(Dispatchers.IO) {
                if (!previewActive.get()) return@withContext
                val login = NetSDKManager.ensureLogin(
                    dev.ip, dev.port, dev.userName, dev.password.ifBlank { "admin" }
                )
                val handle = login.data
                if (!login.isSuccess || handle == null || handle == 0L) return@withContext
                runCatching { NativeSDK.nativeSetEventCallback(handle, true) }
                runCatching { NetSDKManager.stopStream(handle) }
                if (!previewActive.get()) return@withContext
                var retries = 0
                while (retries < 15 && previewActive.get()) {
                    val streams = NetSDKManager.getStreams(handle)
                    if (streams.isSuccess && streams.data != null && streams.data!!.length() > 0) {
                        val firstStream = streams.data!!.getJSONObject(0)
                        activeStreamId = firstStream.optInt("id", 101)
                        val w = firstStream.optInt("resolution_width", 0)
                        val h = firstStream.optInt("resolution_height", 0)
                        if (w > 0 && h > 0) {
                            videoAspectRatio = w.toFloat() / h.toFloat()
                        }
                        val started = NetSDKManager.startStream(activeStreamId, 2, handle)
                        if (started.isSuccess) {
                            lastFrameAt.set(System.currentTimeMillis())
                        }
                        break
                    }
                    delay(200)
                    retries++
                }
                if (!previewActive.get()) {
                    runCatching { NetSDKManager.stopStream(handle) }
                }
            }
        } finally {
            streamLock.unlock()
        }
    }

    DisposableEffect(lifecycleOwner, device.ip) {
        previewActive.set(true)
        NetSDKManager.setStreamFrameListener(object : NativeSDK.StreamFrameListener {
            override fun onStreamFrame(
                handle: Long, streamId: Int, streamType: Int,
                width: Int, height: Int, data: ByteArray,
                timestampUs: Long, keyFrame: Boolean
            ) {
                lastFrameAt.set(System.currentTimeMillis())
                if (width <= 0 || height <= 0 || data.isEmpty()) return
                try {
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.copyPixelsFromBuffer(ByteBuffer.wrap(data))
                    currentBitmap = bmp
                    videoAspectRatio = width.toFloat() / height.toFloat()
                } catch (_: Exception) {}
            }
        })
        NetSDKManager.setEventListener(object : NativeSDK.EventListener {
            override fun onEvent(handle: Long, eventType: Int, streamId: Int, message: String) {
                if (latestRecording.value) return
                if (eventType == 2 || eventType == 3) {
                    scope.launch { ensureStreaming() }
                }
            }
        })
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    previewActive.set(true)
                    scope.launch { ensureStreaming() }
                }
                Lifecycle.Event.ON_STOP -> {
                    previewActive.set(false)
                    scope.launch(Dispatchers.IO) {
                        runCatching { NetSDKManager.stopStream() }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            scope.launch { ensureStreaming() }
        }
        onDispose {
            previewActive.set(false)
            lifecycleOwner.lifecycle.removeObserver(observer)
            NetSDKManager.setEventListener(null)
            NetSDKManager.setStreamFrameListener(null)
            scope.launch(Dispatchers.IO) {
                runCatching { NetSDKManager.stopStream() }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!captureDir.exists()) captureDir.mkdirs()
        if (!recordDir.exists()) recordDir.mkdirs()
        while (isActive) {
            delay(3_000)
            if (!previewActive.get() || latestRecording.value) continue
            val last = lastFrameAt.get()
            if (last == 0L || System.currentTimeMillis() - last > 8_000) {
                ensureStreaming()
            }
        }
    }

    if (isFullscreen) {
        FullscreenVideoView(
            bitmap = currentBitmap,
            onExit = {
                isFullscreen = false
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                showSystemBars(activity)
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = AppColors.Background,
            topBar = { PreviewTopBar(device.name, isRecording, onBack, onEditDevice) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
            VideoPreviewArea(
                bitmap = currentBitmap,
                aspectRatio = videoAspectRatio,
                onFullscreen = {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    hideSystemBars(activity)
                    isFullscreen = true
                }
            )

            Spacer(Modifier.height(12.dp))

            ActionButtonsCard(
                onCapture = {
                    scope.launch {
                        val ts = sdf.format(java.util.Date())
                        val file = File(captureDir, "IMG_${ts}.jpg")
                        val result = withContext(Dispatchers.IO) {
                            NetSDKManager.getThermalCapture(0, file.absolutePath)
                        }
                        Toast.makeText(
                            context,
                            if (result.isSuccess) context.getString(R.string.preview_capture_ok, file.name) else context.getString(R.string.preview_capture_fail),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onRecord = {
                    scope.launch {
                        if (isRecording) {
                            withContext(Dispatchers.IO) {
                                NetSDKManager.stopStreamRecord(activeStreamId)
                            }
                            isRecording = false
                            Toast.makeText(context, context.getString(R.string.preview_record_saved), Toast.LENGTH_SHORT).show()
                        } else {
                            val ts = sdf.format(java.util.Date())
                            val file = File(recordDir, "VID_${ts}.mp4")
                            val result = withContext(Dispatchers.IO) {
                                NetSDKManager.startStreamRecord(activeStreamId, file.absolutePath)
                            }
                            if (result.isSuccess) {
                                isRecording = true
                                Toast.makeText(context, context.getString(R.string.preview_record_started), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.preview_record_fail), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                isRecording = isRecording,
                onCalibrate = onCalibrate,
                showPaletteMenu = showPaletteMenu,
                paletteList = paletteList,
                currentPaletteRefNo = currentPaletteRefNo,
                onPaletteClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            NetSDKManager.getThermalPresetPalettes()
                        }
                        if (result.isSuccess && result.data != null) {
                            paletteList = result.data
                        }
                        val cur = withContext(Dispatchers.IO) {
                            NetSDKManager.getThermalPalette()
                        }
                        if (cur.isSuccess && cur.data != null) {
                            currentPaletteRefNo = cur.data!!.optInt("ref_no", -1)
                        }
                        showPaletteMenu = true
                    }
                },
                onPaletteDismiss = { showPaletteMenu = false },
                onPaletteSelect = { refNo ->
                    showPaletteMenu = false
                    currentPaletteRefNo = refNo
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            NetSDKManager.setThermalPalette("""{"ref_no":$refNo}""")
                        }
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            RecentAlarmsCard(
                alarms = alarms,
                fallbackThumb = device.thumbnailPath,
                onAlarmClick = { alarm ->
                    onMarkAlarmRead(alarm.id)
                    detailAlarm = alarm
                }
            )

            Spacer(Modifier.height(24.dp))
        }
        }

        detailAlarm?.let { alarm ->
            BackHandler { detailAlarm = null }
            MessageDetailScreen(
                item = MessageItem(
                    alarm = alarm,
                    deviceName = device.name,
                    thumbnailPath = alarm.imagePath.ifBlank { device.thumbnailPath }
                ),
                onBack = { detailAlarm = null }
            )
        }
    }
}

private fun hideSystemBars(activity: Activity?) {
    activity?.window?.let { w ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.insetsController?.hide(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
            w.insetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }
}

private fun showSystemBars(activity: Activity?) {
    activity?.window?.let { w ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.insetsController?.show(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewTopBar(
    deviceName: String,
    isRecording: Boolean,
    onBack: () -> Unit,
    onEditDevice: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRecording) {
                    val infiniteTransition = rememberInfiniteTransition(label = "rec")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "recAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color.Red.copy(alpha = alpha), CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    deviceName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        actions = {
            IconButton(onClick = onEditDevice) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.preview_device_config),
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
            scrolledContainerColor = Color.White
        )
    )
}

@Composable
private fun VideoPreviewArea(
    bitmap: Bitmap?,
    aspectRatio: Float,
    onFullscreen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .aspectRatio(aspectRatio)
            .background(Color(0xFF1A1A3E)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.preview_thermal_video),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onFullscreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Fullscreen,
                    contentDescription = stringResource(R.string.preview_fullscreen),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun FullscreenVideoView(
    bitmap: Bitmap?,
    onExit: () -> Unit
) {
    BackHandler { onExit() }

    var showControls by remember { mutableStateOf(true) }
    var controlsKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(controlsKey) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
                if (showControls) controlsKey++
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.preview_thermal_video),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp),
                strokeWidth = 3.dp
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x40000000))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onExit) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.preview_exit_fullscreen),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtonsCard(
    onCapture: () -> Unit,
    onRecord: () -> Unit,
    isRecording: Boolean,
    onCalibrate: () -> Unit,
    showPaletteMenu: Boolean,
    paletteList: JSONArray?,
    currentPaletteRefNo: Int,
    onPaletteClick: () -> Unit,
    onPaletteDismiss: () -> Unit,
    onPaletteSelect: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(Icons.Outlined.CameraAlt, stringResource(R.string.preview_capture), onCapture)
            ActionButton(
                Icons.Outlined.Videocam,
                if (isRecording) stringResource(R.string.preview_stop) else stringResource(R.string.preview_record),
                onRecord,
                tint = if (isRecording) Color.Red else AppColors.TextPrimary
            )
            Box {
                ActionButton(Icons.Outlined.Palette, stringResource(R.string.preview_palette), onPaletteClick)
                PaletteDropdown(
                    expanded = showPaletteMenu,
                    palettes = paletteList,
                    currentRefNo = currentPaletteRefNo,
                    onDismiss = onPaletteDismiss,
                    onSelect = onPaletteSelect
                )
            }
            ActionButton(Icons.Outlined.ShutterSpeed, stringResource(R.string.preview_calibrate), onCalibrate)
        }
    }
}

@Composable
private fun PaletteDropdown(
    expanded: Boolean,
    palettes: JSONArray?,
    currentRefNo: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(10.dp),
        containerColor = Color.White,
        shadowElevation = 8.dp
    ) {
        if (palettes == null || palettes.length() == 0) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.preview_no_palette), fontSize = 13.sp, color = AppColors.TextSecondary) },
                onClick = onDismiss
            )
        } else {
            for (i in 0 until palettes.length()) {
                val item = palettes.getJSONObject(i)
                val refNo = item.optInt("ref_no", 0)
                val name = item.optString("name", "Palette $refNo")
                val isSelected = refNo == currentRefNo
                DropdownMenuItem(
                    text = {
                        Text(
                            name,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF2673F9) else AppColors.TextPrimary
                        )
                    },
                    onClick = { onSelect(refNo) },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(40.dp)
                )
                if (i < palettes.length() - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        thickness = 0.5.dp,
                        color = Color(0x0F1D2129)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = AppColors.TextPrimary
) {
    Column(
        modifier = Modifier
            .width(77.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(Color(0xFFF6F7F9), RoundedCornerShape(8.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = tint,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun RecentAlarmsCard(
    alarms: List<AlarmMessage>,
    fallbackThumb: String,
    onAlarmClick: (AlarmMessage) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                stringResource(R.string.preview_recent_alarms),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )

            Spacer(Modifier.height(10.dp))

            if (alarms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.preview_no_alarms),
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )
                }
            } else {
                alarms.take(3).forEachIndexed { index, alarm ->
                    if (index > 0) {
                        Spacer(Modifier.height(8.dp))
                    }
                    AlarmItem(
                        alarm = alarm,
                        thumbnailPath = alarm.imagePath.ifBlank { fallbackThumb },
                        onClick = { onAlarmClick(alarm) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmItem(
    alarm: AlarmMessage,
    thumbnailPath: String,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    var bitmap by remember(thumbnailPath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(thumbnailPath) {
        bitmap = if (thumbnailPath.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                    android.graphics.BitmapFactory.decodeFile(thumbnailPath, opts)?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
        } else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(Color.White)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 74.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2A2D5E)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Outlined.Thermostat,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                alarm.title.ifBlank { alarm.type.ifBlank { stringResource(R.string.msg_type_temp) } },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    dateFormat.format(Date(alarm.timestamp)),
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Thermostat,
                    contentDescription = null,
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "%.1f℃".format(Locale.US, alarm.temperature),
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}
