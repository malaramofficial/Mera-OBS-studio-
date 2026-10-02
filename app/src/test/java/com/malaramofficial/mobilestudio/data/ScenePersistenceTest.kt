package com.malaramofficial.mobilestudio.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.malaramofficial.mobilestudio.data.local.StudioDatabase
import com.malaramofficial.mobilestudio.data.repository.SceneRepositoryImpl
import com.malaramofficial.mobilestudio.domain.model.scene.ChromaKeyConfig
import com.malaramofficial.mobilestudio.domain.model.scene.Crop
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.Source
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.scene.Transform
import com.malaramofficial.mobilestudio.domain.model.scene.Transition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ScenePersistenceTest {

    private lateinit var database: StudioDatabase
    private lateinit var repository: SceneRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            StudioDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = SceneRepositoryImpl(
            sceneDao = database.sceneDao(),
            sourceDao = database.sourceDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSaveAndLoadSceneWithSources() = runBlocking {
        val sourceCamera = Source(
            id = "src_cam",
            name = "Facecam",
            type = SourceType.CAMERA,
            visible = true,
            locked = false,
            transform = Transform(x = 100f, y = 150f, width = 640f, height = 360f, rotation = 0f),
            crop = Crop(left = 5f, top = 5f, right = 5f, bottom = 5f),
            opacity = 0.9f,
            zIndex = 1,
            chromaKey = ChromaKeyConfig(enabled = true, keyColorHex = 0xFF00FF00, similarity = 0.45f),
            config = com.malaramofficial.mobilestudio.domain.model.source.SourceConfig.Camera(
                lensFacing = com.malaramofficial.mobilestudio.domain.model.source.SourceConfig.Camera.LensFacing.FRONT,
                targetWidth = 1280,
                targetHeight = 720
            )
        )

        val sourceBg = Source(
            id = "src_bg",
            name = "Game",
            type = SourceType.SCREEN,
            zIndex = 0
        )

        val scene = Scene(
            id = "scene_complex",
            name = "Complex Layout",
            sources = listOf(sourceBg, sourceCamera),
            transition = Transition(Transition.Type.CROSSFADE, 400L),
            orderIndex = 0
        )

        val saveResult = repository.saveScene(scene)
        assertTrue(saveResult.isSuccess)

        val loaded = repository.getSceneById("scene_complex")
        assertNotNull(loaded)
        assertEquals("Complex Layout", loaded?.name)
        assertEquals(2, loaded?.sources?.size)
        assertEquals(Transition.Type.CROSSFADE, loaded?.transition?.type)
        assertEquals(400L, loaded?.transition?.durationMs)

        val loadedCam = loaded?.findSource("src_cam")
        assertNotNull(loadedCam)
        assertEquals(SourceType.CAMERA, loadedCam?.type)
        assertEquals(640f, loadedCam?.transform?.width ?: 0f, 0.001f)
        assertEquals(5f, loadedCam?.crop?.left ?: 0f, 0.001f)
        assertEquals(true, loadedCam?.chromaKey?.enabled)
        assertEquals(0.45f, loadedCam?.chromaKey?.similarity ?: 0f, 0.001f)
        val loadedConfig = loadedCam?.config as? com.malaramofficial.mobilestudio.domain.model.source.SourceConfig.Camera
        assertNotNull(loadedConfig)
        assertEquals(com.malaramofficial.mobilestudio.domain.model.source.SourceConfig.Camera.LensFacing.FRONT, loadedConfig?.lensFacing)
        assertEquals(1280, loadedConfig?.targetWidth)

        // Verify Flow emits the saved scene
        val allScenes = repository.getAllScenes().first()
        assertEquals(1, allScenes.size)
        assertEquals("scene_complex", allScenes[0].id)

        // Delete test
        val deleteResult = repository.deleteScene("scene_complex")
        assertTrue(deleteResult.isSuccess)
        val loadedAfterDelete = repository.getSceneById("scene_complex")
        assertNull(loadedAfterDelete)
    }
}
