package com.nastools.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nastools.app.data.database.dao.*
import com.nastools.app.data.database.entity.*

@Database(
    entities = [
        NasConfigEntity::class,
        TaskEntity::class,
        UploadPresetEntity::class,
        LogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nasConfigDao(): NasConfigDao
    abstract fun taskDao(): TaskDao
    abstract fun uploadPresetDao(): UploadPresetDao
    abstract fun logDao(): LogDao

    companion object {
        const val DATABASE_NAME = "nastools.db"
    }
}
