package com.nastools.app.data.database.dao

import androidx.room.*
import com.nastools.app.data.database.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE status IN (:statuses) ORDER BY priority DESC, createdAt ASC")
    fun observeByStatus(statuses: List<String>): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status IN ('waiting', 'running', 'paused') ORDER BY priority DESC, createdAt ASC")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: String): Flow<TaskEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET status = :status, errorMessage = :errorMessage, retryCount = :retryCount, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatusWithError(
        id: String,
        status: String,
        errorMessage: String?,
        retryCount: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE tasks SET progressBytes = :progressBytes, totalBytes = :totalBytes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(
        id: String,
        progressBytes: Long,
        totalBytes: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE tasks SET payloadJson = :payloadJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePayload(id: String, payloadJson: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET status = 'waiting' WHERE status = 'running'")
    suspend fun reviveInterrupted(): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}
