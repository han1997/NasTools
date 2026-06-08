package com.nastools.app.battery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 电池优化白名单引导。
 *
 * 注意：直接申请 [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS] 需要
 * 在 manifest 声明 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限。Google Play 上架的
 * 应用谨慎使用 —— 私有部署可以放心。
 */
object BatteryOptimizationHelper {

    fun isIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnore(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
