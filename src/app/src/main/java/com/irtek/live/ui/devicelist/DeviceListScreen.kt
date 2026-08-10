package com.irtek.live.ui.devicelist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalDensity
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.res.stringResource
import com.irtek.live.R
import com.irtek.live.ui.theme.AppColors
import com.irtek.live.ui.theme.AppSpacing
import com.irtek.live.ui.theme.AppTypo

enum class DeviceStatus { ONLINE, OFFLINE, ALARM }

data class DeviceItem(
    val id: String,
    val name: String,
    val status: DeviceStatus,
    val model: String,
    val ip: String,
    val sn: String,
    val thumbnailPath: String = "",
    val updatedAt: Long = 0L,
    val thumbnailColor: Color = Color(0xFF1A237E)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    devices: List<DeviceItem>,
    isRefreshing: Boolean = false,
    selectedNav: Int = 0,
    onRefresh: () -> Unit = {},
    onAddDevice: () -> Unit = {},
    onDeviceClick: (DeviceItem) -> Unit = {},
    onDeleteDevice: (DeviceItem) -> Unit = {},
    onEditDevice: (DeviceItem) -> Unit = {},
    onAlarmConfig: (DeviceItem) -> Unit = {},
    onMaintenance: (DeviceItem) -> Unit = {},
    onNavSelect: (Int) -> Unit = {},
    messageContent: @Composable () -> Unit = {},
    galleryContent: @Composable () -> Unit = {},
    mineContent: @Composable () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabAllFmt = stringResource(R.string.device_tab_all)
    val tabOnlineFmt = stringResource(R.string.device_tab_online)
    val tabOfflineFmt = stringResource(R.string.device_tab_offline)
    val tabAlarmFmt = stringResource(R.string.device_tab_alarm)
    val tabs = remember(devices, tabAllFmt, tabOnlineFmt, tabOfflineFmt, tabAlarmFmt) {
        val onlineCount = devices.count { it.status == DeviceStatus.ONLINE }
        val offlineCount = devices.count { it.status == DeviceStatus.OFFLINE }
        val alarmCount = devices.count { it.status == DeviceStatus.ALARM }
        listOf(
            String.format(tabAllFmt, devices.size),
            String.format(tabOnlineFmt, onlineCount),
            String.format(tabOfflineFmt, offlineCount),
            String.format(tabAlarmFmt, alarmCount)
        )
    }

    val filteredDevices = remember(devices, selectedTab) {
        val filtered = when (selectedTab) {
            1 -> devices.filter { it.status == DeviceStatus.ONLINE }
            2 -> devices.filter { it.status == DeviceStatus.OFFLINE }
            3 -> devices.filter { it.status == DeviceStatus.ALARM }
            else -> devices
        }
        filtered.sortedWith(
            compareBy<DeviceItem> {
                when (it.status) {
                    DeviceStatus.ONLINE -> 0
                    DeviceStatus.ALARM -> 1
                    DeviceStatus.OFFLINE -> 2
                }
            }
                .thenBy { it.name.lowercase() }
                .thenBy { it.sn.lowercase() }
        )
    }

    val pullState = rememberPullToRefreshState()
    val pullOffsetDp = with(LocalDensity.current) {
        (pullState.distanceFraction * 80f).dp
    }

    Scaffold(
        containerColor = AppColors.Background,
        bottomBar = {
            BottomNavBar(selectedNav) { onNavSelect(it) }
        }
    ) { padding ->
        if (selectedNav != 0) {
            androidx.activity.compose.BackHandler { onNavSelect(0) }
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (selectedNav) {
                    1 -> messageContent()
                    2 -> galleryContent()
                    3 -> mineContent()
                }
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TitleBar(onAdd = onAddDevice)
            Spacer(Modifier.height(12.dp))
            FilterTabs(tabs, selectedTab) { selectedTab = it }
            Spacer(Modifier.height(10.dp))
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                state = pullState,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = pullOffsetDp),
                    contentPadding = PaddingValues(
                        start = AppSpacing.ScreenPaddingH,
                        end = AppSpacing.ScreenPaddingH,
                        bottom = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.CardGap)
                ) {
                    items(filteredDevices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            onClick = {
                                if (device.status != DeviceStatus.OFFLINE) {
                                    onDeviceClick(device)
                                }
                            },
                            onDelete = { onDeleteDevice(device) },
                            onEditDevice = { onEditDevice(device) },
                            onAlarmConfig = { onAlarmConfig(device) },
                            onMaintenance = { onMaintenance(device) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleBar(onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            stringResource(R.string.device_title),
            fontSize = AppTypo.TitleSize,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary
        )
        IconButton(onClick = onAdd) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.device_add),
                tint = AppColors.TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun FilterTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.ScreenPaddingH),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.TabGap)
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = index == selected
            Surface(
                shape = CircleShape,
                color = if (isSelected) AppColors.TabSelectedBg else AppColors.TabUnselectedBg,
                modifier = Modifier
                    .height(AppSpacing.TabHeight)
                    .clickable { onSelect(index) }
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
}

@Composable
private fun DeviceCard(
    device: DeviceItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEditDevice: () -> Unit = {},
    onAlarmConfig: () -> Unit = {},
    onMaintenance: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val canOpen = device.status != DeviceStatus.OFFLINE

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (canOpen) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        shape = RoundedCornerShape(AppSpacing.CardCorner),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.CardPaddingH, AppSpacing.CardPaddingV)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .width(AppSpacing.ThumbWidth)
                        .height(AppSpacing.ThumbHeight)
                        .clip(RoundedCornerShape(AppSpacing.ThumbCorner))
                        .background(device.thumbnailColor),
                    contentAlignment = Alignment.Center
                ) {
                    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(device.thumbnailPath, device.updatedAt) {
                        bitmap = if (device.thumbnailPath.isNotBlank()) {
                            withContext(Dispatchers.IO) {
                                val f = File(device.thumbnailPath)
                                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
                            }
                        } else null
                    }
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap!!,
                            contentDescription = stringResource(R.string.device_thumb_cd),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(Modifier.width(AppSpacing.ThumbTextGap))

                // Info area
                Column(modifier = Modifier.weight(1f)) {
                    // Name + Badge row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            device.name,
                            fontSize = AppTypo.DeviceName,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        StatusBadge(device.status)
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        device.model,
                        fontSize = AppTypo.DeviceInfo,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "IP: ${device.ip}",
                        fontSize = AppTypo.DeviceInfo,
                        color = AppColors.TextSecondary,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "SN: ${device.sn}",
                        fontSize = AppTypo.DeviceInfo,
                        color = AppColors.TextSecondary,
                        maxLines = 1
                    )
                }

                // More icon + popup
                Box {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.common_more),
                        tint = AppColors.TextSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { showMenu = true }
                    )
                    DevicePopupMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onEditDevice = { showMenu = false; onEditDevice() },
                        onAlarmConfig = { showMenu = false; onAlarmConfig() },
                        onMaintenance = { showMenu = false; onMaintenance() },
                        onDelete = { showMenu = false; onDelete() }
                    )
                }
            }

        }
    }
}

@Composable
private fun DevicePopupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEditDevice: () -> Unit,
    onAlarmConfig: () -> Unit,
    onMaintenance: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 4.dp),
        shape = RoundedCornerShape(10.dp),
        containerColor = Color.White,
        shadowElevation = 8.dp
    ) {
        DeviceMenuItem(stringResource(R.string.device_menu_edit), AppColors.TextPrimary, onEditDevice)
        MenuDivider()
        DeviceMenuItem(stringResource(R.string.device_menu_alarm), AppColors.TextPrimary, onAlarmConfig)
        MenuDivider()
        DeviceMenuItem(stringResource(R.string.device_menu_maintenance), AppColors.TextPrimary, onMaintenance)
        MenuDivider()
        DeviceMenuItem(stringResource(R.string.device_menu_delete), Color(0xFFDC2626), onDelete)
    }
}

@Composable
private fun DeviceMenuItem(text: String, color: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text,
                fontSize = AppTypo.DeviceInfo,
                color = color,
                fontWeight = FontWeight.Normal
            )
        },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        modifier = Modifier.height(44.dp)
    )
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp),
        thickness = 0.5.dp,
        color = Color(0x0F1D2129)
    )
}

@Composable
private fun StatusBadge(status: DeviceStatus) {
    val (text, textColor, bgColor) = when (status) {
        DeviceStatus.ONLINE -> Triple(stringResource(R.string.device_status_online), AppColors.BadgeOnline, AppColors.BadgeOnlineBg)
        DeviceStatus.OFFLINE -> Triple(stringResource(R.string.device_status_offline), AppColors.BadgeOffline, AppColors.BadgeOfflineBg)
        DeviceStatus.ALARM -> Triple(stringResource(R.string.device_status_alarm), AppColors.BadgeAlarm, AppColors.BadgeAlarmBg)
    }
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(AppSpacing.BadgeCorner))
            .padding(horizontal = AppSpacing.BadgePaddingH, vertical = AppSpacing.BadgePaddingV),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = AppTypo.BadgeSize, color = textColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BottomNavBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        NavItem(stringResource(R.string.nav_devices), NavIcon.DEVICE),
        NavItem(stringResource(R.string.nav_messages), NavIcon.MESSAGE),
        NavItem(stringResource(R.string.nav_gallery), NavIcon.GALLERY),
        NavItem(stringResource(R.string.nav_mine), NavIcon.PROFILE)
    )
    Column {
        HorizontalDivider(thickness = 0.5.dp, color = AppColors.BottomNavBorder)
        NavigationBar(
            containerColor = AppColors.BottomNavBg,
            modifier = Modifier.height(AppSpacing.BottomNavHeight),
            tonalElevation = 0.dp
        ) {
            items.forEachIndexed { index, item ->
                val isActive = index == selected
                NavigationBarItem(
                    selected = isActive,
                    onClick = { onSelect(index) },
                    icon = {
                        NavIconView(
                            icon = item.icon,
                            active = isActive
                        )
                    },
                    label = {
                        Text(
                            item.label,
                            fontSize = AppTypo.NavLabel,
                            color = if (isActive) AppColors.NavActive else AppColors.NavInactive
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}

private data class NavItem(val label: String, val icon: NavIcon)

private enum class NavIcon { DEVICE, MESSAGE, GALLERY, PROFILE }

@Composable
private fun NavIconView(icon: NavIcon, active: Boolean) {
    val tint = if (active) AppColors.NavActive else AppColors.NavInactive
    val imageVector = when (icon) {
        NavIcon.DEVICE -> Icons.Filled.Devices
        NavIcon.MESSAGE -> Icons.Filled.ChatBubbleOutline
        NavIcon.GALLERY -> Icons.Filled.PhotoLibrary
        NavIcon.PROFILE -> Icons.Filled.PersonOutline
    }
    Icon(imageVector = imageVector, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
}
