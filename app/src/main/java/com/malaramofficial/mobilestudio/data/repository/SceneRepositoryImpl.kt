package com.malaramofficial.mobilestudio.data.repository

import com.malaramofficial.mobilestudio.core.model.AppError
import com.malaramofficial.mobilestudio.core.model.AppResult
import com.malaramofficial.mobilestudio.data.local.dao.SceneDao
import com.malaramofficial.mobilestudio.data.local.dao.SourceDao
import com.malaramofficial.mobilestudio.data.local.entity.SceneEntity
import com.malaramofficial.mobilestudio.data.local.entity.SourceEntity
import com.malaramofficial.mobilestudio.domain.model.scene.ChromaKeyConfig
import com.malaramofficial.mobilestudio.domain.model.scene.Crop
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.Source
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.scene.Transform
import com.malaramofficial.mobilestudio.domain.model.scene.Transition
import com.malaramofficial.mobilestudio.domain.repository.SceneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SceneRepositoryImpl(
    private val sceneDao: SceneDao,
    private val sourceDao: SourceDao
) : SceneRepository {

    override fun getAllScenes(): Flow<List<Scene>> {
        return sceneDao.getAllScenes().map { sceneEntities ->
            sceneEntities.map { sceneEntity ->
                val sourceEntities = sourceDao.getSourcesForSceneDirect(sceneEntity.id)
                mapToDomainScene(sceneEntity, sourceEntities)
            }
        }
    }

    override suspend fun getSceneById(id: String): Scene? {
        val sceneEntity = sceneDao.getSceneById(id) ?: return null
        val sourceEntities = sourceDao.getSourcesForSceneDirect(id)
        return mapToDomainScene(sceneEntity, sourceEntities)
    }

    override suspend fun saveScene(scene: Scene): AppResult<Unit> {
        return try {
            val sceneEntity = SceneEntity(
                id = scene.id,
                name = scene.name,
                orderIndex = scene.orderIndex,
                transitionType = scene.transition.type.name,
                transitionDurationMs = scene.transition.durationMs
            )
            val sourceEntities = scene.sources.map { source ->
                SourceEntity(
                    id = source.id,
                    sceneId = scene.id,
                    name = source.name,
                    type = source.type.name,
                    visible = source.visible,
                    locked = source.locked,
                    posX = source.transform.x,
                    posY = source.transform.y,
                    width = source.transform.width,
                    height = source.transform.height,
                    rotation = source.transform.rotation,
                    scaleX = source.transform.scaleX,
                    scaleY = source.transform.scaleY,
                    cropLeft = source.crop.left,
                    cropTop = source.crop.top,
                    cropRight = source.crop.right,
                    cropBottom = source.crop.bottom,
                    opacity = source.opacity,
                    zIndex = source.zIndex,
                    chromaEnabled = source.chromaKey?.enabled ?: false,
                    chromaColorHex = source.chromaKey?.keyColorHex ?: 0xFF00FF00,
                    chromaSimilarity = source.chromaKey?.similarity ?: 0.4f,
                    chromaSmoothness = source.chromaKey?.smoothness ?: 0.08f,
                    chromaSpill = source.chromaKey?.spillReduction ?: 0.5f,
                    customData = if (source.config != null) {
                        com.malaramofficial.mobilestudio.data.local.serializer.SourceConfigSerializer.serialize(source.config)
                    } else {
                        source.customData
                    }
                )
            }
            sceneDao.insertScene(sceneEntity)
            sourceDao.deleteSourcesForScene(scene.id)
            if (sourceEntities.isNotEmpty()) {
                sourceDao.insertSources(sourceEntities)
            }
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(AppError.Storage.FileWriteFailed(scene.id, e))
        }
    }

    override suspend fun deleteScene(id: String): AppResult<Unit> {
        return try {
            sceneDao.deleteSceneById(id)
            sourceDao.deleteSourcesForScene(id)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(AppError.Storage.FileWriteFailed(id, e))
        }
    }

    override suspend fun reorderScenes(sceneIds: List<String>): AppResult<Unit> {
        return try {
            sceneIds.forEachIndexed { index, id ->
                sceneDao.updateOrderIndex(id, index)
            }
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(AppError.Storage.FileWriteFailed("reorder", e))
        }
    }

    private fun mapToDomainScene(
        sceneEntity: SceneEntity,
        sourceEntities: List<SourceEntity>
    ): Scene {
        val transitionType = try {
            Transition.Type.valueOf(sceneEntity.transitionType)
        } catch (e: Exception) {
            Transition.Type.CUT
        }
        val sources = sourceEntities.map { se ->
            val sourceType = try {
                SourceType.valueOf(se.type)
            } catch (e: Exception) {
                SourceType.IMAGE
            }
            val chroma = if (se.chromaEnabled) {
                ChromaKeyConfig(
                    enabled = true,
                    keyColorHex = se.chromaColorHex,
                    similarity = se.chromaSimilarity,
                    smoothness = se.chromaSmoothness,
                    spillReduction = se.chromaSpill
                )
            } else null

            Source(
                id = se.id,
                name = se.name,
                type = sourceType,
                visible = se.visible,
                locked = se.locked,
                transform = Transform(
                    x = se.posX,
                    y = se.posY,
                    width = se.width,
                    height = se.height,
                    rotation = se.rotation,
                    scaleX = se.scaleX,
                    scaleY = se.scaleY
                ),
                crop = Crop(
                    left = se.cropLeft,
                    top = se.cropTop,
                    right = se.cropRight,
                    bottom = se.cropBottom
                ),
                opacity = se.opacity,
                zIndex = se.zIndex,
                chromaKey = chroma,
                config = com.malaramofficial.mobilestudio.data.local.serializer.SourceConfigSerializer.deserialize(se.customData, sourceType),
                customData = se.customData
            )
        }
        return Scene(
            id = sceneEntity.id,
            name = sceneEntity.name,
            sources = sources,
            transition = Transition(transitionType, sceneEntity.transitionDurationMs),
            orderIndex = sceneEntity.orderIndex
        )
    }
}
