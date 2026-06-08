package com.nastools.app.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 调用外部应用打开本地文件（ACTION_VIEW）。
 *
 * 输入是 app 内的绝对路径（典型场景：缓存到 cache/preview/ 的预览文件）。
 * 通过 FileProvider 产出 content:// URI 并附 FLAG_GRANT_READ_URI_PERMISSION
 * 授权给目标应用。
 */
object ViewerHelper {

    /**
     * @param localPath app 内文件的绝对路径，必须落在 FileProvider 暴露的子树内
     *                  （`@xml/file_paths` 当前仅暴露 `cache/preview/`）
     * @param mime      推断好的 MIME，空串视为 `&#42;/&#42;`
     * @return true = startActivity 成功；false = 无可用应用 / 文件不存在 /
     *               URI 构造失败，由 Flutter 端兜底提示用户
     */
    fun openExternal(context: Context, localPath: String, mime: String): Boolean {
        val file = File(localPath)
        if (!file.exists() || !file.isFile) return false

        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (_: IllegalArgumentException) {
            // 文件不在 file_paths.xml 暴露的子树里
            return false
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mime.ifEmpty { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
