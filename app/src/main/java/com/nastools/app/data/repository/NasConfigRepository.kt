package com.nastools.app.data.repository

import com.nastools.app.data.database.dao.NasConfigDao
import com.nastools.app.data.database.entity.NasConfigEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NasConfigRepository @Inject constructor(
    private val dao: NasConfigDao
) {
    fun observeAll(): Flow<List<NasConfigEntity>> = dao.observeAll()

    fun observeById(id: String): Flow<NasConfigEntity?> = dao.observeById(id)

    suspend fun getById(id: String): NasConfigEntity? = dao.getById(id)

    suspend fun insert(config: NasConfigEntity) = dao.insert(config)

    suspend fun update(config: NasConfigEntity) = dao.update(config)

    suspend fun deleteById(id: String) = dao.deleteById(id)
}
