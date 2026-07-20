package com.irtek.live.data.dao

import androidx.room.*
import com.irtek.live.data.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Query("SELECT * FROM devices ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE status = :status ORDER BY updatedAt DESC")
    fun getByStatus(status: Int): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun getById(id: Long): DeviceEntity?

    @Query("SELECT * FROM devices WHERE ip = :ip LIMIT 1")
    suspend fun getByIp(ip: String): DeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: DeviceEntity): Long

    @Update
    suspend fun update(device: DeviceEntity)

    @Query("UPDATE devices SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int, now: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(device: DeviceEntity)

    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM devices")
    fun count(): Flow<Int>

    @Query("SELECT COUNT(*) FROM devices WHERE status = :status")
    fun countByStatus(status: Int): Flow<Int>
}
