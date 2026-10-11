package com.nastools.app.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 远端路径归一化(D6,design.md §3 与 §9.3)。
 */
class BackupRemotePathTest {

    @Test
    fun nullPath_fallsBackToRoot() {
        assertEquals("/NasTools", BackupRemotePath.directory(null))
        assertEquals("/NasTools/backup.json", BackupRemotePath.file(null))
    }

    @Test
    fun blankPath_fallsBackToRoot() {
        assertEquals("/NasTools", BackupRemotePath.directory(""))
        assertEquals("/NasTools/backup.json", BackupRemotePath.file("   "))
    }

    @Test
    fun pathWithoutLeadingSlash_isNormalized() {
        assertEquals("/照片/NasTools", BackupRemotePath.directory("照片"))
        assertEquals("/照片/NasTools/backup.json", BackupRemotePath.file("照片"))
    }

    @Test
    fun pathWithTrailingSlashAndDuplicates_isNormalized() {
        assertEquals("/共享文件/NasTools", BackupRemotePath.directory("/共享文件/"))
        assertEquals("/共享文件/NasTools", BackupRemotePath.directory("//共享文件//"))
        assertEquals(
            "/共享文件/NasTools/backup.json",
            BackupRemotePath.file("/共享文件///")
        )
    }
}