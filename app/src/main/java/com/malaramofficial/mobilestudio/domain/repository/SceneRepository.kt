package com.malaramofficial.mobilestudio.domain.repository

import com.malaramofficial.mobilestudio.core.model.AppResult
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for persistent storage and retrieval of studio scenes and sources.
 */
interface SceneRepository {
    fun getAllScenes(): Flow<List<Scene>>
    suspend fun getSceneById(id: String): Scene?
    suspend fun saveScene(scene: Scene): AppResult<Unit>
    suspend fun deleteScene(id: String): AppResult<Unit>
    suspend fun reorderScenes(sceneIds: List<String>): AppResult<Unit>
}
