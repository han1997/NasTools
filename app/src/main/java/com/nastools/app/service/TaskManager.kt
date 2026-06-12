package com.nastools.app.service

import com.nastools.app.data.database.dao.TaskDao
import com.nastools.app.data.database.entity.TaskEntity
import com.nastools.app.domain.model.TaskResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskManager @Inject constructor(
    private val taskDao: TaskDao
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val semaphore = Semaphore(3)
    private val runningJobs = mutableMapOf<String, Job>()

    fun start() {
        scope.launch {
            taskDao.reviveInterrupted()
            taskDao.observeByStatus(listOf("waiting")).collect { tasks ->
                tasks.forEach { task ->
                    if (!runningJobs.containsKey(task.id)) {
                        launchTask(task)
                    }
                }
            }
        }
    }

    private fun launchTask(task: TaskEntity) {
        val job = scope.launch {
            semaphore.acquire()
            try {
                taskDao.updateStatus(task.id, "running")
                // TODO: Execute task with executor
                delay(1000)
                taskDao.updateStatus(task.id, "completed")
            } catch (e: Exception) {
                taskDao.updateStatusWithError(task.id, "failed", e.message, task.retryCount + 1)
            } finally {
                semaphore.release()
                runningJobs.remove(task.id)
            }
        }
        runningJobs[task.id] = job
    }

    suspend fun pause(id: String) {
        taskDao.updateStatus(id, "paused")
        runningJobs[id]?.cancel()
    }

    suspend fun resume(id: String) {
        taskDao.updateStatus(id, "waiting")
    }

    suspend fun cancel(id: String) {
        taskDao.updateStatus(id, "cancelled")
        runningJobs[id]?.cancel()
    }

    suspend fun retry(id: String) {
        taskDao.updateStatusWithError(id, "waiting", null, 0)
    }

    fun stop() {
        scope.cancel()
    }
}
