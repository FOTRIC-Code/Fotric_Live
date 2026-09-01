package com.irtek.live.settings

import android.content.Context
import com.irtek.live.R
import com.irtek.live.data.entity.AlarmMessage

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

    /** Rebuild title from structured markers so UI follows current locale. */
    fun displayTitle(context: Context, alarm: AlarmMessage): String {
        val markers = alarm.markerDetails()
        val primary = markers.maxByOrNull { it.level } ?: markers.firstOrNull()
        if (primary != null) {
            val conditionLabel = condition(context, primary.alarmType).ifBlank {
                alarm.type.takeIf { it.isNotBlank() } ?: ""
            }
            val levelLabel = level(context, primary.level)
            val markerNamesLabel = markers.joinToString(" · ") { d ->
                if (d.markerName == "global") context.getString(R.string.common_global)
                else d.markerName
            }
            return buildString {
                append(conditionLabel)
                if (levelLabel.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(levelLabel)
                }
                if (markerNamesLabel.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(markerNamesLabel)
                }
            }.ifBlank {
                alarm.title.ifBlank { tempAlarm(context) }
            }
        }
        return alarm.title.ifBlank { alarm.type.ifBlank { tempAlarm(context) } }
    }

    /** Localized condition / type label for list & detail badges. */
    fun displayType(context: Context, alarm: AlarmMessage): String {
        val markers = alarm.markerDetails()
        val primary = markers.maxByOrNull { it.level } ?: markers.firstOrNull()
        if (primary != null && primary.alarmType > 0) {
            val label = condition(context, primary.alarmType)
            if (label.isNotBlank()) return label
        }
        val type = alarm.type
        return when {
            type.contains("入侵") -> context.getString(R.string.msg_type_intrusion)
            type.contains("温度") || type.contains("temp", ignoreCase = true) -> tempAlarm(context)
            type.contains("设备报警") || type.contains("device", ignoreCase = true) -> deviceAlarm(context)
            type.isNotBlank() -> type
            else -> tempAlarm(context)
        }
    }
}
