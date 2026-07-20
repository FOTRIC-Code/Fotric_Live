package com.irtek.live.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val ip: String,
    val port: Int = 80,
    val userName: String = "admin",
    val password: String = "",
    val model: String = "",
    val serialNo: String = "",
    val firmwareVersion: String = "",
    val status: Int = STATUS_OFFLINE,
    val channelCount: Int = 1,
    val thumbnailPath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_OFFLINE = 0
        const val STATUS_ONLINE = 1
        const val STATUS_ALARM = 2
    }
}
