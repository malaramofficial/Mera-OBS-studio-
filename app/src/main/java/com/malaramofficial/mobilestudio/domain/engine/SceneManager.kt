package com.malaramofficial.mobilestudio.domain.engine

import com.malaramofficial.mobilestudio.core.model.AppResult
import com.malaramofficial.mobilestudio.domain.model.render.RenderPlan
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.Transition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Production Scene Engine contract for managing scenes, layer topology,
 * and the dual-pipeline Preview / Program broadcast stage.
 */
interface SceneManager {
    val scenes: StateFlow<List<Scene>>
    val previewScene: StateFlow<Scene?>
    val programScene: StateFlow<Scene?>
    val activeTransition: StateFlow<Transition>
    val previewRenderPlan: StateFlow<RenderPlan?>
    val programRenderPlan: StateFlow<RenderPlan?>

    suspend fun createScene(name: String): AppResult<Scene>
    suspend fun deleteScene(sceneId: String): AppResult<Unit>
    suspend fun renameScene(sceneId: String, newName: String): AppResult<Scene>
    suspend fun duplicateScene(sceneId: String): AppResult<Scene>
    suspend fun reorderScenes(sceneIds: List<String>): AppResult<Unit>

    fun getScene(sceneId: String): Scene?
    fun getScenes(): Flow<List<Scene>>

    suspend fun setPreviewScene(sceneId: String): AppResult<Unit>
    suspend fun setProgramScene(sceneId: String): AppResult<Unit>
    suspend fun cut(): AppResult<Unit>
    suspend fun transition(transition: Transition): AppResult<Unit>

    /**
     * Updates an existing scene directly and synchronizes preview/program states if active.
     */
    suspend fun updateScene(scene: Scene): AppResult<Scene>
}
