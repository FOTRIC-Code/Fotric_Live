package com.irtek.live.ui.message

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irtek.live.data.entity.AlarmMessage
import com.irtek.live.ui.theme.AppColors
import com.irtek.live.ui.theme.AppSpacing
import com.irtek.live.ui.theme.AppTypo
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
    onClick: (MessageItem) -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "消息",
                fontSize = AppTypo.TitleSize,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        }

        Spacer(Modifier.height(12.dp))

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无消息", fontSize = 15.sp, color = AppColors.TextSecondary)
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
                    MessageCard(item, dateFormat) { onClick(item) }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    item: MessageItem,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
            val bitmap = remember(item.thumbnailPath) {
                if (item.thumbnailPath.isNotBlank()) {
                    try {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        BitmapFactory.decodeFile(item.thumbnailPath, opts)?.asImageBitmap()
                    } catch (_: Exception) { null }
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
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val alarmLabel = when {
                    item.alarm.type.contains("入侵") -> "入侵报警"
                    item.alarm.type.contains("温度") || item.alarm.type.contains("temp") -> "温度报警"
                    item.alarm.type.isNotBlank() -> item.alarm.type
                    else -> "温度报警"
                }

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

            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
