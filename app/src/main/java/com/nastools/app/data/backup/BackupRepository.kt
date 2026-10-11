package com.nastools.app.data.backup

import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.data.network.StorageAdapterFactory
import com.nastools.app.data.repository.NasConfigRepository
import com.nastools.app.data.repository.UploadPresetRepository
import com.nastools.app.domain.backup.BackupAppInfo
import com.nastools.app.domain.backup.BackupConfig
import com.nastools.app.domain.backup.BackupDecodeResult
import com.nastools.app.domain.backup.BackupFile
import com.nastools.app.domain.backup.BackupMergePolicy
import com.nastools.app.domain.backup.BackupPreset
import com.nastools.app.domain.backup.BackupRemotePath
import com.nastools.app.domain.backup.BackupResult
import com.nastools.app.domain.backup.PresetBackupCodec
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** 备份文件格式版本高于当前支持版本(R6 / AC9) */
class UnsupportedBackupVersionException(
    val version: Int,
    val supported: Int
) : IOException("unsupported backup format version $version")

/**
 * 备份/恢复编排(design.md §1、实施计划阶段 2)。
 *
 * 复用 [StorageAdapterFactory] 与两个既有 Repository,不新造平行数据访问层。
 * 编码与合并策略为纯逻辑([PresetBackupCodec] / [BackupMergePolicy]);
 * 本类只负责读库 → 编码 → 传输 → 写库,并把异常归一化为可读中文文案(AC8/AC9)。
 */
@Singleton
class BackupRepository @Inject constructor(
    private val configRepository: NasConfigRepository,
    private val presetRepository: UploadPresetRepository,
    private val adapterFactory: StorageAdapterFactory
) {

    /**
     * 备份:读库 → 编码 → mkdir(目录) → upload(路径)。
     * @return 成功时返回远端完整路径
     */
    suspend fun backup(configId: String, versionName: String?): Result<String> {
        val config = try {
            resolveConfig(configId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(IOException(e.message ?: "所选连接不存在"))
        }

        return try {
            val adapter = createAdapter(config)

            val configs = configRepository.getAll()
            val presets = presetRepository.getAll()

            val backup = BackupFile(
                formatVersion = PresetBackupCodec.SUPPORTED_VERSION,
                generatedAt = System.currentTimeMillis(),
                app = BackupAppInfo(versionName = versionName),
                configs = configs.map { it.toBackupConfig() },
                presets = presets.mapNotNull { preset ->
                    val presetConfigName = configs.firstOrNull { it.id == preset.nasConfigId }?.name
                        ?: return@mapNotNull null
                    BackupPreset(
                        configName = presetConfigName,
                        name = preset.name,
                        localUri = preset.localUri,
                        localLabel = preset.localLabel,
                        remoteRoot = preset.remoteRoot,
                        optionsJson = preset.optionsJson
                    )
                }
            )

            val json = PresetBackupCodec.encode(backup)
            val directory = BackupRemotePath.directory(config.defaultRemotePath)
            val filePath = BackupRemotePath.file(config.defaultRemotePath)

            // 目录不存在则自动创建(mkdir 逐级容错,已存在忽略)
            adapter.mkdir(directory)
            adapter.upload(filePath, json.toByteArray(Charsets.UTF_8))

            Result.success(filePath)
        } catch (e: CancellationException) {
            // 协程取消必须继续抛出,不得转成普通失败(state-feedback.md)
            throw e
        } catch (e: Exception) {
            Result.failure(IOException("备份失败:${e.readableMessage()}"))
        }
    }

    /**
     * 恢复:stat(路径) → download → decode(校验版本)→ merge → 写库。
     */
    suspend fun restore(configId: String): Result<BackupResult> {
        val config = try {
            resolveConfig(configId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(IOException(e.message ?: "所选连接不存在"))
        }

        return try {
            val adapter = createAdapter(config)
            val filePath = BackupRemotePath.file(config.defaultRemotePath)

            if (adapter.stat(filePath) == null) {
                throw IOException("未找到备份文件:$filePath,请先在该位置执行备份")
            }

            val bytes = adapter.download(filePath)
            val json = bytes.toString(Charsets.UTF_8)

            val decoded = PresetBackupCodec.decode(json)
            when (decoded) {
                is BackupDecodeResult.UnsupportedVersion ->
                    throw UnsupportedBackupVersionException(decoded.version, decoded.supported)

                is BackupDecodeResult.Malformed ->
                    throw IOException("备份文件无法解析:${decoded.reason}")

                is BackupDecodeResult.Success -> {
                    val merge = BackupMergePolicy.merge(
                        backupConfigs = decoded.backup.configs,
                        backupPresets = decoded.backup.presets,
                        localConfigs = configRepository.getAll(),
                        localPresets = presetRepository.getAll()
                    )
                    // 先写连接,再写预设(预设对连接有 FK 依赖)
                    merge.newConfigs.forEach { configRepository.insert(it) }
                    merge.newPresets.forEach { presetRepository.upsert(it) }

                    Result.success(
                        BackupResult(
                            addedConnections = merge.addedConnections,
                            addedPresets = merge.addedPresets,
                            skipped = merge.skipped
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            // 协程取消必须继续抛出,不得转成普通失败(state-feedback.md)
            throw e
        } catch (e: UnsupportedBackupVersionException) {
            Result.failure(
                IOException(
                    "备份由更新版本创建(格式 v${e.version},当前支持 v${e.supported}),无法导入"
                )
            )
        } catch (e: Exception) {
            Result.failure(IOException("恢复失败:${e.readableMessage()}"))
        }
    }

    private suspend fun resolveConfig(configId: String): NasConfigEntity {
        return configRepository.getById(configId)
            ?: throw IOException("所选连接不存在")
    }

    private fun createAdapter(config: NasConfigEntity) = runCatching {
        adapterFactory.create(config)
    }.getOrElse { error ->
        throw IOException("无法创建连接:${error.readableMessage()}")
    }

    private fun NasConfigEntity.toBackupConfig() = BackupConfig(
        name = name,
        type = type,
        baseUrl = baseUrl,
        username = username,
        password = password,
        trustSelfSigned = trustSelfSigned,
        defaultRemotePath = defaultRemotePath,
        extraJson = extraJson
    )
}

/**
 * 把底层异常转成可读文案,避免把原始 HTTP 码或空消息直接抛给用户(AC8)。
 *
 * WebDAV 层抛出的消息形如 `PUT failed: 403`,这里翻译成
 * 「权限不足,服务器拒绝写入」等可理解提示;网络层异常按类型识别。
 */
private val httpFailurePattern = Regex("""^\w+ failed: (\d{3})$""")

internal fun Throwable.readableMessage(): String {
    when (this) {
        is java.net.UnknownHostException -> return "无法解析服务器地址,请检查网络与地址"
        is java.net.SocketTimeoutException -> return "连接服务器超时,请检查网络后重试"
        is java.net.ConnectException -> return "无法连接到服务器,请检查网络与地址"
    }

    val raw = message?.takeIf { it.isNotBlank() } ?: return "网络或服务器错误"
    val code = httpFailurePattern.matchEntire(raw)?.groupValues?.get(1)?.toIntOrNull()
        ?: return raw

    return when (code) {
        401 -> "服务器要求登录,请检查用户名与密码"
        403 -> "权限不足,服务器拒绝访问"
        404 -> "远端目录不存在,请检查连接默认目录"
        405, 409 -> "远端目录或文件状态冲突,请检查目录后重试"
        507 -> "服务器存储空间不足"
        in 500..599 -> "服务器内部错误(HTTP $code)"
        else -> "远端返回 HTTP $code"
    }
}