package com.nastools.app.domain.backup

import com.nastools.app.util.RemotePath

/**
 * 备份文件的远端位置(D6,R1/R2)。
 *
 * 固定为 `<所选连接的默认远端目录>/NasTools/backup.json`。
 * `defaultRemotePath` 可能为 null / 空 / 无前导斜杠 / 末尾多斜杠,
 * 统一由 [RemotePath.join] 归一化为 `/a/b` 形式(与 BrowserScreen 路径风格一致)。
 */
object BackupRemotePath {

    /** 备份目录名 */
    const val FOLDER = "NasTools"

    /** 备份文件名(固定单份,重复备份覆盖) */
    const val FILE_NAME = "backup.json"

    /** 备份目录:`<默认远端目录>/NasTools` */
    fun directory(defaultRemotePath: String?): String {
        return RemotePath.join(defaultRemotePath, FOLDER)
    }

    /** 备份文件完整路径:`<默认远端目录>/NasTools/backup.json` */
    fun file(defaultRemotePath: String?): String {
        return RemotePath.join(defaultRemotePath, FOLDER, FILE_NAME)
    }
}