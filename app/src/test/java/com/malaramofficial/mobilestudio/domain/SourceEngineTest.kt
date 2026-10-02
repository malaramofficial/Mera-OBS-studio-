package com.malaramofficial.mobilestudio.domain

import com.malaramofficial.mobilestudio.core.model.AppError
import com.malaramofficial.mobilestudio.core.model.AppResult
import com.malaramofficial.mobilestudio.domain.engine.LayerOrderAction
import com.malaramofficial.mobilestudio.domain.model.scene.ChromaKeyConfig
import com.malaramofficial.mobilestudio.domain.model.scene.Crop
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.scene.Transform
import com.malaramofficial.mobilestudio.domain.repository.SceneRepository
import com.malaramofficial.mobilestudio.engine.scene.SceneManagerImpl
import com.malaramofficial.mobilestudio.engine.source.SourceManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SourceEngineTest {

    private class SimpleRepo : SceneRepository {
        val scenes = mutableListOf<Scene>()
        val flow = MutableStateFlow<List<Scene>>(emptyList())
        override fun getAllScenes(): Flow<List<Scene>> = flow
        override suspend fun getSceneById(id: String): Scene? = scenes.find { it.id == id }
        override suspend fun saveScene(scene: Scene): AppResult<Unit> {
            val i = scenes.indexOfFirst { it.id == scene.id }
            if (i >= 0) scenes[i] = scene else scenes.add(scene)
            flow.value = scenes.toList()
            return AppResult.Success(Unit)
        }
        override suspend fun deleteScene(id: String): AppResult<Unit> {
            scenes.removeAll { it.id == id }
            flow.value = scenes.toList()
            return AppResult.Success(Unit)
        }
        override suspend fun reorderScenes(sceneIds: List<String>): AppResult<Unit> = AppResult.Success(Unit)
    }

    private lateinit var repo: SimpleRepo
    private lateinit var sceneManager: SceneManagerImpl
    private lateinit var sourceManager: SourceManagerImpl
    private lateinit var testSceneId: String

    @Before
    fun setup() = runBlocking {
        repo = SimpleRepo()
        sceneManager = SceneManagerImpl(repo, CoroutineScope(Dispatchers.Unconfined))
        sourceManager = SourceManagerImpl(sceneManager)
        val s = (sceneManager.createScene("Source Test Scene") as AppResult.Success).data
        testSceneId = s.id
    }

    @Test
    fun testAddAndRemoveSource() = runBlocking {
        val addResult = sourceManager.addSource(
            sceneId = testSceneId,
            name = "Webcam Layer",
            type = SourceType.CAMERA
        )
        assertTrue(addResult.isSuccess)
        val added = (addResult as AppResult.Success).data
        assertEquals("Webcam Layer", added.name)
        assertEquals(SourceType.CAMERA, added.type)
        assertEquals(0, added.zIndex)

        val sources = sourceManager.getSources(testSceneId)
        assertEquals(1, sources.size)

        val removeResult = sourceManager.removeSource(testSceneId, added.id)
        assertTrue(removeResult.isSuccess)
        assertEquals(0, sourceManager.getSources(testSceneId).size)
    }

    @Test
    fun testDuplicateAndRenameSource() = runBlocking {
        val added = (sourceManager.addSource(testSceneId, "Logo", SourceType.IMAGE) as AppResult.Success).data

        val dupResult = sourceManager.duplicateSource(testSceneId, added.id)
        assertTrue(dupResult.isSuccess)
        val duplicated = (dupResult as AppResult.Success).data

        assertEquals("Logo (Copy)", duplicated.name)
        assertTrue(duplicated.id != added.id)
        assertEquals(2, sourceManager.getSources(testSceneId).size)

        val renameResult = sourceManager.renameSource(testSceneId, duplicated.id, "Sponsor Banner")
        assertTrue(renameResult.isSuccess)
        val renamed = (renameResult as AppResult.Success).data
        assertEquals("Sponsor Banner", renamed.name)
    }

    @Test
    fun testLayerReordering() = runBlocking {
        val s1 = (sourceManager.addSource(testSceneId, "Layer 1", SourceType.IMAGE) as AppResult.Success).data
        val s2 = (sourceManager.addSource(testSceneId, "Layer 2", SourceType.CAMERA) as AppResult.Success).data
        val s3 = (sourceManager.addSource(testSceneId, "Layer 3", SourceType.TEXT) as AppResult.Success).data

        // Initial order: s1 (Z:0), s2 (Z:1), s3 (Z:2)
        assertEquals(listOf(s1.id, s2.id, s3.id), sourceManager.getSources(testSceneId).map { it.id })

        // Move s3 to back
        sourceManager.reorderSource(testSceneId, s3.id, LayerOrderAction.SEND_TO_BACK)
        assertEquals(listOf(s3.id, s1.id, s2.id), sourceManager.getSources(testSceneId).map { it.id })

        // Bring s3 to front
        sourceManager.reorderSource(testSceneId, s3.id, LayerOrderAction.BRING_TO_FRONT)
        assertEquals(listOf(s1.id, s2.id, s3.id), sourceManager.getSources(testSceneId).map { it.id })
    }

    @Test
    fun testVisibilityAndLockToggles() = runBlocking {
        val s = (sourceManager.addSource(testSceneId, "Test Layer", SourceType.IMAGE) as AppResult.Success).data
        assertTrue(s.visible)
        assertFalse(s.locked)

        val hideResult = sourceManager.setSourceVisibility(testSceneId, s.id, false)
        assertFalse((hideResult as AppResult.Success).data.visible)

        val lockResult = sourceManager.setSourceLock(testSceneId, s.id, true)
        assertTrue((lockResult as AppResult.Success).data.locked)
    }

    @Test
    fun testUpdateTransformAndCrop() = runBlocking {
        val s = (sourceManager.addSource(
            testSceneId,
            "Video Box",
            SourceType.MEDIA,
            transform = Transform(width = 1920f, height = 1080f)
        ) as AppResult.Success).data

        val newTransform = s.transform.moveBy(50f, 100f).resize(1280f, 720f)
        val tResult = sourceManager.updateTransform(testSceneId, s.id, newTransform)
        assertTrue(tResult.isSuccess)
        val updatedT = (tResult as AppResult.Success).data.transform
        assertEquals(50f, updatedT.x, 0.001f)
        assertEquals(1280f, updatedT.width, 0.001f)

        // Valid crop
        val validCrop = Crop(left = 50f, top = 50f, right = 50f, bottom = 50f)
        val cropResult = sourceManager.updateCrop(testSceneId, s.id, validCrop)
        assertTrue(cropResult.isSuccess)

        // Invalid impossible crop (exceeds width: 700 + 700 = 1400 > 1280)
        val impossibleCrop = Crop(left = 700f, top = 0f, right = 700f, bottom = 0f)
        val badCropResult = sourceManager.updateCrop(testSceneId, s.id, impossibleCrop)
        assertTrue(badCropResult.isError)
        val error = (badCropResult as AppResult.Error).error
        assertTrue(error is AppError.Source.InvalidCrop)
    }

    @Test
    fun testUpdateOpacityAndChromaKey() = runBlocking {
        val s = (sourceManager.addSource(testSceneId, "Chroma Cam", SourceType.CAMERA) as AppResult.Success).data

        val opacityResult = sourceManager.updateOpacity(testSceneId, s.id, 0.75f)
        assertEquals(0.75f, (opacityResult as AppResult.Success).data.opacity, 0.001f)

        val chroma = ChromaKeyConfig(enabled = true, keyColorHex = 0xFF00FF00, similarity = 0.42f)
        val chromaResult = sourceManager.updateChromaKey(testSceneId, s.id, chroma)
        assertTrue(chromaResult.isSuccess)
        val updatedChroma = (chromaResult as AppResult.Success).data.chromaKey
        assertNotNull(updatedChroma)
        assertEquals(0.42f, updatedChroma?.similarity ?: 0f, 0.001f)
    }
}
