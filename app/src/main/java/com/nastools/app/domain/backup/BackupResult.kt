package com.nastools.app.domain.backup

/**
 * 恢复结果的用户可核对汇总(R2/D7):
 * 「新增 N 条连接、M 条预设;跳过 K 项(逐条原因)」。
 */
data class BackupResult(
    val addedConnections: Int,
    val addedPresets: Int,
    val skipped: List<SkippedItem>
)