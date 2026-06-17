package com.nastools.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nastools.app.data.database.AppDatabase
import com.nastools.app.data.database.DatabaseMigrations
import com.nastools.app.data.database.dao.NasConfigDao
import com.nastools.app.data.database.dao.TaskDao
import com.nastools.app.data.database.dao.UploadPresetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val databaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        // Move prepareDatabaseFiles to background to avoid blocking startup
        // Room database creation itself is lazy (only created on first access)
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(DatabaseMigrations.migration1To2)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // Prepare database files in background to avoid blocking database access
                    databaseScope.launch {
                        DatabaseMigrations.prepareDatabaseFiles(context)
                    }
                }
            })
            .build()
    }

    @Provides
    fun provideNasConfigDao(database: AppDatabase): NasConfigDao {
        return database.nasConfigDao()
    }

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    fun provideUploadPresetDao(database: AppDatabase): UploadPresetDao {
        return database.uploadPresetDao()
    }
}
