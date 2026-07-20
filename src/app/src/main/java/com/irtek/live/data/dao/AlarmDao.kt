package com.irtek.live.data.dao

import androidx.room.*
import com.irtek.live.data.entity.AlarmMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarm_messages ORDER BY timestamp DESC")
    fun getAll(): Flow<List<AlarmMessage>>

    @Query("SELECT * FROM alarm_messages WHERE deviceId = :deviceId ORDER BY timestamp DESC")
    fun getByDevice(deviceId: Long): Flow<List<AlarmMessage>>

    @Query("SELECT * FROM alarm_messages WHERE deviceId = :deviceId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentByDevice(deviceId: Long, limit: Int = 3): Flow<List<AlarmMessage>>

    @Query("SELECT * FROM alarm_messages WHERE isRead = 0 ORDER BY timestamp DESC")
    fun getUnread(): Flow<List<AlarmMessage>>

    @Query("SELECT COUNT(*) FROM alarm_messages WHERE isRead = 0")
    fun unreadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM alarm_messages")
    suspend fun count(): Long

    @Insert
    suspend fun insert(alarm: AlarmMessage): Long

    @Query("UPDATE alarm_messages SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE alarm_messages SET isRead = 1 WHERE deviceId = :deviceId")
    suspend fun markAllReadByDevice(deviceId: Long)

    @Query("DELETE FROM alarm_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM alarm_messages WHERE deviceId = :deviceId")
    suspend fun deleteByDevice(deviceId: Long)
}
