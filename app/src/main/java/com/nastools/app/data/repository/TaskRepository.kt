package com.nastools.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.nastools.app.data.database.dao.TaskDao
import com.nastools.app.data.database.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val dao: TaskDao
) {
    fun observeActive(): Flow<List<TaskEntity>> = dao.observeActive()

    fun observeByStatus(statuses: List<String>): Flow<List<TaskEntity>> = dao.observeByStatus(statuses)

    /**
     * Provides paginated task data to avoid loading all tasks into memory at once.
     * Recommended for large task lists (100+ items) to prevent OOM.
     */
    fun pagingByStatus(statuses: List<String>): Flow<PagingData<TaskEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 5
            ),
            pagingSourceFactory = { dao.pagingByStatus(statuses) }
        ).flow
    }

    fun observeById(id: String): Flow<TaskEntity?> = dao.observeById(id)

    suspend fun getById(id: String): TaskEntity? = dao.getById(id)

    suspend fun insert(task: TaskEntity) = dao.insert(task)

    suspend fun deleteById(id: String) = dao.deleteById(id)

    suspend fun deleteByIds(ids: List<String>) = dao.deleteByIds(ids)

    suspend fun updateTitle(taskId: String, title: String) = dao.updateTitle(taskId, title)
}
