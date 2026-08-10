package com.irtek.live.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

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

    fun getLanguage(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, LANG_ZH) ?: LANG_ZH

    fun setLanguage(context: Context, language: String) {
        prefs(context).edit { putString(KEY_LANGUAGE, language) }
    }
}
