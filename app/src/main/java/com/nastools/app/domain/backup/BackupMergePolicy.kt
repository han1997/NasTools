package com.nastools.app.domain.backup

import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.data.database.entity.UploadPresetEntity
import java.util.UUID

/** 恢复结果中逐条列明的跳过项(reason 供 UI 展示,label 指明对象) */
data class SkippedItem(
    val reason: String,
    val label: String
)

/**
 * 合并决策输出:统计 + 需要写库的新实体。
 * 幂等:把 [newConfigs] / [newPresets] 写库后再跑一次,结果全部落入 skipped。
 */
data class BackupMergeResult(
    val addedConnections: Int,
    val addedPresets: Int,
    val newConfigs: List<NasConfigEntity>,
    val newPresets: List<UploadPresetEntity>,
    val skipped: List<SkippedItem>
)

/**
 * 恢复合并策略(D2 / D7)。纯函数,不依赖 Android,可直接单测。
 *
 * 规则:
 * - 连接:按名称匹配本机已有连接;同名**只复用本地 id、不覆盖本机字段**;备份内重名后者跳过。
 * - 预设:先按 `configName` 反查本地连接 id;找不到连接则跳过并说明;
 *   同一连接下同名预设已存在(含本次已导入)则跳过。
 */
object BackupMergePolicy {

    fun merge(
        backupConfigs: List<BackupConfig>,
        backupPresets: List<BackupPreset>,
        localConfigs: List<NasConfigEntity>,
        localPresets: List<UploadPresetEntity>,
        now: Long = System.currentTimeMillis()
    ): BackupMergeResult {
        // 名称 → 本地连接 id(复用);同名连接不覆盖本机字段(D7)
        val configIdByName = linkedMapOf<String, String>()
        localConfigs.forEach { existing ->
            configIdByName.putIfAbsent(existing.name, existing.id)
        }

        val newConfigs = mutableListOf<NasConfigEntity>()
        val skipped = mutableListOf<SkippedItem>()

        backupConfigs.forEach { backupConfig ->
            val name = backupConfig.name
            if (name.isBlank()) {
                skipped.add(SkippedItem(reason = "连接名称为空", label = "连接"))
                return@forEach
            }
            if (configIdByName.containsKey(name)) {
                // 同名连接复用本机 id,不写入备份里的字段(不冲掉本机正确的地址/密码)
                skipped.add(SkippedItem(reason = "连接已存在,复用本机配置", label = "连接「$name」"))
                return@forEach
            }
            val created = NasConfigEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                type = backupConfig.type.ifBlank { "webdav" },
                baseUrl = backupConfig.baseUrl,
                username = backupConfig.username,
                password = backupConfig.password,
                trustSelfSigned = backupConfig.trustSelfSigned,
                defaultRemotePath = backupConfig.defaultRemotePath,
                extraJson = backupConfig.extraJson,
                createdAt = now
            )
            configIdByName[name] = created.id
            newConfigs.add(created)
        }

        // (configId, presetName) → 已存在,含本机已有与本次已导入
        val presetKeys = mutableSetOf<Pair<String, String>>()
        localPresets.forEach { presetKeys.add(it.nasConfigId to it.name) }

        val newPresets = mutableListOf<UploadPresetEntity>()

        backupPresets.forEach { backupPreset ->
            val presetName = backupPreset.name
            val configId = configIdByName[backupPreset.configName]
            if (configId == null) {
                skipped.add(
                    SkippedItem(
                        reason = "找不到连接「${backupPreset.configName}」",
                        label = "预设「$presetName」"
                    )
                )
                return@forEach
            }
            val key = configId to presetName
            if (key in presetKeys) {
                skipped.add(SkippedItem(reason = "预设已存在,未覆盖", label = "预设「$presetName」"))
                return@forEach
            }
            presetKeys.add(key)
            newPresets.add(
                UploadPresetEntity(
                    id = UUID.randomUUID().toString(),
                    nasConfigId = configId,
                    name = presetName,
                    // D3:保留备份中的旧 localUri,不做预检、不做标记
                    localUri = backupPreset.localUri,
                    localLabel = backupPreset.localLabel,
                    remoteRoot = backupPreset.remoteRoot,
                    optionsJson = backupPreset.optionsJson,
                    createdAt = now,
                    updatedAt = now,
                    lastRunAt = null
                )
            )
        }

        return BackupMergeResult(
            addedConnections = newConfigs.size,
            addedPresets = newPresets.size,
            newConfigs = newConfigs,
            newPresets = newPresets,
            skipped = skipped
        )
    }
}