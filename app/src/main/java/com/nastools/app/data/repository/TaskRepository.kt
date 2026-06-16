package com.nastools.app.data.repository

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

    fun observeById(id: String): Flow<TaskEntity?> = dao.observeById(id)

    suspend fun getById(id: String): TaskEntity? = dao.getById(id)

    suspend fun insert(task: TaskEntity) = dao.insert(task)

    suspend fun deleteById(id: String) = dao.deleteById(id)

    suspend fun deleteByIds(ids: List<String>) = dao.deleteByIds(ids)
}
