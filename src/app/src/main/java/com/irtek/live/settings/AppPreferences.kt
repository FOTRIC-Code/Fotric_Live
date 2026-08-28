package com.irtek.live.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import java.util.Locale

/**
 * App-level settings persisted in SharedPreferences.
 */
object AppPreferences {
    private const val PREF_NAME = "live_settings"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"
    private const val KEY_LANGUAGE = "language" // "zh" | "en"

    const val LANG_ZH = "zh"
    const val LANG_EN = "en"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isNotificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATIONS, true)

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_NOTIFICATIONS, enabled) }
    }

    /**
     * Returns saved language, or follows system when unset:
     * Chinese system locales → zh, otherwise → en.
     */
    fun getLanguage(context: Context): String {
        val saved = prefs(context).getString(KEY_LANGUAGE, null)
        if (!saved.isNullOrBlank()) return saved
        return resolveSystemLanguage(context)
    }

    fun resolveSystemLanguage(context: Context): String {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        } ?: Locale.getDefault()
        return if (locale.language.startsWith("zh", ignoreCase = true)) LANG_ZH else LANG_EN
    }

    fun setLanguage(context: Context, language: String) {
        prefs(context).edit { putString(KEY_LANGUAGE, language) }
    }
}
