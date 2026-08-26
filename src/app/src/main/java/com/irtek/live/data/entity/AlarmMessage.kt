package com.irtek.live.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

/** One marker that participated in a thermal alarm event. */
data class AlarmMarkerDetail(
    val markerName: String = "",
    val level: Int = 0,
    val alarmType: Int = 0,
    val temperature: Double = 0.0,
    val threshold: Double = 0.0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("markerName", markerName)
        put("level", level)
        put("alarmType", alarmType)
        put("temperature", temperature)
        put("threshold", threshold)
    }

    companion object {
        fun fromJson(obj: JSONObject): AlarmMarkerDetail = AlarmMarkerDetail(
            markerName = obj.optString("markerName"),
            level = obj.optInt("level", 0),
            alarmType = obj.optInt("alarmType", 0),
            temperature = obj.optDouble("temperature", 0.0),
            threshold = obj.optDouble("threshold", 0.0)
        )
    }
}

@Entity(
    tableName = "alarm_messages",
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["id"],
        childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("deviceId")]
)
data class AlarmMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: Long,
    val type: String = "",
    val title: String = "",
    val content: String = "",
    val temperature: Double = 0.0,
    val threshold: Double = 0.0,
    val markerName: String = "",
    /** JSON array of [AlarmMarkerDetail]; empty for legacy rows. */
    val markersJson: String = "",
    val imagePath: String = "",
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun markerDetails(): List<AlarmMarkerDetail> {
        if (markersJson.isNotBlank()) {
            runCatching {
                val arr = JSONArray(markersJson)
                return buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        add(AlarmMarkerDetail.fromJson(obj))
                    }
                }
            }
        }
        // Legacy single-marker rows.
        if (markerName.isNotBlank() || temperature != 0.0 || threshold != 0.0) {
            return listOf(
                AlarmMarkerDetail(
                    markerName = markerName,
                    temperature = temperature,
                    threshold = threshold
                )
            )
        }
        return emptyList()
    }

    companion object {
        fun markersToJson(markers: List<AlarmMarkerDetail>): String {
            if (markers.isEmpty()) return ""
            val arr = JSONArray()
            markers.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}
