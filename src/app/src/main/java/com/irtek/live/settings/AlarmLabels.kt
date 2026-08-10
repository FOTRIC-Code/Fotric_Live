package com.irtek.live.settings

import android.content.Context
import com.irtek.live.R

/** Localized alarm type / level labels for non-Compose code. */
object AlarmLabels {
    fun condition(context: Context, alarmType: Int): String = when (alarmType) {
        1 -> context.getString(R.string.alarm_cond_high_gt)
        2 -> context.getString(R.string.alarm_cond_high_lt)
        3 -> context.getString(R.string.alarm_cond_low_gt)
        4 -> context.getString(R.string.alarm_cond_low_lt)
        5 -> context.getString(R.string.alarm_cond_avg_gt)
        6 -> context.getString(R.string.alarm_cond_avg_lt)
        7 -> context.getString(R.string.alarm_cond_diff_gt)
        8 -> context.getString(R.string.alarm_cond_diff_lt)
        else -> ""
    }

    fun level(context: Context, level: Int): String = when (level) {
        1 -> context.getString(R.string.alarm_level_alert)
        2 -> context.getString(R.string.alarm_level_warning)
        3 -> context.getString(R.string.alarm_level_alarm)
        else -> ""
    }

    fun tempAlarm(context: Context): String = context.getString(R.string.msg_type_temp)
    fun deviceAlarm(context: Context): String = context.getString(R.string.msg_type_device)
}
