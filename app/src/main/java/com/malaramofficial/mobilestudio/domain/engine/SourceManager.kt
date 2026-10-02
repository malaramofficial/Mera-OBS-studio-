package com.malaramofficial.mobilestudio.domain.engine

import com.malaramofficial.mobilestudio.core.model.AppResult
import com.malaramofficial.mobilestudio.domain.model.scene.ChromaKeyConfig
import com.malaramofficial.mobilestudio.domain.model.scene.Crop
import com.malaramofficial.mobilestudio.domain.model.scene.Source
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.scene.Transform
import com.malaramofficial.mobilestudio.domain.model.source.SourceConfig

enum class LayerOrderAction {
    BRING_TO_FRONT,
    SEND_TO_BACK,
    MOVE_UP,
    MOVE_DOWN
}

/**
 * Production Source Engine contract for managing source layers inside scenes.
 */
interface SourceManager {
    suspend fun addSource(
        sceneId: String,
        name: String,
        type: SourceType,
        config: SourceConfig? = null,
        transform: Transform = Transform()
    ): AppResult<Source>

    suspend fun removeSource(sceneId: String, sourceId: String): AppResult<Unit>
    suspend fun duplicateSource(sceneId: String, sourceId: String): AppResult<Source>
    suspend fun renameSource(sceneId: String, sourceId: String, newName: String): AppResult<Source>
    suspend fun reorderSource(sceneId: String, sourceId: String, action: LayerOrderAction): AppResult<Source>

    suspend fun setSourceVisibility(sceneId: String, sourceId: String, isVisible: Boolean): AppResult<Source>
    suspend fun setSourceLock(sceneId: String, sourceId: String, isLocked: Boolean): AppResult<Source>

    suspend fun updateTransform(sceneId: String, sourceId: String, transform: Transform): AppResult<Source>
    suspend fun updateCrop(sceneId: String, sourceId: String, crop: Crop): AppResult<Source>
    suspend fun updateOpacity(sceneId: String, sourceId: String, opacity: Float): AppResult<Source>
    suspend fun updateChromaKey(sceneId: String, sourceId: String, chromaKey: ChromaKeyConfig?): AppResult<Source>
    suspend fun updateConfig(sceneId: String, sourceId: String, config: SourceConfig): AppResult<Source>

    fun getSources(sceneId: String): List<Source>
    fun getSource(sceneId: String, sourceId: String): Source?
    fun getSupportedSourceTypes(): List<SourceType>
}
