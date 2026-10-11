package com.nastools.app.domain.backup

import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.data.database.entity.UploadPresetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 幂等合并策略(D2 / D7,design.md §4 与 §9.2)。
 */
class BackupMergePolicyTest {

    private fun backupConfig(name: String = "家庭 NAS") = BackupConfig(
        name = name,
        type = "webdav",
        baseUrl = "https://nas.home.example/dav",
        username = "han",
        password = "pw",
        trustSelfSigned = false,
        defaultRemotePath = "/照片",
        extraJson = null
    )

    private fun backupPreset(configName: String = "家庭 NAS", name: String = "手机照片备份") =
        BackupPreset(
            configName = configName,
            name = name,
            localUri = "content://old-device/1",
            localLabel = "相机",
            remoteRoot = "/照片/相机胶卷",
            optionsJson = "{}"
        )

    private fun localConfig(id: String, name: String) = NasConfigEntity(
        id = id,
        name = name,
        type = "webdav",
        baseUrl = "https://local.example/dav",
        username = "local-user",
        password = "local-pw",
        defaultRemotePath = "/local"
    )

    private fun localPreset(id: String, configId: String, name: String) = UploadPresetEntity(
        id = id,
        nasConfigId = configId,
        name = name,
        localUri = "content://local/1",
        localLabel = "本地",
        remoteRoot = "/local",
        optionsJson = "{}"
    )

    @Test
    fun emptyLocal_addsEverything() {
        val result = BackupMergePolicy.merge(
            backupConfigs = listOf(backupConfig()),
            backupPresets = listOf(backupPreset()),
            localConfigs = emptyList(),
            localPresets = emptyList()
        )

        assertEquals(1, result.addedConnections)
        assertEquals(1, result.addedPresets)
        assertEquals(1, result.newConfigs.size)
        assertEquals(1, result.newPresets.size)
        assertTrue(result.skipped.isEmpty())
        // 预设指向本次新建的连接
        assertEquals(result.newConfigs[0].id, result.newPresets[0].nasConfigId)
        // D3:保留备份中的旧 localUri
        assertEquals("content://old-device/1", result.newPresets[0].localUri)
    }

    @Test
    fun sameNameConfig_reusesLocalIdWithoutOverwritingFields() {
        val local = localConfig("local-1", "家庭 NAS")
        val result = BackupMergePolicy.merge(
            backupConfigs = listOf(backupConfig()),
            backupPresets = emptyList(),
            localConfigs = listOf(local),
            localPresets = emptyList()
        )

        assertEquals(0, result.addedConnections)
        assertTrue(result.newConfigs.isEmpty())
        assertEquals(1, result.skipped.size)
        assertTrue(result.skipped[0].reason.contains("复用本机配置"))
    }

    @Test
    fun presetUnderExistingConfig_usesLocalConfigId() {
        val local = localConfig("local-1", "家庭 NAS")
        val result = BackupMergePolicy.merge(
            backupConfigs = listOf(backupConfig()),
            backupPresets = listOf(backupPreset()),
            localConfigs = listOf(local),
            localPresets = emptyList()
        )

        assertEquals(0, result.addedConnections)
        assertEquals(1, result.addedPresets)
        // 复用本地连接 id,而非新建
        assertEquals("local-1", result.newPresets[0].nasConfigId)
    }

    @Test
    fun sameNamePresetUnderSameConfig_isSkipped() {
        val local = localConfig("local-1", "家庭 NAS")
        val existingPreset = localPreset("preset-1", "local-1", "手机照片备份")
        val result = BackupMergePolicy.merge(
            backupConfigs = listOf(backupConfig()),
            backupPresets = listOf(backupPreset()),
            localConfigs = listOf(local),
            localPresets = listOf(existingPreset)
        )

        assertEquals(0, result.addedPresets)
        assertTrue(result.newPresets.isEmpty())
        assertTrue(result.skipped.any { it.reason.contains("预设已存在") })
    }

    @Test
    fun samePresetNameUnderDifferentConfig_isAdded() {
        val local = localConfig("local-1", "家庭 NAS")
        val existingPreset = localPreset("preset-1", "other-config", "手机照片备份")
        val result = BackupMergePolicy.merge(
            backupConfigs = listOf(backupConfig()),
            backupPresets = listOf(backupPreset()),
            localConfigs = listOf(local),
            localPresets = listOf(existingPreset)
        )

        assertEquals(1, result.addedPresets)
        assertEquals("local-1", result.newPresets[0].nasConfigId)
    }

    @Test
    fun presetWithMissingConfig_isSkippedWithReason() {
        val result = BackupMergePolicy.merge(
            backupConfigs = emptyList(),
            backupPresets = listOf(backupPreset(configName = "旅行 NAS", name = "周末相册")),
            localConfigs = emptyList(),
            localPresets = emptyList()
        )

        assertEquals(0, result.addedPresets)
        assertEquals(1, result.skipped.size)
        assertTrue(result.skipped[0].reason.contains("找不到连接"))
        assertTrue(result.skipped[0].label.contains("周末相册"))
    }

    @Test
    fun duplicateConfigNamesInBackup_laterIsSkipped() {
        val result = BackupMergePolicy.merge(
            backupConfigs = listOf(backupConfig("家庭 NAS"), backupConfig("家庭 NAS")),
            backupPresets = emptyList(),
            localConfigs = emptyList(),
            localPresets = emptyList()
        )

        assertEquals(1, result.addedConnections)
        assertEquals(1, result.skipped.size)
    }

    @Test
    fun duplicatePresetNamesInBackup_laterIsSkipped() {
        val result = BackupMergePolicy.merge(
            backupConfigs = listOf(backupConfig()),
            backupPresets = listOf(backupPreset(), backupPreset()),
            localConfigs = emptyList(),
            localPresets = emptyList()
        )

        assertEquals(1, result.addedPresets)
        assertEquals(1, result.skipped.size)
    }

    /**
     * AC2:整体可重复执行 —— 连跑两次结果一致。
     * 用第一次的结果「写库」后作为本机现状再合并一次,应全部落入 skipped。
     */
    @Test
    fun merge_isIdempotentAcrossTwoRuns() {
        val configs = listOf(backupConfig())
        val presets = listOf(backupPreset())

        val first = BackupMergePolicy.merge(
            backupConfigs = configs,
            backupPresets = presets,
            localConfigs = emptyList(),
            localPresets = emptyList()
        )

        val second = BackupMergePolicy.merge(
            backupConfigs = configs,
            backupPresets = presets,
            localConfigs = first.newConfigs,
            localPresets = first.newPresets
        )

        assertEquals(0, second.addedConnections)
        assertEquals(0, second.addedPresets)
        assertTrue(second.newConfigs.isEmpty())
        assertTrue(second.newPresets.isEmpty())
        // 连接与预设各一条跳过
        assertEquals(2, second.skipped.size)
    }
}