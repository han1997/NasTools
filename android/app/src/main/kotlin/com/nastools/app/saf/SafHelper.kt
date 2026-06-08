package com.nastools.app.saf

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * SAF 操作工具。
 *
 * - listChildren(uri)  —— 列出目录直接子项（支持任意层级嵌套）
 * - stat(uri)          —— 查询单个 URI 的元信息
 * - read(uri, offset, length) —— 从 [offset] 读取最多 [length] 字节
 *
 * 实现关键：直接用 [DocumentsContract] 构造子目录 URI，而不是依赖
 * [androidx.documentfile.provider.DocumentFile.listFiles] —— 后者在
 * "把子目录 URI 序列化回 Dart 再传回 Kotlin 列其子项" 这种用法下行为不稳定。
 *
 * tree URI 与 tree-derived document URI 都被识别为合法入参：
 *   tree URI：               `content://...com.android.externalstorage.documents/tree/primary%3AFoo`
 *   document URI from tree： `content://...com.android.externalstorage.documents/tree/primary%3AFoo/document/primary%3AFoo%2Fbar`
 * 两者都通过 `buildChildDocumentsUriUsingTree(parsed, docId)` 拼出
 * 子项 query URI。
 */
object SafHelper {

    private val DOC_PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    fun listChildren(context: Context, uri: String): List<Map<String, Any?>> {
        val parsed = Uri.parse(uri)
        val docId = resolveDocumentId(context, parsed) ?: return emptyList()
        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(parsed, docId)
        } catch (_: Exception) {
            return emptyList()
        }

        val results = mutableListOf<Map<String, Any?>>()
        context.contentResolver.query(childrenUri, DOC_PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val childDocId = c.getString(0) ?: continue
                val name = c.getString(1) ?: ""
                val mime = c.getString(2) ?: ""
                val size = c.getLong(3)
                val mtime = c.getLong(4)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val childUri = DocumentsContract.buildDocumentUriUsingTree(parsed, childDocId)
                results.add(
                    mapOf(
                        "uri" to childUri.toString(),
                        "name" to name,
                        "isDirectory" to isDir,
                        "size" to if (isDir) 0L else size,
                        "mtime" to mtime
                    )
                )
            }
        }
        return results
    }

    fun stat(context: Context, uri: String): Map<String, Any?>? {
        val parsed = Uri.parse(uri)
        val docId = resolveDocumentId(context, parsed) ?: return null
        // stat 的目标 URI ：tree URI 时构造 document URI（指向 root 自身）；否则用原 URI。
        val docUri = if (DocumentsContract.isDocumentUri(context, parsed)) {
            parsed
        } else {
            DocumentsContract.buildDocumentUriUsingTree(parsed, docId)
        }

        context.contentResolver.query(docUri, DOC_PROJECTION, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(1) ?: ""
                val mime = c.getString(2) ?: ""
                val size = c.getLong(3)
                val mtime = c.getLong(4)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                return mapOf(
                    "uri" to docUri.toString(),
                    "name" to name,
                    "isDirectory" to isDir,
                    "size" to if (isDir) 0L else size,
                    "mtime" to mtime
                )
            }
        }
        return null
    }

    fun read(context: Context, uri: String, offset: Long, length: Int): ByteArray {
        val parsed = Uri.parse(uri)
        context.contentResolver.openInputStream(parsed)?.use { input ->
            var skipped = 0L
            while (skipped < offset) {
                val s = input.skip(offset - skipped)
                if (s <= 0) break
                skipped += s
            }
            val out = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(out, read, length - read)
                if (n < 0) break
                read += n
            }
            return if (read == length) out else out.copyOf(read)
        }
        return ByteArray(0)
    }

    /**
     * 把通过 ACTION_OPEN_DOCUMENT_TREE 拿到的 tree URI 持久化授权 ——
     * 否则进程重启后失效。
     */
    fun persistPermission(context: Context, uri: String) {
        val parsed = Uri.parse(uri)
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(parsed, flags)
        } catch (_: SecurityException) {
            // URI 不来自 SAF 选择器；忽略
        }
    }

    /**
     * 删除 [uri] 对应的文档。支持 tree URI（删除根本身）和 tree-derived
     * document URI（删除子项）。
     *
     * 返回 true 表示文档已不存在（成功删除 / 原本就不存在），false 表示删除被
     * provider 拒绝（如权限不足、文档锁定）。失败由调用方决定是否报错。
     *
     * 调用方应已经在 persistPermission 时拿到 FLAG_GRANT_WRITE_URI_PERMISSION，
     * 否则 deleteDocument 必返回 false。
     */
    fun delete(context: Context, uri: String): Boolean {
        val parsed = Uri.parse(uri)
        val docUri = if (DocumentsContract.isDocumentUri(context, parsed)) {
            parsed
        } else {
            val docId = resolveDocumentId(context, parsed) ?: return false
            DocumentsContract.buildDocumentUriUsingTree(parsed, docId) ?: return false
        }
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, docUri)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 在 [parentTreeUri] 指定的目录下创建一个新文档。
     *
     * 返回新建文档的 URI（字符串）；若 provider 拒绝 / 目录不存在 / 同名冲突由
     * provider 自动改名，仍返回最终 URI。失败返回 null，调用方降级处理。
     */
    fun createFile(context: Context, parentTreeUri: String, displayName: String, mime: String): String? {
        val parsed = Uri.parse(parentTreeUri)
        val docId = resolveDocumentId(context, parsed) ?: return null
        val parentDocUri = if (DocumentsContract.isDocumentUri(context, parsed)) {
            parsed
        } else {
            DocumentsContract.buildDocumentUriUsingTree(parsed, docId)
        }
        return try {
            DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                mime.ifEmpty { "application/octet-stream" },
                displayName
            )?.toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 把 [bytes] 整段写入 [uri]（content:// 形式的文档 URI）。truncate 写模式。
     *
     * 仅适用于"用户主动下载到 SAF"的中等大小文件；超大文件应改用流式 channel。
     */
    fun writeBytes(context: Context, uri: String, bytes: ByteArray): Boolean {
        val parsed = Uri.parse(uri)
        return try {
            context.contentResolver.openOutputStream(parsed, "w")?.use { out ->
                out.write(bytes)
                out.flush()
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 拿到 [uri] 对应的 document id：
     *   - tree URI（仅含 `/tree/`）：返回 tree document id
     *   - tree-derived document URI（含 `/document/`）：返回 document id
     */
    private fun resolveDocumentId(context: Context, uri: Uri): String? {
        return try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.getDocumentId(uri)
            } else {
                DocumentsContract.getTreeDocumentId(uri)
            }
        } catch (_: Exception) {
            null
        }
    }
}
