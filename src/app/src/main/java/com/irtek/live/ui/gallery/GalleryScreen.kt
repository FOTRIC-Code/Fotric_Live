package com.irtek.live.ui.gallery

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irtek.live.ui.theme.AppColors
import com.irtek.live.ui.theme.AppSpacing
import com.irtek.live.ui.theme.AppTypo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GalleryTab { ALL, PHOTOS, VIDEOS }

data class GalleryItem(
    val file: File,
    val isVideo: Boolean,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    captureDir: File,
    recordDir: File
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("全部", "图片", "视频")

    var items by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    LaunchedEffect(selectedTab) {
        items = withContext(Dispatchers.IO) {
            val photos = if (captureDir.exists()) {
                captureDir.listFiles()
                    ?.filter { it.extension.lowercase() in listOf("jpg", "jpeg", "png", "bmp") }
                    ?.map { GalleryItem(it, false, it.lastModified()) }
                    ?: emptyList()
            } else emptyList()

            val videos = if (recordDir.exists()) {
                recordDir.listFiles()
                    ?.filter { it.extension.lowercase() in listOf("mp4", "avi", "mkv") }
                    ?.map { GalleryItem(it, true, it.lastModified()) }
                    ?: emptyList()
            } else emptyList()

            when (selectedTab) {
                1 -> photos.sortedByDescending { it.timestamp }
                2 -> videos.sortedByDescending { it.timestamp }
                else -> (photos + videos).sortedByDescending { it.timestamp }
            }
        }
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
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "图库",
                fontSize = AppTypo.TitleSize,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.ScreenPaddingH),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.TabGap)
        ) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == selectedTab
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) AppColors.TabSelectedBg else AppColors.TabUnselectedBg,
                    modifier = Modifier
                        .height(AppSpacing.TabHeight)
                        .clickable { selectedTab = index }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(
                            label,
                            fontSize = AppTypo.TabSize,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) Color.White else AppColors.TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无内容", fontSize = 15.sp, color = AppColors.TextSecondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items, key = { it.file.absolutePath }) { item ->
                    GalleryThumbnail(item)
                }
            }
        }
    }
}

@Composable
private fun GalleryThumbnail(item: GalleryItem) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF2A2D5E))
            .clickable {
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        item.file
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, if (item.isVideo) "video/*" else "image/*")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            },
        contentAlignment = Alignment.Center
    ) {
        var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(item.file.absolutePath, item.timestamp) {
            bitmap = withContext(Dispatchers.IO) {
                try {
                    if (item.isVideo) {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(item.file.absolutePath)
                        val frame = retriever.frameAtTime
                        retriever.release()
                        frame?.asImageBitmap()
                    } else {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        BitmapFactory.decodeFile(item.file.absolutePath, opts)?.asImageBitmap()
                    }
                } catch (_: Exception) { null }
            }
        }
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (item.isVideo) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = "视频",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(32.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    dateFormat.format(Date(item.timestamp)),
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}
