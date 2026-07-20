package com.irtek.live.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppColors {
    val Background = Color(0xFFF0F2F5)
    val CardBackground = Color.White
    val TabSelectedBg = Color(0xFF323231)
    val TabUnselectedBg = Color.White
    val TextPrimary = Color(0xFF1D2129)
    val TextSecondary = Color(0xFF8A8F9A)
    val BadgeOnline = Color(0xFF0256FF)
    val BadgeOnlineBg = Color(0x140256FF) // 8% opacity
    val BadgeOffline = Color(0xFF8A8F9A)
    val BadgeOfflineBg = Color(0x148A8F9A)
    val BadgeAlarm = Color(0xFFDC2626)
    val BadgeAlarmBg = Color(0x14DC2626)
    val ButtonBg = Color(0xFFF8F9FB)
    val ButtonBorder = Color(0x2E1D2129) // 18% opacity
    val NavActive = Color(0xFF2673F9)
    val NavInactive = Color(0xFF1D2129)
    val BottomNavBg = Color(0xE6FFFFFF) // 90% opacity
    val BottomNavBorder = Color(0xFFF5F5F5)
    val Divider = Color(0xFFF5F5F5)
}

object AppSpacing {
    val ScreenPaddingH = 12.dp
    val CardCorner = 12.dp
    val CardGap = 8.dp
    val CardPaddingH = 12.dp
    val CardPaddingV = 12.dp
    val ThumbWidth = 114.dp
    val ThumbHeight = 88.dp
    val ThumbCorner = 6.dp
    val ThumbTextGap = 10.dp
    val BadgeCorner = 4.dp
    val BadgePaddingH = 8.dp
    val BadgePaddingV = 2.dp
    val ChannelBtnHeight = 32.dp
    val ChannelBtnCorner = 4.dp
    val TabHeight = 29.dp
    val TabCorner = 14.5.dp
    val TabGap = 8.dp
    val BottomNavHeight = 84.dp
}

object AppTypo {
    val TitleSize = 24.sp
    val TabSize = 13.sp
    val DeviceName = 15.sp
    val BadgeSize = 12.sp
    val DeviceInfo = 13.sp
    val ChannelBtn = 13.sp
    val NavLabel = 10.sp
}
