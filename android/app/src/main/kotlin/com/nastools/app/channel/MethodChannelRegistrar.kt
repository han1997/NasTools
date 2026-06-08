package com.nastools.app.channel

import com.nastools.app.MainActivity
import com.nastools.app.battery.BatteryOptimizationHelper
import com.nastools.app.saf.SafHelper
import com.nastools.app.service.NasForegroundService
import com.nastools.app.viewer.ViewerHelper
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * MethodChannel 路由表。
 *
 * 三个 channel：
 * - `nastools/service` —— 前台服务控制 / 权限申请
 * - `nastools/saf`     —— SAF 文件操作
 * - `nastools/viewer`  —— ACTION_VIEW 调用外部应用打开本地文件
 */
object MethodChannelRegistrar {

    private const val CH_SERVICE = "nastools/service"
    private const val CH_SAF = "nastools/saf"
    private const val CH_VIEWER = "nastools/viewer"

    fun registerWith(activity: MainActivity, engine: FlutterEngine) {
        val ctx = activity.applicationContext

        MethodChannel(engine.dartExecutor.binaryMessenger, CH_SERVICE).setMethodCallHandler { call, result ->
            when (call.method) {
                "startService" -> {
                    NasForegroundService.start(ctx)
                    result.success(true)
                }
                "stopService" -> {
                    NasForegroundService.stop(ctx)
                    result.success(true)
                }
                "updateNotification" -> {
                    val title = call.argument<String>("title") ?: "NasTools"
                    val msg = call.argument<String>("message") ?: ""
                    val progress = call.argument<Int>("progress") ?: -1
                    val max = call.argument<Int>("max") ?: 0
                    NasForegroundService.update(ctx, title, msg, progress, max)
                    result.success(true)
                }
                "requestNotificationPermission" -> {
                    // 异步申请 —— MainActivity 在系统对话框 resolve 时调用 result.success
                    activity.requestNotificationPermission(result)
                }
                "requestIgnoreBatteryOptimizations" -> {
                    BatteryOptimizationHelper.requestIgnore(activity)
                    result.success(true)
                }
                "isBatteryOptimizationIgnored" -> {
                    result.success(BatteryOptimizationHelper.isIgnored(ctx))
                }
                else -> result.notImplemented()
            }
        }

        MethodChannel(engine.dartExecutor.binaryMessenger, CH_SAF).setMethodCallHandler { call, result ->
            try {
                handleSaf(activity, call, result)
            } catch (e: Exception) {
                result.error("SAF_ERROR", e.message, null)
            }
        }

        MethodChannel(engine.dartExecutor.binaryMessenger, CH_VIEWER).setMethodCallHandler { call, result ->
            when (call.method) {
                "openExternal" -> {
                    val path = call.argument<String>("path") ?: ""
                    val mime = call.argument<String>("mime") ?: ""
                    result.success(ViewerHelper.openExternal(ctx, path, mime))
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun handleSaf(activity: MainActivity, call: MethodCall, result: MethodChannel.Result) {
        val ctx = activity.applicationContext
        when (call.method) {
            "listChildren" -> {
                val uri = call.argument<String>("uri") ?: ""
                result.success(SafHelper.listChildren(ctx, uri))
            }
            "stat" -> {
                val uri = call.argument<String>("uri") ?: ""
                result.success(SafHelper.stat(ctx, uri))
            }
            "read" -> {
                val uri = call.argument<String>("uri") ?: ""
                val offset = (call.argument<Number>("offset") ?: 0).toLong()
                val length = (call.argument<Number>("length") ?: 0).toInt()
                result.success(SafHelper.read(ctx, uri, offset, length))
            }
            "persistPermission" -> {
                val uri = call.argument<String>("uri") ?: ""
                SafHelper.persistPermission(ctx, uri)
                result.success(true)
            }
            "delete" -> {
                val uri = call.argument<String>("uri") ?: ""
                result.success(SafHelper.delete(ctx, uri))
            }
            "createFile" -> {
                val parent = call.argument<String>("parentUri") ?: ""
                val name = call.argument<String>("displayName") ?: ""
                val mime = call.argument<String>("mime") ?: "application/octet-stream"
                result.success(SafHelper.createFile(ctx, parent, name, mime))
            }
            "writeBytes" -> {
                val uri = call.argument<String>("uri") ?: ""
                val bytes = call.argument<ByteArray>("bytes") ?: ByteArray(0)
                result.success(SafHelper.writeBytes(ctx, uri, bytes))
            }
            else -> result.notImplemented()
        }
    }
}
