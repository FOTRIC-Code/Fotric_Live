package com.irtek.live.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
