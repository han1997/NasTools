package com.nastools.app.data.database

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

object DatabaseMigrations {
    private const val TAG = "DatabaseMigrations"
    private const val LEGACY_DATABASE_NAME = "nastools.db"

    val migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS logs")
        }
    }

    fun prepareDatabaseFiles(context: Context) {
        runCatching {
            val target = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            val legacy = context.getDatabasePath(LEGACY_DATABASE_NAME)
            if (!legacy.exists() || target.absolutePath == legacy.absolutePath) return
            if (hasUserData(target) || !hasUserData(legacy)) return

            checkpoint(legacy)
            context.deleteDatabase(AppDatabase.DATABASE_NAME)
            copyDatabaseFiles(source = legacy, target = target)
        }.onFailure { error ->
            Log.w(TAG, "Unable to prepare local database files", error)
        }
    }

    private fun hasUserData(databaseFile: File): Boolean {
        if (!databaseFile.exists()) return false

        val db = runCatching {
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )
        }.getOrNull() ?: return false

        return try {
            listOf("nas_configs", "upload_presets", "tasks").any { table ->
                tableExists(db, table) && countRows(db, table) > 0L
            }
        } finally {
            db.close()
        }
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean {
        return DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ) > 0L
    }

    private fun countRows(db: SQLiteDatabase, table: String): Long {
        return DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM $table", null)
    }

    private fun checkpoint(databaseFile: File) {
        val db = runCatching {
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )
        }.getOrNull() ?: return

        try {
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close()
        } finally {
            db.close()
        }
    }

    private fun copyDatabaseFiles(source: File, target: File) {
        target.parentFile?.mkdirs()
        listOf("", "-wal", "-shm").forEach { suffix ->
            val sourceFile = File(source.absolutePath + suffix)
            val targetFile = File(target.absolutePath + suffix)
            if (sourceFile.exists()) {
                sourceFile.copyTo(targetFile, overwrite = true)
            }
        }
    }
}
