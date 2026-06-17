package com.nastools.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.nastools.app.MainActivity
import com.nastools.app.data.database.dao.TaskDao
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NasForegroundService : LifecycleService() {

    @Inject
    lateinit var taskDao: TaskDao

    @Inject
    lateinit var taskManager: TaskManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var isForegroundStarted = false

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        acquireWakeLock()

        taskManager.start()

        lifecycleScope.launch {
            taskDao.observeActive().collectLatest { tasks ->
                if (tasks.isEmpty()) {
                    if (isForegroundStarted) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        isForegroundStarted = false
                    }
                    stopSelf()
                } else {
                    val task = tasks.first()
                    val progress = if (task.totalBytes > 0) {
                        ((task.progressBytes.toFloat() / task.totalBytes) * 100).toInt()
                    } else {
                        0
                    }
                    val notification = buildNotification(
                        title = task.title,
                        message = "${task.progressBytes / 1024 / 1024}MB / ${task.totalBytes / 1024 / 1024}MB",
                        progress = progress,
                        max = 100
                    )

                    if (!isForegroundStarted) {
                        startForegroundWithNotification(notification)
                    } else {
                        updateNotification(notification)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                if (!isForegroundStarted) {
                    startForegroundWithNotification(
                        buildNotification("NasTools", "准备开始传输...", -1, 0)
                    )
                }
            }

            ACTION_STOP -> {
                taskManager.stop()
                if (isForegroundStarted) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    isForegroundStarted = false
                }
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        taskManager.stop()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
        super.onDestroy()
    }

    private fun startForegroundWithNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForegroundStarted = true
    }

    private fun updateNotification(notification: Notification) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        title: String,
        message: String,
        progress: Int,
        max: Int
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        when {
            progress >= 0 && max > 0 -> builder.setProgress(max, progress, false)
            progress < 0 -> builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "传输任务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "上传和下载进度通知"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NasTools:upload"
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "nastools.transfer"

        const val ACTION_START = "com.nastools.app.action.START"
        const val ACTION_STOP = "com.nastools.app.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, NasForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NasForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
