package com.nastools.app.data.database.dao

import androidx.room.*
import com.nastools.app.data.database.entity.NasConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NasConfigDao {
    @Query("SELECT * FROM nas_configs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NasConfigEntity>>

    @Query("SELECT * FROM nas_configs WHERE id = :id")
    suspend fun getById(id: String): NasConfigEntity?

    @Query("SELECT * FROM nas_configs WHERE id = :id")
    fun observeById(id: String): Flow<NasConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: NasConfigEntity)

    @Update
    suspend fun update(config: NasConfigEntity)

    @Query("DELETE FROM nas_configs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM nas_configs")
    suspend fun deleteAll()
}
