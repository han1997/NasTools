package com.nastools.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.nastools.app.MainActivity

/**
 * 单纯的"持久存活"前台服务。
 *
 * 它本身不跑上传 —— 上传逻辑全在 Flutter 端的 Dart isolate 中。
 * 这个 service 只保证：进程在锁屏 / 后台时不被系统回收。
 *
 * 通知文案由 Flutter 通过 MethodChannel 推送过来更新（updateNotification）。
 *
 * 设计权衡：完整的 Dart background isolate 方案（通过 flutter_background_service
 * 包）更标准，但需要额外的 plugin 依赖。这里采用最小化方案 —— 单一 FGS 持久化，
 * 配 MethodChannel 控制通知；后续若需要把 TaskManager 真正搬到 service isolate，
 * 在此基础上扩展即可。
 */
class NasForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: defaultTitle()
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
                val progress = intent.getIntExtra(EXTRA_PROGRESS, -1)
                val max = intent.getIntExtra(EXTRA_MAX, 0)
                startForegroundWithNotification(buildNotification(title, message, progress, max))
            }
            else -> {
                startForegroundWithNotification(buildNotification(defaultTitle(), "", -1, 0))
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification(notif: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NasTools:upload")
        wakeLock?.setReferenceCounted(false)
        wakeLock?.acquire(/* ten minutes ceiling */ 10 * 60 * 1000L)
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "传输任务",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "上传 / 下载进度"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(title: String, msg: String, progress: Int, max: Int): Notification {
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            flag
        )

        val builder = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(msg)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)

        if (progress >= 0 && max > 0) {
            builder.setProgress(max, progress, false)
        } else if (progress < 0) {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun defaultTitle(): String = "NasTools 后台运行中"

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "nastools.transfer"

        const val ACTION_START = "com.nastools.app.action.START"
        const val ACTION_STOP = "com.nastools.app.action.STOP"
        const val ACTION_UPDATE = "com.nastools.app.action.UPDATE"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_MAX = "max"

        fun start(context: Context) {
            val intent = Intent(context, NasForegroundService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NasForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun update(
            context: Context,
            title: String,
            message: String,
            progress: Int,
            max: Int
        ) {
            val intent = Intent(context, NasForegroundService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_PROGRESS, progress)
                .putExtra(EXTRA_MAX, max)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
