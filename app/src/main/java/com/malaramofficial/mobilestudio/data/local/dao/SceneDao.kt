package com.malaramofficial.mobilestudio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.malaramofficial.mobilestudio.data.local.entity.SceneEntity
import com.malaramofficial.mobilestudio.data.local.entity.SourceEntity
import kotlinx.coroutines.flow.Flow

data class SceneWithSources(
    val scene: SceneEntity,
    val sources: List<SourceEntity>
)

@Dao
interface SceneDao {
    @Query("SELECT * FROM scenes ORDER BY orderIndex ASC")
    fun getAllScenes(): Flow<List<SceneEntity>>

    @Query("SELECT * FROM scenes WHERE id = :id")
    suspend fun getSceneById(id: String): SceneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScene(scene: SceneEntity)

    @Query("DELETE FROM scenes WHERE id = :id")
    suspend fun deleteSceneById(id: String)

    @Query("UPDATE scenes SET orderIndex = :newOrder WHERE id = :id")
    suspend fun updateOrderIndex(id: String, newOrder: Int)

    @Query("SELECT COUNT(*) FROM scenes")
    suspend fun getSceneCount(): Int
}
