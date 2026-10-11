package com.nastools.app.domain.backup

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 备份文件 DTO 与编解码(design.md §2,R6)。
 *
 * 纯 JVM,不依赖 Android/Compose,可直接单测。
 *
 * 白名单原则:
 * - 不写本地 id(`id` / `nasConfigId` 跨设备无意义);
 * - 连接用 `name` 反向引用,预设用 `configName` 指向连接;
 * - 不含 `createdAt/updatedAt/lastRunAt` 等本机时间语义字段;
 * - 除下列字段外不序列化任何其它数据(R5)。
 */

/** 备份文件根结构(formatVersion = 1) */
data class BackupFile(
    val formatVersion: Int = PresetBackupCodec.SUPPORTED_VERSION,
    val generatedAt: Long,
    val app: BackupAppInfo? = null,
    val configs: List<BackupConfig> = emptyList(),
    val presets: List<BackupPreset> = emptyList()
)

data class BackupAppInfo(
    val versionName: String? = null
)

/** 连接定义(含明文密码,用户已确认的取舍 D1) */
data class BackupConfig(
    val name: String,
    val type: String,
    val baseUrl: String,
    val username: String,
    val password: String,
    val trustSelfSigned: Boolean,
    val defaultRemotePath: String?,
    val extraJson: String?
)

/** 上传预设(configName 指向连接名称,不写 nasConfigId) */
data class BackupPreset(
    val configName: String,
    val name: String,
    val localUri: String,
    val localLabel: String,
    val remoteRoot: String,
    val optionsJson: String
)

sealed interface BackupDecodeResult {
    data class Success(val backup: BackupFile) : BackupDecodeResult

    /** 备份由更新(或未知)版本创建,拒绝导入(R6) */
    data class UnsupportedVersion(val version: Int, val supported: Int) : BackupDecodeResult

    /** JSON 结构无法识别 */
    data class Malformed(val reason: String) : BackupDecodeResult
}

object PresetBackupCodec {

    /** 当前支持的备份格式版本;未来加字段时递增,R6 */
    const val SUPPORTED_VERSION = 1

    // serializeNulls:让可空字段显式写为 null,键集合稳定且可核对白名单(AC4)
    private val gson = GsonBuilder().serializeNulls().create()

    fun encode(backup: BackupFile): String = gson.toJson(backup)

    /**
     * 解析备份文件。版本不兼容或结构非法时返回失败,不做「尽力而为」的部分导入(R6)。
     */
    fun decode(json: String?): BackupDecodeResult {
        if (json.isNullOrBlank()) {
            return BackupDecodeResult.Malformed("备份文件为空")
        }

        val root: JsonObject = runCatching {
            JsonParser.parseString(json)
        }.getOrNull() as? JsonObject ?: run {
            return BackupDecodeResult.Malformed("备份文件不是有效的 JSON 对象")
        }

        val versionElement = root.get("formatVersion")
        if (versionElement == null || !versionElement.isJsonPrimitive) {
            return BackupDecodeResult.Malformed("备份文件缺少格式版本号")
        }
        val version = runCatching { versionElement.asInt }.getOrNull()
            ?: return BackupDecodeResult.Malformed("备份文件格式版本号无效")

        if (version > SUPPORTED_VERSION) {
            return BackupDecodeResult.UnsupportedVersion(version, SUPPORTED_VERSION)
        }
        if (version < 1) {
            return BackupDecodeResult.Malformed("备份文件格式版本号无效")
        }

        val parsed = runCatching {
            gson.fromJson(json, BackupFile::class.java)
        }.getOrNull() ?: return BackupDecodeResult.Malformed("备份文件解析失败")

        return BackupDecodeResult.Success(parsed.sanitized())
    }

    /**
     * Gson 反射创建对象时不会执行 Kotlin 默认值,缺失的可空集合/字符串会是 null。
     * 这里统一归一化,避免下游 NPE。
     */
    private fun BackupFile.sanitized() = copy(
        configs = configs.orEmpty().map { it.sanitized() },
        presets = presets.orEmpty().map { it.sanitized() }
    )

    private fun BackupConfig.sanitized() = copy(
        name = name.orEmpty(),
        type = type.orEmpty(),
        baseUrl = baseUrl.orEmpty(),
        username = username.orEmpty(),
        password = password.orEmpty()
    )

    private fun BackupPreset.sanitized() = copy(
        configName = configName.orEmpty(),
        name = name.orEmpty(),
        localUri = localUri.orEmpty(),
        localLabel = localLabel.orEmpty(),
        remoteRoot = remoteRoot.orEmpty(),
        optionsJson = optionsJson.orEmpty()
    )
}