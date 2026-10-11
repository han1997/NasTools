package com.nastools.app.domain.backup

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份编解码与版本校验(design.md §9.1)。
 */
class PresetBackupCodecTest {

    private fun sampleBackup() = BackupFile(
        generatedAt = 1_760_000_000_000L,
        app = BackupAppInfo(versionName = "0.2.6"),
        configs = listOf(
            BackupConfig(
                name = "家庭 NAS",
                type = "webdav",
                baseUrl = "https://nas.home.example/dav",
                username = "han",
                password = "s3cret",
                trustSelfSigned = false,
                defaultRemotePath = "/照片",
                extraJson = null
            )
        ),
        presets = listOf(
            BackupPreset(
                configName = "家庭 NAS",
                name = "手机照片备份",
                localUri = "content://media/external/images/1",
                localLabel = "相机 · 12 个文件",
                remoteRoot = "/照片/相机胶卷",
                optionsJson = "{\"sourceType\":\"folder\"}"
            )
        )
    )

    @Test
    fun encodeDecode_roundTrip() {
        val original = sampleBackup()
        val json = PresetBackupCodec.encode(original)

        val result = PresetBackupCodec.decode(json)
        assertTrue(result is BackupDecodeResult.Success)
        val decoded = (result as BackupDecodeResult.Success).backup

        assertEquals(PresetBackupCodec.SUPPORTED_VERSION, decoded.formatVersion)
        assertEquals(original.generatedAt, decoded.generatedAt)
        assertEquals(original.app, decoded.app)
        assertEquals(original.configs, decoded.configs)
        assertEquals(original.presets, decoded.presets)
    }

    @Test
    fun decode_rejectsHigherFormatVersion() {
        val json = """
            {"formatVersion":2,"generatedAt":1,"configs":[],"presets":[]}
        """.trimIndent()

        val result = PresetBackupCodec.decode(json)
        assertTrue(result is BackupDecodeResult.UnsupportedVersion)
        result as BackupDecodeResult.UnsupportedVersion
        assertEquals(2, result.version)
        assertEquals(PresetBackupCodec.SUPPORTED_VERSION, result.supported)
    }

    @Test
    fun decode_rejectsMissingVersion() {
        val json = """{"generatedAt":1,"configs":[],"presets":[]}"""
        val result = PresetBackupCodec.decode(json)
        assertTrue(result is BackupDecodeResult.Malformed)
    }

    @Test
    fun decode_rejectsBlankAndBrokenJson() {
        assertTrue(PresetBackupCodec.decode(null) is BackupDecodeResult.Malformed)
        assertTrue(PresetBackupCodec.decode("") is BackupDecodeResult.Malformed)
        assertTrue(PresetBackupCodec.decode("not json") is BackupDecodeResult.Malformed)
        assertTrue(PresetBackupCodec.decode("[1,2,3]") is BackupDecodeResult.Malformed)
    }

    @Test
    fun decode_toleratesMissingFieldsWithoutCrash() {
        val json = """{"formatVersion":1}"""
        val result = PresetBackupCodec.decode(json)
        assertTrue(result is BackupDecodeResult.Success)
        val decoded = (result as BackupDecodeResult.Success).backup
        assertTrue(decoded.configs.isEmpty())
        assertTrue(decoded.presets.isEmpty())
        assertEquals(null, decoded.app)
    }

    @Test
    fun decode_toleratesBlankValues() {
        val json = """
            {"formatVersion":1,"generatedAt":0,
             "configs":[{"name":"","type":"","baseUrl":"","username":"","password":""}],
             "presets":[{"configName":"","name":"","localUri":"","localLabel":"",
                         "remoteRoot":"","optionsJson":""}]}
        """.trimIndent()

        val result = PresetBackupCodec.decode(json)
        assertTrue(result is BackupDecodeResult.Success)
    }

    /**
     * AC4/R5:备份文件的键集合必须是白名单 —— 不得写入除连接与预设以外的任何数据,
     * 也不得泄漏本地 id / 时间语义字段。
     */
    @Test
    fun encode_outputKeysAreWhitelisted() {
        val json = PresetBackupCodec.encode(sampleBackup())
        val root = JsonParser.parseString(json).asJsonObject

        assertEquals(
            setOf("formatVersion", "generatedAt", "app", "configs", "presets"),
            root.keySet()
        )

        val config = root.getAsJsonArray("configs")[0].asJsonObject
        assertEquals(
            setOf(
                "name", "type", "baseUrl", "username", "password",
                "trustSelfSigned", "defaultRemotePath", "extraJson"
            ),
            config.keySet()
        )
        // 本地 id 绝不写入
        assertTrue(!config.has("id"))

        val preset = root.getAsJsonArray("presets")[0].asJsonObject
        assertEquals(
            setOf(
                "configName", "name", "localUri", "localLabel",
                "remoteRoot", "optionsJson"
            ),
            preset.keySet()
        )
        assertTrue(!preset.has("id"))
        assertTrue(!preset.has("nasConfigId"))
        assertTrue(!preset.has("createdAt"))
        assertTrue(!preset.has("updatedAt"))
        assertTrue(!preset.has("lastRunAt"))
    }

    @Test
    fun encode_includesPasswordAndConfigDefinition() {
        // AC4/D1:备份确实包含连接定义与明文密码。
        val json = PresetBackupCodec.encode(sampleBackup())
        val config = JsonParser.parseString(json)
            .asJsonObject.getAsJsonArray("configs")[0].asJsonObject

        assertEquals("家庭 NAS", config.get("name").asString)
        assertEquals("https://nas.home.example/dav", config.get("baseUrl").asString)
        assertEquals("han", config.get("username").asString)
        assertEquals("s3cret", config.get("password").asString)
    }
}