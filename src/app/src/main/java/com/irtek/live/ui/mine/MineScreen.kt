package com.irtek.live.ui.mine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.irtek.live.R
import com.irtek.live.settings.AlarmNotifier
import com.irtek.live.settings.AppPreferences
import com.irtek.live.ui.theme.AppColors
import com.irtek.live.ui.theme.AppTypo
import com.irtek.netsdk.NetSDKManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MineScreen(
    onLanguageChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    var notificationEnabled by remember {
        mutableStateOf(AppPreferences.isNotificationsEnabled(context))
    }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val currentLang = remember { AppPreferences.getLanguage(context) }
    val languageLabel = if (currentLang == AppPreferences.LANG_EN) {
        stringResource(R.string.mine_lang_en)
    } else {
        stringResource(R.string.mine_lang_zh)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            AppPreferences.setNotificationsEnabled(context, true)
            notificationEnabled = true
            AlarmNotifier.ensureChannel(context)
        } else {
            AppPreferences.setNotificationsEnabled(context, false)
            notificationEnabled = false
            Toast.makeText(
                context,
                context.getString(R.string.mine_notification_permission_hint),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun setNotification(enabled: Boolean) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
            AppPreferences.setNotificationsEnabled(context, true)
            notificationEnabled = true
            AlarmNotifier.ensureChannel(context)
        } else {
            AppPreferences.setNotificationsEnabled(context, false)
            notificationEnabled = false
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
                stringResource(R.string.mine_title),
                fontSize = AppTypo.TitleSize,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            SettingsRow(
                icon = Icons.Outlined.Notifications,
                label = stringResource(R.string.mine_notifications),
                trailing = {
                    Switch(
                        checked = notificationEnabled,
                        onCheckedChange = { setNotification(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0079FF),
                            checkedBorderColor = Color.Transparent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFE0E0E0),
                            uncheckedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = Color(0x0F1D2129)
            )

            SettingsRow(
                icon = Icons.Outlined.Language,
                label = stringResource(R.string.mine_language),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            languageLabel,
                            fontSize = 14.sp,
                            color = AppColors.TextSecondary
                        )
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                onClick = { showLanguageDialog = true }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = Color(0x0F1D2129)
            )

            SettingsRow(
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.mine_about),
                trailing = {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { showAboutDialog = true }
            )
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.mine_choose_language)) },
            text = {
                Column {
                    LanguageOption(
                        label = stringResource(R.string.mine_lang_zh),
                        selected = currentLang == AppPreferences.LANG_ZH
                    ) {
                        if (currentLang != AppPreferences.LANG_ZH) {
                            AppPreferences.setLanguage(context, AppPreferences.LANG_ZH)
                            showLanguageDialog = false
                            onLanguageChanged()
                        } else {
                            showLanguageDialog = false
                        }
                    }
                    LanguageOption(
                        label = stringResource(R.string.mine_lang_en),
                        selected = currentLang == AppPreferences.LANG_EN
                    ) {
                        if (currentLang != AppPreferences.LANG_EN) {
                            AppPreferences.setLanguage(context, AppPreferences.LANG_EN)
                            showLanguageDialog = false
                            onLanguageChanged()
                        } else {
                            showLanguageDialog = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.mine_cancel))
                }
            }
        )
    }

    if (showAboutDialog) {
        var sdkVersion by remember { mutableStateOf("-") }
        LaunchedEffect(Unit) {
            sdkVersion = withContext(Dispatchers.IO) {
                val r = NetSDKManager.getSdkVersion()
                if (r.isSuccess && r.data != null) {
                    val d = r.data!!
                    "${d.optInt("major")}.${d.optInt("minor")}.${d.optInt("patch")}.${d.optInt("build")}"
                } else "-"
            }
        }
        val appVersion = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "1.0.0"
        }

        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.mine_about_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${stringResource(R.string.mine_app_version)}：$appVersion")
                    Text("${stringResource(R.string.mine_sdk_version)}：$sdkVersion")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.mine_ok))
                }
            }
        )
    }
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0079FF))
        )
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 15.sp, color = AppColors.TextPrimary)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = AppColors.NavActive,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}
