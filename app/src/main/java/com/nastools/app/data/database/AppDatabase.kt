package com.nastools.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nastools.app.data.database.dao.NasConfigDao
import com.nastools.app.data.database.dao.TaskDao
import com.nastools.app.data.database.dao.UploadPresetDao
import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.data.database.entity.TaskEntity
import com.nastools.app.data.database.entity.UploadPresetEntity

@Database(
    entities = [
        NasConfigEntity::class,
        TaskEntity::class,
        UploadPresetEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nasConfigDao(): NasConfigDao
    abstract fun taskDao(): TaskDao
    abstract fun uploadPresetDao(): UploadPresetDao

    companion object {
        const val DATABASE_NAME = "nastools_room.db"
    }
}
