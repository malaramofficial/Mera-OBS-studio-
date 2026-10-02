package com.malaramofficial.mobilestudio.engine.scene

import com.malaramofficial.mobilestudio.core.model.AppError
import com.malaramofficial.mobilestudio.core.model.AppResult
import com.malaramofficial.mobilestudio.domain.engine.SceneManager
import com.malaramofficial.mobilestudio.domain.model.render.RenderPlan
import com.malaramofficial.mobilestudio.domain.model.render.RenderPlanBuilder
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.Transition
import com.malaramofficial.mobilestudio.domain.repository.SceneRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Production implementation of [SceneManager] coordinating scene lifecycle,
 * persistence, and the dual-pipeline Preview / Program staging model.
 */
class SceneManagerImpl(
    private val sceneRepository: SceneRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : SceneManager {

    private val mutex = Mutex()

    private val _scenes = MutableStateFlow<List<Scene>>(emptyList())
    override val scenes: StateFlow<List<Scene>> = _scenes.asStateFlow()

    private val _previewSceneId = MutableStateFlow<String?>(null)
    private val _programSceneId = MutableStateFlow<String?>(null)
    private val _activeTransition = MutableStateFlow(Transition.Cut)
    override val activeTransition: StateFlow<Transition> = _activeTransition.asStateFlow()

    override val previewScene: StateFlow<Scene?> = combine(_scenes, _previewSceneId) { list, id ->
        list.find { it.id == id }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override val programScene: StateFlow<Scene?> = combine(_scenes, _programSceneId) { list, id ->
        list.find { it.id == id }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override val previewRenderPlan: StateFlow<RenderPlan?> = previewScene.map { scene ->
        scene?.let { RenderPlanBuilder.build(it) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override val programRenderPlan: StateFlow<RenderPlan?> = programScene.map { scene ->
        scene?.let { RenderPlanBuilder.build(it) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        // Collect scenes reactively from repository
        scope.launch {
            sceneRepository.getAllScenes().collect { repositoryScenes ->
                mutex.withLock {
                    _scenes.value = repositoryScenes
                    // Auto-select initial scenes if unselected
                    if (_previewSceneId.value == null && repositoryScenes.isNotEmpty()) {
                        _previewSceneId.value = repositoryScenes.first().id
                    }
                    if (_programSceneId.value == null && repositoryScenes.isNotEmpty()) {
                        _programSceneId.value = repositoryScenes.first().id
                    }
                }
            }
        }
    }

    override suspend fun createScene(name: String): AppResult<Scene> = mutex.withLock {
        if (name.isBlank()) {
            return AppResult.Error(AppError.Scene.InvalidSceneName("Name cannot be blank"))
        }

        val newOrder = (_scenes.value.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val newScene = Scene(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            sources = emptyList(),
            orderIndex = newOrder
        )

        val saveResult = sceneRepository.saveScene(newScene)
        if (saveResult is AppResult.Error) return saveResult

        val updatedList = _scenes.value + newScene
        _scenes.value = updatedList

        if (_previewSceneId.value == null) {
            _previewSceneId.value = newScene.id
        }
        if (_programSceneId.value == null) {
            _programSceneId.value = newScene.id
        }

        return AppResult.Success(newScene)
    }

    override suspend fun deleteScene(sceneId: String): AppResult<Unit> = mutex.withLock {
        if (_scenes.value.size <= 1) {
            return AppResult.Error(AppError.Scene.CannotDeleteLastScene)
        }

        val target = _scenes.value.find { it.id == sceneId }
            ?: return AppResult.Error(AppError.Scene.SceneNotFound(sceneId))

        val deleteResult = sceneRepository.deleteScene(sceneId)
        if (deleteResult is AppResult.Error) return deleteResult

        val remainingScenes = _scenes.value.filterNot { it.id == sceneId }
        _scenes.value = remainingScenes

        // Safe recovery if the deleted scene was active
        if (_previewSceneId.value == sceneId) {
            _previewSceneId.value = remainingScenes.firstOrNull()?.id
        }
        if (_programSceneId.value == sceneId) {
            _programSceneId.value = remainingScenes.firstOrNull()?.id
        }

        return AppResult.Success(Unit)
    }

    override suspend fun renameScene(sceneId: String, newName: String): AppResult<Scene> = mutex.withLock {
        if (newName.isBlank()) {
            return AppResult.Error(AppError.Scene.InvalidSceneName("Name cannot be blank"))
        }

        val target = _scenes.value.find { it.id == sceneId }
            ?: return AppResult.Error(AppError.Scene.SceneNotFound(sceneId))

        val renamed = target.copy(name = newName.trim())
        val saveResult = sceneRepository.saveScene(renamed)
        if (saveResult is AppResult.Error) return saveResult

        _scenes.value = _scenes.value.map { if (it.id == sceneId) renamed else it }
        return AppResult.Success(renamed)
    }

    override suspend fun duplicateScene(sceneId: String): AppResult<Scene> = mutex.withLock {
        val target = _scenes.value.find { it.id == sceneId }
            ?: return AppResult.Error(AppError.Scene.SceneNotFound(sceneId))

        val duplicated = target.duplicate(
            newSceneId = UUID.randomUUID().toString(),
            newSceneName = "${target.name} (Copy)"
        )

        val saveResult = sceneRepository.saveScene(duplicated)
        if (saveResult is AppResult.Error) return saveResult

        _scenes.value = _scenes.value + duplicated
        return AppResult.Success(duplicated)
    }

    override suspend fun reorderScenes(sceneIds: List<String>): AppResult<Unit> = mutex.withLock {
        val currentMap = _scenes.value.associateBy { it.id }
        val reorderedList = sceneIds.mapNotNull { currentMap[it] }

        if (reorderedList.size != _scenes.value.size) {
            return AppResult.Error(AppError.Scene.SceneNotFound("Mismatched scene IDs during reorder"))
        }

        val indexedList = reorderedList.mapIndexed { index, scene ->
            scene.copy(orderIndex = index)
        }

        indexedList.forEach { sceneRepository.saveScene(it) }
        _scenes.value = indexedList
        return AppResult.Success(Unit)
    }

    override fun getScene(sceneId: String): Scene? {
        return _scenes.value.find { it.id == sceneId }
    }

    override fun getScenes(): Flow<List<Scene>> {
        return scenes
    }

    override suspend fun setPreviewScene(sceneId: String): AppResult<Unit> = mutex.withLock {
        val exists = _scenes.value.any { it.id == sceneId }
        if (!exists) {
            return AppResult.Error(AppError.Scene.SceneNotFound(sceneId))
        }
        _previewSceneId.value = sceneId
        return AppResult.Success(Unit)
    }

    override suspend fun setProgramScene(sceneId: String): AppResult<Unit> = mutex.withLock {
        val exists = _scenes.value.any { it.id == sceneId }
        if (!exists) {
            return AppResult.Error(AppError.Scene.SceneNotFound(sceneId))
        }
        _programSceneId.value = sceneId
        return AppResult.Success(Unit)
    }

    /**
     * Executes CUT: Instantaneous logical promotion of Preview scene to Program.
     * Guarantees Preview modifications do not corrupt Program, and Program changes
     * do not accidentally alter Preview.
     */
    override suspend fun cut(): AppResult<Unit> = mutex.withLock {
        val previewId = _previewSceneId.value
            ?: return AppResult.Error(AppError.Scene.SceneNotFound("No preview scene staged"))

        _programSceneId.value = previewId
        return AppResult.Success(Unit)
    }

    override suspend fun transition(transition: Transition): AppResult<Unit> = mutex.withLock {
        val previewId = _previewSceneId.value
            ?: return AppResult.Error(AppError.Scene.SceneNotFound("No preview scene staged"))

        _activeTransition.value = transition
        _programSceneId.value = previewId
        return AppResult.Success(Unit)
    }

    override suspend fun updateScene(scene: Scene): AppResult<Scene> = mutex.withLock {
        val normalized = scene.normalizeLayers()
        val saveResult = sceneRepository.saveScene(normalized)
        if (saveResult is AppResult.Error) return saveResult

        _scenes.value = _scenes.value.map { if (it.id == normalized.id) normalized else it }
        return AppResult.Success(normalized)
    }
}
