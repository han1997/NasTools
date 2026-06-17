package com.nastools.app.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    // ImageLoader is lazily initialized - only created when first accessed
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    // Limit memory cache to 15% of RAM to avoid OOM
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    // Limit disk cache to 50MB
                    .maxSizeBytes(50 * 1024 * 1024)
                    .build()
            }
            // Enable crossfade for smoother image loading
            .crossfade(true)
            // Respect cache headers from network
            .respectCacheHeaders(false)
            .build()
    }
}
