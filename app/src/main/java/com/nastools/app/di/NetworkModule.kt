package com.nastools.app.di

import com.nastools.app.data.network.WebDavAdapter
import com.nastools.app.data.network.WebDavClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .writeTimeout(600, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    fun provideWebDavClient(client: OkHttpClient): WebDavClient {
        return WebDavClient(client, "")
    }

    @Provides
    fun provideWebDavAdapter(client: WebDavClient): WebDavAdapter {
        return WebDavAdapter(client)
    }
}
