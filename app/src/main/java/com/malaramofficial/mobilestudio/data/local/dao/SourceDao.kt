package com.malaramofficial.mobilestudio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.malaramofficial.mobilestudio.data.local.entity.SourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources WHERE sceneId = :sceneId ORDER BY zIndex ASC")
    fun getSourcesForScene(sceneId: String): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE sceneId = :sceneId ORDER BY zIndex ASC")
    suspend fun getSourcesForSceneDirect(sceneId: String): List<SourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<SourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteSourceById(id: String)

    @Query("DELETE FROM sources WHERE sceneId = :sceneId")
    suspend fun deleteSourcesForScene(sceneId: String)
}
