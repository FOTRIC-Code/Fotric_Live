@file:OptIn(ExperimentalFoundationApi::class)

package com.irtek.live.ui.message

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irtek.live.R
import com.irtek.live.data.entity.AlarmMessage
import com.irtek.live.ui.theme.AppColors
import com.irtek.live.ui.theme.AppSpacing
import com.irtek.live.ui.theme.AppTypo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MessageItem(
    val alarm: AlarmMessage,
    val deviceName: String,
    val thumbnailPath: String
)

@Composable
fun MessageScreen(
    messages: List<MessageItem>,
    onDelete: (List<Long>) -> Unit = {},
    onMarkRead: (Long) -> Unit = {}
) {
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var detailItem by remember { mutableStateOf<MessageItem?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(messages) {
        val valid = messages.map { it.alarm.id }.toSet()
        selectedIds = selectedIds.filter { it in valid }.toSet()
        if (selecting && messages.isEmpty()) {
            selecting = false
            selectedIds = emptySet()
        }
        detailItem?.let { current ->
            if (messages.none { it.alarm.id == current.alarm.id }) {
                detailItem = null
            } else {
                detailItem = messages.first { it.alarm.id == current.alarm.id }
            }
        }
    }

    if (detailItem != null) {
        BackHandler { detailItem = null }
    } else if (selecting) {
        BackHandler {
            selecting = false
            selectedIds = emptySet()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
        ) {
            MessageTopBar(
                selecting = selecting,
                selectedCount = selectedIds.size,
                totalCount = messages.size,
                canSelect = messages.isNotEmpty(),
                onEnterSelect = { selecting = true },
                onCancelSelect = {
                    selecting = false
                    selectedIds = emptySet()
                },
                onToggleSelectAll = {
                    selectedIds = if (selectedIds.size == messages.size) {
                        emptySet()
                    } else {
                        messages.map { it.alarm.id }.toSet()
                    }
                },
                onDeleteClick = {
                    if (selectedIds.isNotEmpty()) confirmDelete = true
                }
            )

            Spacer(Modifier.height(12.dp))

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.msg_empty), fontSize = 15.sp, color = AppColors.TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AppSpacing.ScreenPaddingH,
                        end = AppSpacing.ScreenPaddingH,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.alarm.id }) { item ->
                        val selected = item.alarm.id in selectedIds
                        MessageCard(
                            item = item,
                            dateFormat = dateFormat,
                            selecting = selecting,
                            selected = selected,
                            onClick = {
                                if (selecting) {
                                    selectedIds = if (selected) {
                                        selectedIds - item.alarm.id
                                    } else {
                                        selectedIds + item.alarm.id
                                    }
                                } else {
                                    onMarkRead(item.alarm.id)
                                    detailItem = item
                                }
                            },
                            onLongClick = {
                                if (!selecting) {
                                    selecting = true
                                    selectedIds = setOf(item.alarm.id)
                                }
                            }
                        )
                    }
                }
            }
        }

        detailItem?.let { item ->
            MessageDetailScreen(
                item = item,
                onBack = { detailItem = null }
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.msg_delete_title)) },
            text = { Text(String.format(stringResource(R.string.msg_delete_confirm), selectedIds.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = selectedIds.toList()
                        confirmDelete = false
                        selecting = false
                        selectedIds = emptySet()
                        onDelete(ids)
                    }
                ) { Text(stringResource(R.string.common_delete), color = Color(0xFFDC2626)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun MessageTopBar(
    selecting: Boolean,
    selectedCount: Int,
    totalCount: Int,
    canSelect: Boolean,
    onEnterSelect: () -> Unit,
    onCancelSelect: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            TextButton(onClick = onCancelSelect) {
                Text(stringResource(R.string.common_cancel), color = AppColors.TextPrimary)
            }
            Text(
                String.format(stringResource(R.string.common_selected_count), selectedCount),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onToggleSelectAll) {
                Text(
                    if (selectedCount == totalCount && totalCount > 0) stringResource(R.string.common_deselect_all) else stringResource(R.string.common_select_all),
                    color = Color(0xFF0256FF)
                )
            }
            TextButton(
                onClick = onDeleteClick,
                enabled = selectedCount > 0
            ) {
                Text(
                    stringResource(R.string.common_delete),
                    color = if (selectedCount > 0) Color(0xFFDC2626) else AppColors.TextSecondary
                )
            }
        } else {
            Text(
                stringResource(R.string.nav_messages),
                fontSize = AppTypo.TitleSize,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            if (canSelect) {
                TextButton(onClick = onEnterSelect) {
                    Text(stringResource(R.string.common_select), color = Color(0xFF0256FF))
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    item: MessageItem,
    dateFormat: SimpleDateFormat,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(AppSpacing.CardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selecting) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF0256FF)
                    )
                )
                Spacer(Modifier.width(4.dp))
            }

            var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(item.thumbnailPath) {
                bitmap = if (item.thumbnailPath.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                            BitmapFactory.decodeFile(item.thumbnailPath, opts)?.asImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    }
                } else null
            }

            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2A2D5E)),
                contentAlignment = Alignment.Center
            ) {
                bitmap?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val alarmLabel = alarmTypeLabel(item.alarm)

                Text(
                    alarmLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFDC2626)
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    item.deviceName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    maxLines = 1
                )

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        dateFormat.format(Date(item.alarm.timestamp)),
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary
                    )
                }
            }

            if (!selecting) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun MessageDetailScreen(
    item: MessageItem,
    onBack: () -> Unit
) {
    val fullTimeFormat = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.thumbnailPath) {
        bitmap = if (item.thumbnailPath.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
                    BitmapFactory.decodeFile(item.thumbnailPath, opts)?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = AppColors.TextPrimary
                )
            }
            Text(
                stringResource(R.string.msg_detail_title),
                fontSize = AppTypo.TitleSize,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.ScreenPaddingH, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (bitmap != null && bitmap!!.height > 0) {
                            Modifier.aspectRatio(
                                bitmap!!.width.toFloat() / bitmap!!.height.toFloat()
                            )
                        } else {
                            Modifier.aspectRatio(4f / 3f)
                        }
                    )
                    .clip(RoundedCornerShape(AppSpacing.CardCorner))
                    .background(Color(0xFF2A2D5E)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Outlined.Thermostat,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppSpacing.CardCorner),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        item.alarm.title.ifBlank { alarmTypeLabel(item.alarm) },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        alarmTypeLabel(item.alarm),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFDC2626)
                    )

                    Spacer(Modifier.height(16.dp))
                    DetailRow(stringResource(R.string.msg_type), alarmTypeLabel(item.alarm))
                    DetailRow(stringResource(R.string.common_device_name), item.deviceName)
                    DetailRow(stringResource(R.string.msg_time), fullTimeFormat.format(Date(item.alarm.timestamp)))
                    if (item.alarm.markerName.isNotBlank()) {
                        DetailRow(
                            stringResource(R.string.msg_marker),
                            if (item.alarm.markerName == "global") stringResource(R.string.common_global) else item.alarm.markerName
                        )
                    }
                    if (item.alarm.temperature != 0.0) {
                        DetailRow(
                            stringResource(R.string.msg_current_temp),
                            "%.1f℃".format(Locale.US, item.alarm.temperature)
                        )
                    }
                    if (item.alarm.threshold != 0.0) {
                        DetailRow(
                            stringResource(R.string.msg_threshold),
                            "%.1f℃".format(Locale.US, item.alarm.threshold)
                        )
                    }
                    if (item.alarm.content.isNotBlank()) {
                        DetailRow(stringResource(R.string.common_content), item.alarm.content)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = AppColors.TextSecondary,
            modifier = Modifier.width(120.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            fontSize = 14.sp,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
    HorizontalDivider(color = Color(0xFFF0F0F0))
}

@Composable
private fun alarmTypeLabel(alarm: AlarmMessage): String {
    val deviceAlarmLabel = stringResource(R.string.msg_type_device)
    val intrusionLabel = stringResource(R.string.msg_type_intrusion)
    val tempLabel = stringResource(R.string.msg_type_temp)
    return when {
        alarm.type.isNotBlank() &&
            !alarm.type.contains("温度报警") &&
            !alarm.type.contains("设备报警") -> alarm.type
        alarm.type.contains("入侵") -> intrusionLabel
        alarm.type.contains("温度") || alarm.type.contains("temp", ignoreCase = true) -> tempLabel
        alarm.type.contains("设备报警") -> deviceAlarmLabel
        alarm.type.isNotBlank() -> alarm.type
        else -> tempLabel
    }
}
