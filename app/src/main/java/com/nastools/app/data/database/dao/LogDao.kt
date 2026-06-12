package com.nastools.app.data.database.dao

import androidx.room.*
import com.nastools.app.data.database.entity.LogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<LogEntity>>

    @Query("SELECT * FROM logs WHERE taskId = :taskId ORDER BY timestamp DESC")
    fun observeByTask(taskId: String): Flow<List<LogEntity>>

    @Insert
    suspend fun insert(log: LogEntity)

    @Query("DELETE FROM logs WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long): Int

    @Query("DELETE FROM logs")
    suspend fun deleteAll()
}
