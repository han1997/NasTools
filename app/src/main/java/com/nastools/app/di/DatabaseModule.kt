package com.nastools.app.di

import android.content.Context
import androidx.room.Room
import com.nastools.app.data.database.AppDatabase
import com.nastools.app.data.database.dao.NasConfigDao
import com.nastools.app.data.database.dao.TaskDao
import com.nastools.app.data.database.dao.UploadPresetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
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
