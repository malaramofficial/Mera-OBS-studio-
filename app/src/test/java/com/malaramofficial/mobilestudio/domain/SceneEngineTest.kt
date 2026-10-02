package com.malaramofficial.mobilestudio.domain

import com.malaramofficial.mobilestudio.core.model.AppError
import com.malaramofficial.mobilestudio.core.model.AppResult
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.Source
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.scene.Transition
import com.malaramofficial.mobilestudio.domain.repository.SceneRepository
import com.malaramofficial.mobilestudio.engine.scene.SceneManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SceneEngineTest {

    private class InMemorySceneRepository : SceneRepository {
        private val list = mutableListOf<Scene>()
        private val flow = MutableStateFlow<List<Scene>>(emptyList())

        override fun getAllScenes(): Flow<List<Scene>> = flow

        override suspend fun getSceneById(id: String): Scene? = list.find { it.id == id }

        override suspend fun saveScene(scene: Scene): AppResult<Unit> {
            val idx = list.indexOfFirst { it.id == scene.id }
            if (idx >= 0) {
                list[idx] = scene
            } else {
                list.add(scene)
            }
            flow.value = list.toList()
            return AppResult.Success(Unit)
        }

        override suspend fun deleteScene(id: String): AppResult<Unit> {
            list.removeAll { it.id == id }
            flow.value = list.toList()
            return AppResult.Success(Unit)
        }

        override suspend fun reorderScenes(sceneIds: List<String>): AppResult<Unit> {
            val map = list.associateBy { it.id }
            list.clear()
            sceneIds.forEachIndexed { i, id ->
                map[id]?.let { list.add(it.copy(orderIndex = i)) }
            }
            flow.value = list.toList()
            return AppResult.Success(Unit)
        }
    }

    private lateinit var repository: InMemorySceneRepository
    private lateinit var sceneManager: SceneManagerImpl

    @Before
    fun setup() {
        repository = InMemorySceneRepository()
        sceneManager = SceneManagerImpl(repository, CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun testCreateAndRenameScene() = runBlocking {
        val createResult = sceneManager.createScene("Main Camera")
        assertTrue(createResult.isSuccess)
        val created = (createResult as AppResult.Success).data
        assertEquals("Main Camera", created.name)

        val renameResult = sceneManager.renameScene(created.id, "Studio Camera 1")
        assertTrue(renameResult.isSuccess)
        val renamed = (renameResult as AppResult.Success).data
        assertEquals("Studio Camera 1", renamed.name)
        assertEquals("Studio Camera 1", sceneManager.getScene(created.id)?.name)
    }

    @Test
    fun testCannotDeleteLastRemainingScene() = runBlocking {
        val createResult = sceneManager.createScene("Only Scene")
        val sceneId = (createResult as AppResult.Success).data.id

        val deleteResult = sceneManager.deleteScene(sceneId)
        assertTrue(deleteResult.isError)
        val error = (deleteResult as AppResult.Error).error
        assertTrue(error is AppError.Scene.CannotDeleteLastScene)
    }

    @Test
    fun testDeleteSceneAndAutoRecoveryOfActiveSelection() = runBlocking {
        val s1 = (sceneManager.createScene("Scene 1") as AppResult.Success).data
        val s2 = (sceneManager.createScene("Scene 2") as AppResult.Success).data

        sceneManager.setPreviewScene(s1.id)
        sceneManager.setProgramScene(s1.id)
        assertEquals(s1.id, sceneManager.previewScene.value?.id)
        assertEquals(s1.id, sceneManager.programScene.value?.id)

        // Delete active scene 1 -> Preview and Program should safely recover to scene 2
        val deleteResult = sceneManager.deleteScene(s1.id)
        assertTrue(deleteResult.isSuccess)

        assertEquals(s2.id, sceneManager.previewScene.value?.id)
        assertEquals(s2.id, sceneManager.programScene.value?.id)
    }

    @Test
    fun testDuplicateSceneCreatesIndependentCopies() = runBlocking {
        val s1 = (sceneManager.createScene("Original") as AppResult.Success).data
        val s1WithSource = s1.withSourceAdded(
            Source(id = "src_original", name = "Logo", type = SourceType.IMAGE)
        )
        sceneManager.updateScene(s1WithSource)

        val dupResult = sceneManager.duplicateScene(s1.id)
        assertTrue(dupResult.isSuccess)
        val duplicated = (dupResult as AppResult.Success).data

        assertEquals("Original (Copy)", duplicated.name)
        assertEquals(1, duplicated.sources.size)
        // Ensure new unique source ID
        assertTrue(duplicated.sources[0].id != "src_original")
    }

    @Test
    fun testCutAndTransitionExecution() = runBlocking {
        val s1 = (sceneManager.createScene("Scene 1") as AppResult.Success).data
        val s2 = (sceneManager.createScene("Scene 2") as AppResult.Success).data

        sceneManager.setPreviewScene(s1.id)
        sceneManager.setProgramScene(s2.id)

        assertEquals(s1.id, sceneManager.previewScene.value?.id)
        assertEquals(s2.id, sceneManager.programScene.value?.id)

        // CUT: promotes Preview (s1) to Program
        val cutResult = sceneManager.cut()
        assertTrue(cutResult.isSuccess)
        assertEquals(s1.id, sceneManager.programScene.value?.id)

        // Transition: updates activeTransition and promotes Preview
        sceneManager.setPreviewScene(s2.id)
        val transResult = sceneManager.transition(Transition.DefaultFade)
        assertTrue(transResult.isSuccess)
        assertEquals(Transition.Type.CROSSFADE, sceneManager.activeTransition.value.type)
        assertEquals(s2.id, sceneManager.programScene.value?.id)
    }
}
