package com.nastools.app.presentation.browser

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 文件标签页的会话内状态（用户已确认的连接选择规则）。
 *
 * - 只在进程内存活：不持久化，不构成跨重启的「默认 NAS」偏好。
 * - [selectedConfigId] 为 null 表示本次会话尚未明确选择连接，
 *   文件入口显示连接选择页（即使只有一台也不默选）。
 * - [lastPath] 记录当前连接的浏览目录，用于标签页切换后恢复位置；
 *   切换到不同连接时由 [select] 清空，避免把旧目录带到新 NAS。
 *
 * 网络错误不经过这里：浏览失败保留连接与路径（见 BrowserViewModel），
 * 只有连接配置被删除时才由 FilesScreen 清理选择。
 */
@Singleton
class FileTabSession @Inject constructor() {

    private val _selectedConfigId = MutableStateFlow<String?>(null)
    val selectedConfigId: StateFlow<String?> = _selectedConfigId.asStateFlow()

    private val _lastPath = MutableStateFlow<String?>(null)
    val lastPath: StateFlow<String?> = _lastPath.asStateFlow()

    /** 明确选择一个连接；切换到不同连接时丢弃旧目录位置 */
    fun select(configId: String) {
        if (_selectedConfigId.value != configId) {
            _lastPath.value = null
        }
        _selectedConfigId.value = configId
    }

    /** 当前连接配置被删除时清理，回到连接选择 */
    fun clearSelection() {
        _selectedConfigId.value = null
        _lastPath.value = null
    }

    /** 浏览目录变化时记录，用于会话内恢复 */
    fun updatePath(path: String) {
        _lastPath.value = path
    }
}
