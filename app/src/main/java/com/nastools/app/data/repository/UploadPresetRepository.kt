package com.nastools.app.data.repository

import com.nastools.app.data.database.dao.UploadPresetDao
import com.nastools.app.data.database.entity.UploadPresetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadPresetRepository @Inject constructor(
    private val dao: UploadPresetDao
) {
    fun observeAll(): Flow<List<UploadPresetEntity>> = dao.observeAll()

    fun observeByConfig(nasConfigId: String): Flow<List<UploadPresetEntity>> = dao.observeByConfig(nasConfigId)

    suspend fun getById(id: String): UploadPresetEntity? = dao.getById(id)

    suspend fun upsert(preset: UploadPresetEntity) = dao.insert(preset)

    suspend fun deleteById(id: String) = dao.deleteById(id)

    suspend fun touchLastRun(id: String) = dao.touchLastRun(id)
}
