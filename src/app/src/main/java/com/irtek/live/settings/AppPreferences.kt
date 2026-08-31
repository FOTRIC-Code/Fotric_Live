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

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isNotificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATIONS, true)

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_NOTIFICATIONS, enabled) }
    }
}
