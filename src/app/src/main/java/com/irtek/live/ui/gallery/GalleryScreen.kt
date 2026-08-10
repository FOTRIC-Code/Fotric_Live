@file:OptIn(ExperimentalFoundationApi::class)

package com.irtek.live.ui.gallery

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irtek.live.R
import com.irtek.live.ui.theme.AppColors
import com.irtek.live.ui.theme.AppSpacing
import com.irtek.live.ui.theme.AppTypo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GalleryItem(
    val file: File,
    val isVideo: Boolean,
    val timestamp: Long
)

@Composable
fun GalleryScreen(
    captureDir: File,
    recordDir: File
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.gallery_tab_all),
        stringResource(R.string.gallery_tab_photo),
        stringResource(R.string.gallery_tab_video)
    )

    var items by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    suspend fun loadItems(tab: Int): List<GalleryItem> = withContext(Dispatchers.IO) {
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

        when (tab) {
            1 -> photos.sortedByDescending { it.timestamp }
            2 -> videos.sortedByDescending { it.timestamp }
            else -> (photos + videos).sortedByDescending { it.timestamp }
        }
    }

    LaunchedEffect(selectedTab, refreshKey) {
        previewIndex = null
        items = loadItems(selectedTab)
        val valid = items.map { it.file.absolutePath }.toSet()
        selectedPaths = selectedPaths.filter { it in valid }.toSet()
        if (selecting && items.isEmpty()) {
            selecting = false
            selectedPaths = emptySet()
        }
    }

    if (previewIndex != null) {
        BackHandler { previewIndex = null }
    } else if (selecting) {
        BackHandler {
            selecting = false
            selectedPaths = emptySet()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
        ) {
            GalleryTopBar(
                selecting = selecting,
                selectedCount = selectedPaths.size,
                totalCount = items.size,
                canSelect = items.isNotEmpty(),
                onEnterSelect = { selecting = true },
                onCancelSelect = {
                    selecting = false
                    selectedPaths = emptySet()
                },
                onToggleSelectAll = {
                    selectedPaths = if (selectedPaths.size == items.size) {
                        emptySet()
                    } else {
                        items.map { it.file.absolutePath }.toSet()
                    }
                },
                onDeleteClick = {
                    if (selectedPaths.isNotEmpty()) confirmDelete = true
                }
            )

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
                            .clickable(enabled = !selecting) {
                                selectedTab = index
                            }
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
                    Text(stringResource(R.string.gallery_empty), fontSize = 15.sp, color = AppColors.TextSecondary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(items, key = { _, item -> item.file.absolutePath }) { index, item ->
                        val path = item.file.absolutePath
                        val selected = path in selectedPaths
                        GalleryThumbnail(
                            item = item,
                            selecting = selecting,
                            selected = selected,
                            onClick = {
                                if (selecting) {
                                    selectedPaths = if (selected) {
                                        selectedPaths - path
                                    } else {
                                        selectedPaths + path
                                    }
                                } else {
                                    previewIndex = index
                                }
                            },
                            onLongClick = {
                                if (!selecting) {
                                    selecting = true
                                    selectedPaths = setOf(path)
                                }
                            }
                        )
                    }
                }
            }
        }

        previewIndex?.let { index ->
            if (items.isNotEmpty()) {
                GalleryPreview(
                    items = items,
                    initialIndex = index.coerceIn(0, items.lastIndex),
                    onClose = { previewIndex = null }
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.gallery_delete_title)) },
            text = { Text(String.format(stringResource(R.string.gallery_delete_confirm), selectedPaths.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val paths = selectedPaths.toList()
                        confirmDelete = false
                        selecting = false
                        selectedPaths = emptySet()
                        paths.forEach { path ->
                            runCatching { File(path).delete() }
                        }
                        refreshKey++
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
private fun GalleryTopBar(
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
                stringResource(R.string.nav_gallery),
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
private fun GalleryThumbnail(
    item: GalleryItem,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF2A2D5E))
            .then(
                if (selected) Modifier.border(2.dp, Color(0xFF0256FF), RoundedCornerShape(6.dp))
                else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(item.file.absolutePath, item.timestamp) {
            bitmap = withContext(Dispatchers.IO) {
                loadGalleryBitmap(item, sampleSize = 4)
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
                contentDescription = stringResource(R.string.common_video),
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

        if (selecting) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (selected) Color(0xFF0256FF) else Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
            )
        }
    }
}

@Composable
private fun GalleryPreview(
    items: List<GalleryItem>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { items.size }
    )
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    val current = items.getOrNull(pagerState.currentPage)

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { items[it].file.absolutePath }
        ) { page ->
            val item = items[page]
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                var bitmap by remember(item.file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(item.file.absolutePath, item.timestamp) {
                    bitmap = withContext(Dispatchers.IO) {
                        loadGalleryBitmap(item, sampleSize = if (item.isVideo) 2 else 1)
                    }
                }
                bitmap?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (item.isVideo) {
                    IconButton(
                        onClick = {
                            try {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    item.file
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "video/mp4")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = stringResource(R.string.common_play),
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(48.dp)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = Color.White
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    current?.file?.name.orEmpty(),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                if (current != null) {
                    Text(
                        "${pagerState.currentPage + 1}/${items.size}  ·  ${dateFormat.format(Date(current.timestamp))}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun loadGalleryBitmap(item: GalleryItem, sampleSize: Int): ImageBitmap? {
    return try {
        if (item.isVideo) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(item.file.absolutePath)
            val frame = retriever.frameAtTime
            retriever.release()
            frame?.asImageBitmap()
        } else {
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) }
            BitmapFactory.decodeFile(item.file.absolutePath, opts)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}
