package com.nastools.app.service

import com.nastools.app.data.database.dao.TaskDao
import com.nastools.app.data.database.entity.TaskEntity
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskManager @Inject constructor(
    private val taskDao: TaskDao,
    private val uploadExecutor: UploadExecutor
) {
    private var scope = newScope()
    private val semaphore = Semaphore(3)
    private val runningJobs = ConcurrentHashMap<String, Job>()
    private var collectorJob: Job? = null

    fun start() {
        if (collectorJob?.isActive == true) return
        if (!scope.isActive) scope = newScope()

        collectorJob = scope.launch {
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
            try {
                semaphore.withPermit {
                    executeTask(task)
                }
            } catch (e: CancellationException) {
                val latest = taskDao.getById(task.id)
                if (latest?.status == "running") {
                    taskDao.updateStatus(task.id, "waiting")
                }
                throw e
            } catch (e: Exception) {
                val latest = taskDao.getById(task.id) ?: task
                taskDao.updateStatusWithError(task.id, "failed", e.message, latest.retryCount + 1)
            } finally {
                runningJobs.remove(task.id)
            }
        }
        runningJobs[task.id] = job
    }

    private suspend fun executeTask(task: TaskEntity) {
        val current = taskDao.getById(task.id) ?: return
        if (current.status != "waiting") return

        taskDao.updateStatus(current.id, "running")

        when (current.moduleId) {
            "upload" -> uploadExecutor.execute(current) { uploadedBytes, totalBytes ->
                taskDao.updateProgress(current.id, uploadedBytes, totalBytes)
            }

            else -> error("Unsupported task module: ${current.moduleId}")
        }

        val latest = taskDao.getById(current.id)
        if (latest?.status == "running") {
            taskDao.updateStatus(current.id, "completed")
        }
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
        collectorJob?.cancel()
        collectorJob = null
        runningJobs.values.forEach { it.cancel() }
        runningJobs.clear()
        scope.cancel()
        scope = newScope()
    }

    private fun newScope(): CoroutineScope {
        return CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
}
