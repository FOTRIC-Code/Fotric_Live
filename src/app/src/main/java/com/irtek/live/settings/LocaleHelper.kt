package com.irtek.live.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Maps system language to app language: Chinese → zh, everything else → en.
 * Must set [LocaleList] (not only deprecated [Configuration.setLocale]) so resource
 * resolution does not fall back to default Chinese [values] for ko/es/etc.
 */
object LocaleHelper {
    fun wrap(context: Context): Context {
        val locale = if (isSystemChinese(context)) {
            Locale.SIMPLIFIED_CHINESE
        } else {
            Locale.ENGLISH
        }
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        return context.createConfigurationContext(config)
    }

    private fun isSystemChinese(context: Context): Boolean {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        } ?: Locale.getDefault()
        return locale.language.startsWith("zh", ignoreCase = true)
    }
}
