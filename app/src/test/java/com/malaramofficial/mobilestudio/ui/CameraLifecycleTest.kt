package com.malaramofficial.mobilestudio.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.malaramofficial.mobilestudio.core.model.AppError
import com.malaramofficial.mobilestudio.core.model.AppResult
import com.malaramofficial.mobilestudio.core.permissions.PermissionManager
import com.malaramofficial.mobilestudio.data.datastore.StudioPreferences
import com.malaramofficial.mobilestudio.data.security.SecureCredentialStore
import com.malaramofficial.mobilestudio.engine.stream.StudioBroadcastController
import com.malaramofficial.mobilestudio.engine.recording.StudioRecordingController
import com.malaramofficial.mobilestudio.domain.engine.SceneManager
import com.malaramofficial.mobilestudio.domain.engine.SourceManager
import com.malaramofficial.mobilestudio.domain.model.render.RenderPlan
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.Transition
import com.malaramofficial.mobilestudio.engine.camera.CameraSourceEngine
import com.malaramofficial.mobilestudio.engine.camera.CameraState
import com.malaramofficial.mobilestudio.engine.gpu.StudioRenderPipeline
import com.malaramofficial.mobilestudio.engine.gpu.VisualTextureSourceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CameraLifecycleTest {

    private class MockSceneManager : SceneManager {
        val sceneFlow = MutableStateFlow<List<Scene>>(emptyList())
        val planFlow = MutableStateFlow<RenderPlan?>(null)
        val transFlow = MutableStateFlow<Transition>(Transition.Cut)

        override val scenes: StateFlow<List<Scene>> = sceneFlow
        override val previewScene: StateFlow<Scene?> = MutableStateFlow(null)
        override val programScene: StateFlow<Scene?> = MutableStateFlow(null)
        override val activeTransition: StateFlow<Transition> = transFlow
        override val previewRenderPlan: StateFlow<RenderPlan?> = planFlow
        override val programRenderPlan: StateFlow<RenderPlan?> = planFlow

        override suspend fun createScene(name: String): AppResult<Scene> = AppResult.Success(Scene(id = "s1", name = name))
        override suspend fun deleteScene(sceneId: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun renameScene(sceneId: String, newName: String): AppResult<Scene> = AppResult.Success(Scene(id = sceneId, name = newName))
        override suspend fun duplicateScene(sceneId: String): AppResult<Scene> = AppResult.Success(Scene(id = "s_dup", name = "Copy"))
        override suspend fun reorderScenes(sceneIds: List<String>): AppResult<Unit> = AppResult.Success(Unit)
        override fun getScene(sceneId: String): Scene? = null
        override fun getScenes(): Flow<List<Scene>> = sceneFlow
        override suspend fun setPreviewScene(sceneId: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun setProgramScene(sceneId: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun cut(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun transition(transition: Transition): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun updateScene(scene: Scene): AppResult<Scene> = AppResult.Success(scene)
    }

    private class MockSourceManager : SourceManager {
        override suspend fun addSource(sceneId: String, name: String, type: com.malaramofficial.mobilestudio.domain.model.scene.SourceType, config: com.malaramofficial.mobilestudio.domain.model.source.SourceConfig?, transform: com.malaramofficial.mobilestudio.domain.model.scene.Transform) = AppResult.Success(com.malaramofficial.mobilestudio.domain.model.scene.Source(id = "src1", name = name, type = type))
        override suspend fun removeSource(sceneId: String, sourceId: String) = AppResult.Success(Unit)
        override suspend fun duplicateSource(sceneId: String, sourceId: String) = AppResult.Success(com.malaramofficial.mobilestudio.domain.model.scene.Source(id = "src2", name = "Copy", type = com.malaramofficial.mobilestudio.domain.model.scene.SourceType.CAMERA))
        override suspend fun renameSource(sceneId: String, sourceId: String, newName: String) = AppResult.Success(com.malaramofficial.mobilestudio.domain.model.scene.Source(id = sourceId, name = newName, type = com.malaramofficial.mobilestudio.domain.model.scene.SourceType.CAMERA))
        override suspend fun reorderSource(sceneId: String, sourceId: String, action: com.malaramofficial.mobilestudio.domain.engine.LayerOrderAction) = AppResult.Success(com.malaramofficial.mobilestudio.domain.model.scene.Source(id = sourceId, name = "Reordered", type = com.malaramofficial.mobilestudio.domain.model.scene.SourceType.CAMERA))
        override suspend fun setSourceVisibility(sceneId: String, sourceId: String, isVisible: Boolean) = AppResult.Success(com.malaramofficial.mobilestudio.domain.model.scene.Source(id = sourceId, name = "Vis", type = com.malaramofficial.mobilestudio.domain.model.scene.SourceType.CAMERA, visible = isVisible))
        override suspend fun setSourceLock(sceneId: String, sourceId: String, isLocked: Boolean) = AppResult.Success(com.malaramofficial.mobilestudio.domain.model.scene.Source(id = sourceId, name = "Lock", type = com.malaramofficial.mobilestudio.domain.model.scene.SourceType.CAMERA, locked = isLocked))
        override suspend fun updateTransform(sceneId: String, sourceId: String, transform: com.malaramofficial.mobilestudio.domain.model.scene.Transform) = AppResult.Success(com.malaramofficial.mobilestudio.domain.model.scene.Source(id = sourceId, name = "T", type = com.malaramofficial.mobilestudio.domain.model.scene.SourceType.CAMERA, transform = transform))
        override suspend fun updateCrop(sceneId: String, sourceId: String, crop: com.malaramofficial.mobilestudio.domain.model.scene.Crop) = AppResult.Success(com.malaramofficial.mobilestudio.domain.model.scene.Source(id = sourceId, name = "C", type = com.malaramofficial.mobilestudio.domain.model.scene.SourceType.CAMERA, crop = crop))
        override suspend fun updateOpacity(sceneId: String, sourceId: String, opacity: Float) = AppResult.Success(com.malaramofficial.mobilestudio.domain.model.scene.Source(id = sourceId, name = "O", type = com.malaramofficial.mobilestudio.domain.model.scene.SourceType.CAMERA, opacity = opacity))
        override suspend fun updateChromaKey(sceneId: String, sourceId: String, chromaKey: com.malaramofficial.mobilestudio.domain.model.scene.ChromaKeyConfig?) = AppResult.Success(com.malaramofficial.mobilestudio.domain.model.scene.Source(id = sourceId, name = "CK", type = com.malaramofficial.mobilestudio.domain.model.scene.SourceType.CAMERA, chromaKey = chromaKey))
        override suspend fun updateConfig(sceneId: String, sourceId: String, config: com.malaramofficial.mobilestudio.domain.model.source.SourceConfig) = AppResult.Success(com.malaramofficial.mobilestudio.domain.model.scene.Source(id = sourceId, name = "Cfg", type = com.malaramofficial.mobilestudio.domain.model.scene.SourceType.CAMERA, config = config))
        override fun getSources(sceneId: String) = emptyList<com.malaramofficial.mobilestudio.domain.model.scene.Source>()
        override fun getSource(sceneId: String, sourceId: String) = null
        override fun getSupportedSourceTypes() = listOf(com.malaramofficial.mobilestudio.domain.model.scene.SourceType.CAMERA)
    }

    private lateinit var context: Context
    private lateinit var viewModel: StudioViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val perm = PermissionManager(context)
        val pref = StudioPreferences(context)
        val pipeline = StudioRenderPipeline()
        val cameraEngine = CameraSourceEngine(context)
        val visualTextureSourceManager = VisualTextureSourceManager(context, pipeline)
        viewModel = StudioViewModel(
            sceneManager = MockSceneManager(),
            sourceManager = MockSourceManager(),
            preferences = pref,
            permissionManager = perm,
            renderPipeline = pipeline,
            cameraSourceEngine = cameraEngine,
            visualTextureSourceManager = visualTextureSourceManager,
            appContext = context,
            secureCredentialStore = SecureCredentialStore(context),
            broadcastController = StudioBroadcastController(pipeline),
            recordingController = StudioRecordingController(context, pipeline)
        )
    }

    @Test
    fun testInitialCameraStateInViewModelIsIdle() {
        assertEquals(CameraState.Idle, viewModel.cameraState.value)
    }

    @Test
    fun testStopCameraResetsState() {
        viewModel.stopCamera()
        assertEquals(CameraState.Idle, viewModel.cameraState.value)
    }
}
