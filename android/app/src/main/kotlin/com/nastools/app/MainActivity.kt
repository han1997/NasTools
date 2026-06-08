package com.nastools.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nastools.app.channel.MethodChannelRegistrar
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    /** 当前挂起的"申请通知权限"的回调 —— 系统对话框返回时 resolve。 */
    @Volatile
    private var pendingNotificationResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannelRegistrar.registerWith(this, flutterEngine)
    }

    /**
     * 由 [MethodChannelRegistrar] 调用：申请 POST_NOTIFICATIONS 权限并
     * 通过 [result] 返回最终是否被授予。
     *
     * - 已授予 / 不需要（< API 33）：直接 success(true)
     * - 拒绝：success(false)
     * - 重复并发申请：之前的 result 立即 success(false) 取消，避免泄漏
     */
    fun requestNotificationPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            result.success(true)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            result.success(true)
            return
        }
        // 取消任何已挂起的请求 —— 同一时间只允许一个
        pendingNotificationResult?.success(false)
        pendingNotificationResult = result
        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATION_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            pendingNotificationResult?.success(granted)
            pendingNotificationResult = null
        }
    }

    companion object {
        const val REQUEST_NOTIFICATION_PERMISSION = 1101
    }
}
