package com.malaramofficial.mobilestudio.engine.source

import com.malaramofficial.mobilestudio.core.model.AppError
import com.malaramofficial.mobilestudio.core.model.AppResult
import com.malaramofficial.mobilestudio.domain.engine.LayerOrderAction
import com.malaramofficial.mobilestudio.domain.engine.SceneManager
import com.malaramofficial.mobilestudio.domain.engine.SourceManager
import com.malaramofficial.mobilestudio.domain.model.scene.ChromaKeyConfig
import com.malaramofficial.mobilestudio.domain.model.scene.Crop
import com.malaramofficial.mobilestudio.domain.model.scene.Source
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.scene.Transform
import com.malaramofficial.mobilestudio.domain.model.source.SourceConfig
import java.util.UUID

/**
 * Production implementation of [SourceManager] managing visual layers and configuration.
 */
class SourceManagerImpl(
    private val sceneManager: SceneManager
) : SourceManager {

    override suspend fun addSource(
        sceneId: String,
        name: String,
        type: SourceType,
        config: SourceConfig?,
        transform: Transform
    ): AppResult<Source> {
        if (name.isBlank()) {
            return AppResult.Error(AppError.Source.InvalidTransform("Source name cannot be blank"))
        }

        val scene = sceneManager.getScene(sceneId)
            ?: return AppResult.Error(AppError.Scene.SceneNotFound(sceneId))

        val effectiveConfig = config ?: when (type) {
            SourceType.TEXT -> SourceConfig.Text(text = name.trim())
            SourceType.CAMERA -> SourceConfig.Camera()
            SourceType.SCREEN -> SourceConfig.Screen()
            SourceType.IMAGE -> SourceConfig.Image()
            SourceType.MEDIA -> SourceConfig.Media()
            SourceType.BROWSER -> SourceConfig.Browser()
        }

        val newSource = Source(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            type = type,
            visible = true,
            locked = false,
            transform = transform,
            config = effectiveConfig
        )

        val updatedScene = scene.withSourceAdded(newSource)
        val updateResult = sceneManager.updateScene(updatedScene)

        return when (updateResult) {
            is AppResult.Success -> {
                val persisted = updateResult.data.findSource(newSource.id) ?: newSource
                AppResult.Success(persisted)
            }
            is AppResult.Error -> updateResult
        }
    }

    override suspend fun removeSource(sceneId: String, sourceId: String): AppResult<Unit> {
        val scene = sceneManager.getScene(sceneId)
            ?: return AppResult.Error(AppError.Scene.SceneNotFound(sceneId))

        if (scene.findSource(sourceId) == null) {
            return AppResult.Error(AppError.Source.SourceNotFound(sourceId))
        }

        val updatedScene = scene.withSourceRemoved(sourceId)
        val updateResult = sceneManager.updateScene(updatedScene)

        return when (updateResult) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Error -> updateResult
        }
    }

    override suspend fun duplicateSource(sceneId: String, sourceId: String): AppResult<Source> {
        val scene = sceneManager.getScene(sceneId)
            ?: return AppResult.Error(AppError.Scene.SceneNotFound(sceneId))

        val original = scene.findSource(sourceId)
            ?: return AppResult.Error(AppError.Source.SourceNotFound(sourceId))

        val newId = UUID.randomUUID().toString()
        val duplicated = original.duplicate(
            newId = newId,
            newName = "${original.name} (Copy)"
        )

        val updatedScene = scene.withSourceAdded(duplicated)
        val updateResult = sceneManager.updateScene(updatedScene)

        return when (updateResult) {
            is AppResult.Success -> {
                val persisted = updateResult.data.findSource(newId) ?: duplicated
                AppResult.Success(persisted)
            }
            is AppResult.Error -> updateResult
        }
    }

    override suspend fun renameSource(
        sceneId: String,
        sourceId: String,
        newName: String
    ): AppResult<Source> {
        if (newName.isBlank()) {
            return AppResult.Error(AppError.Source.InvalidTransform("Source name cannot be blank"))
        }

        return updateSourceInternal(sceneId, sourceId) { it.copy(name = newName.trim()) }
    }

    override suspend fun reorderSource(
        sceneId: String,
        sourceId: String,
        action: LayerOrderAction
    ): AppResult<Source> {
        val scene = sceneManager.getScene(sceneId)
            ?: return AppResult.Error(AppError.Scene.SceneNotFound(sceneId))

        val target = scene.findSource(sourceId)
            ?: return AppResult.Error(AppError.Source.SourceNotFound(sourceId))

        val reorderedScene = when (action) {
            LayerOrderAction.BRING_TO_FRONT -> scene.bringToFront(sourceId)
            LayerOrderAction.SEND_TO_BACK -> scene.sendToBack(sourceId)
            LayerOrderAction.MOVE_UP -> scene.moveUp(sourceId)
            LayerOrderAction.MOVE_DOWN -> scene.moveDown(sourceId)
        }

        val updateResult = sceneManager.updateScene(reorderedScene)
        return when (updateResult) {
            is AppResult.Success -> {
                val persisted = updateResult.data.findSource(sourceId) ?: target
                AppResult.Success(persisted)
            }
            is AppResult.Error -> updateResult
        }
    }

    override suspend fun setSourceVisibility(
        sceneId: String,
        sourceId: String,
        isVisible: Boolean
    ): AppResult<Source> {
        return updateSourceInternal(sceneId, sourceId) { it.copy(visible = isVisible) }
    }

    override suspend fun setSourceLock(
        sceneId: String,
        sourceId: String,
        isLocked: Boolean
    ): AppResult<Source> {
        return updateSourceInternal(sceneId, sourceId) { it.copy(locked = isLocked) }
    }

    override suspend fun updateTransform(
        sceneId: String,
        sourceId: String,
        transform: Transform
    ): AppResult<Source> {
        return updateSourceInternal(sceneId, sourceId) { it.copy(transform = transform) }
    }

    override suspend fun updateCrop(
        sceneId: String,
        sourceId: String,
        crop: Crop
    ): AppResult<Source> {
        val scene = sceneManager.getScene(sceneId)
            ?: return AppResult.Error(AppError.Scene.SceneNotFound(sceneId))

        val target = scene.findSource(sourceId)
            ?: return AppResult.Error(AppError.Source.SourceNotFound(sourceId))

        if (!crop.isValidFor(target.transform.width, target.transform.height)) {
            return AppResult.Error(
                AppError.Source.InvalidCrop(
                    "Crop dimensions (L:${crop.left}, R:${crop.right}, T:${crop.top}, B:${crop.bottom}) " +
                            "exceed layer boundaries (${target.transform.width}x${target.transform.height})"
                )
            )
        }

        return updateSourceInternal(sceneId, sourceId) { it.copy(crop = crop) }
    }

    override suspend fun updateOpacity(
        sceneId: String,
        sourceId: String,
        opacity: Float
    ): AppResult<Source> {
        val clamped = opacity.coerceIn(0f, 1f)
        return updateSourceInternal(sceneId, sourceId) { it.copy(opacity = clamped) }
    }

    override suspend fun updateChromaKey(
        sceneId: String,
        sourceId: String,
        chromaKey: ChromaKeyConfig?
    ): AppResult<Source> {
        return updateSourceInternal(sceneId, sourceId) { it.copy(chromaKey = chromaKey) }
    }

    override suspend fun updateConfig(
        sceneId: String,
        sourceId: String,
        config: SourceConfig
    ): AppResult<Source> {
        return updateSourceInternal(sceneId, sourceId) { it.copy(config = config) }
    }

    override fun getSources(sceneId: String): List<Source> {
        val scene = sceneManager.getScene(sceneId) ?: return emptyList()
        return scene.sortedSources
    }

    override fun getSource(sceneId: String, sourceId: String): Source? {
        val scene = sceneManager.getScene(sceneId) ?: return null
        return scene.findSource(sourceId)
    }

    override fun getSupportedSourceTypes(): List<SourceType> {
        return listOf(
            SourceType.CAMERA,
            SourceType.SCREEN,
            SourceType.IMAGE,
            SourceType.TEXT,
            SourceType.MEDIA,
            SourceType.BROWSER
        )
    }

    private suspend fun updateSourceInternal(
        sceneId: String,
        sourceId: String,
        block: (Source) -> Source
    ): AppResult<Source> {
        val scene = sceneManager.getScene(sceneId)
            ?: return AppResult.Error(AppError.Scene.SceneNotFound(sceneId))

        val target = scene.findSource(sourceId)
            ?: return AppResult.Error(AppError.Source.SourceNotFound(sourceId))

        val modified = block(target)
        val updatedScene = scene.withSourceUpdated(modified)
        val updateResult = sceneManager.updateScene(updatedScene)

        return when (updateResult) {
            is AppResult.Success -> {
                val persisted = updateResult.data.findSource(sourceId) ?: modified
                AppResult.Success(persisted)
            }
            is AppResult.Error -> updateResult
        }
    }
}
