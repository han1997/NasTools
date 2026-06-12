package com.nastools.app.data.database.dao

import androidx.room.*
import com.nastools.app.data.database.entity.UploadPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadPresetDao {
    @Query("SELECT * FROM upload_presets ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<UploadPresetEntity>>

    @Query("SELECT * FROM upload_presets WHERE nasConfigId = :nasConfigId ORDER BY updatedAt DESC")
    fun observeByConfig(nasConfigId: String): Flow<List<UploadPresetEntity>>

    @Query("SELECT * FROM upload_presets WHERE id = :id")
    suspend fun getById(id: String): UploadPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: UploadPresetEntity)

    @Update
    suspend fun update(preset: UploadPresetEntity)

    @Query("UPDATE upload_presets SET lastRunAt = :timestamp, updatedAt = :timestamp WHERE id = :id")
    suspend fun touchLastRun(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM upload_presets WHERE id = :id")
    suspend fun deleteById(id: String)
}
