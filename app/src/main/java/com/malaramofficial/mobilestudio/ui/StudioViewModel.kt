package com.malaramofficial.mobilestudio.ui

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.malaramofficial.mobilestudio.core.model.AppError
import com.malaramofficial.mobilestudio.core.model.AppResult
import com.malaramofficial.mobilestudio.core.permissions.PermissionManager
import com.malaramofficial.mobilestudio.data.datastore.StudioPreferences
import com.malaramofficial.mobilestudio.data.security.SecureCredentialStore
import com.malaramofficial.mobilestudio.engine.stream.StudioBroadcastController
import com.malaramofficial.mobilestudio.domain.engine.RecordingController
import com.malaramofficial.mobilestudio.domain.model.recording.RecordingConfig
import com.malaramofficial.mobilestudio.service.StudioService
import com.malaramofficial.mobilestudio.domain.engine.LayerOrderAction
import com.malaramofficial.mobilestudio.domain.engine.SceneManager
import com.malaramofficial.mobilestudio.domain.engine.SourceManager
import com.malaramofficial.mobilestudio.domain.model.audio.AudioChannelState
import com.malaramofficial.mobilestudio.domain.model.audio.AudioMixerState
import com.malaramofficial.mobilestudio.domain.model.audio.AudioSourceType
import com.malaramofficial.mobilestudio.domain.model.render.RenderPlan
import com.malaramofficial.mobilestudio.domain.model.scene.ChromaKeyConfig
import com.malaramofficial.mobilestudio.domain.model.scene.Crop
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.Source
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.scene.Transform
import com.malaramofficial.mobilestudio.domain.model.scene.Transition
import com.malaramofficial.mobilestudio.domain.model.source.SourceConfig
import com.malaramofficial.mobilestudio.domain.model.state.StudioAppState
import com.malaramofficial.mobilestudio.engine.camera.CameraSourceEngine
import com.malaramofficial.mobilestudio.engine.camera.CameraState
import com.malaramofficial.mobilestudio.engine.camera.LensFacing
import com.malaramofficial.mobilestudio.engine.gpu.StudioRenderPipeline
import com.malaramofficial.mobilestudio.engine.gpu.VisualTextureSourceManager
import com.malaramofficial.mobilestudio.engine.gpu.MediaTextureSourceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * Coordinates UI state with the domain [SceneManager], [SourceManager],
 * GPU Compositor [StudioRenderPipeline], and [CameraSourceEngine].
 */
class StudioViewModel(
    val sceneManager: SceneManager,
    val sourceManager: SourceManager,
    val preferences: StudioPreferences,
    val permissionManager: PermissionManager,
    val renderPipeline: StudioRenderPipeline,
    val cameraSourceEngine: CameraSourceEngine,
    val visualTextureSourceManager: VisualTextureSourceManager,
    val mediaTextureSourceManager: MediaTextureSourceManager,
    private val appContext: Context,
    private val secureCredentialStore: SecureCredentialStore,
    private val broadcastController: StudioBroadcastController,
    private val recordingController: RecordingController
) : ViewModel() {

    private val _activeError = MutableStateFlow<AppError?>(null)
    val activeError: StateFlow<AppError?> = _activeError.asStateFlow()

    private val _liveBitrateKbps = MutableStateFlow(6000)
    val liveBitrateKbps: StateFlow<Int> = _liveBitrateKbps.asStateFlow()

    val cameraState: StateFlow<CameraState> = cameraSourceEngine.cameraState

    private val _audioState = MutableStateFlow(
        AudioMixerState(
            channels = listOf(
                AudioChannelState(id = "ch_mic", name = "Microphone", type = AudioSourceType.MICROPHONE),
                AudioChannelState(id = "ch_sys", name = "System Audio", type = AudioSourceType.SYSTEM_AUDIO),
                AudioChannelState(id = "ch_media", name = "Media Player", type = AudioSourceType.MEDIA_TRACK)
            )
        )
    )

    val previewRenderPlan: StateFlow<RenderPlan?> = sceneManager.previewRenderPlan
    val programRenderPlan: StateFlow<RenderPlan?> = sceneManager.programRenderPlan

    val appState: StateFlow<StudioAppState> = combine(
        combine(sceneManager.scenes, sceneManager.previewScene, sceneManager.programScene) { scenes, preview, program ->
            Triple(scenes, preview, program)
        },
        sceneManager.activeTransition,
        permissionManager.permissionsState,
        combine(_audioState, _activeError, broadcastController.state, recordingController.recordingState) { audio, error, streamState, recordingState ->
            Quadruple(audio, error, streamState, recordingState)
        }
    ) { sceneTriple, transition, perms, liveState ->
        val (scenes, previewScene, programScene) = sceneTriple
        val (audio, error, streamState, recordingState) = liveState
        StudioAppState(
            scenes = scenes,
            previewSceneId = previewScene?.id,
            programSceneId = programScene?.id,
            streamState = streamState,
            recordingState = recordingState,
            transition = transition,
            permissions = perms,
            audioState = audio,
            activeError = error
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        StudioAppState()
    )

    init {
        initializeDefaultScenesIfEmpty()
        observeProgramRenderPlan()
    }

    private fun observeProgramRenderPlan() {
        viewModelScope.launch {
            sceneManager.programScene.collect { scene ->
                renderPipeline.updateRenderPlan(scene?.let { com.malaramofficial.mobilestudio.domain.model.render.RenderPlanBuilder.build(it) })
                visualTextureSourceManager.syncScene(scene)
                mediaTextureSourceManager.syncScene(scene)
            }
        }
    }

    private fun initializeDefaultScenesIfEmpty() {
        viewModelScope.launch {
            sceneManager.scenes.collect { scenes ->
                if (scenes.isEmpty()) {
                    createDefaultStudioSetup()
                }
            }
        }
    }

    private suspend fun createDefaultStudioSetup() {
        val scene1Result = sceneManager.createScene("Main Camera")
        if (scene1Result is AppResult.Success) {
            sourceManager.addSource(
                sceneId = scene1Result.data.id,
                name = "Camera Layer",
                type = SourceType.CAMERA,
                config = SourceConfig.Camera(lensFacing = SourceConfig.Camera.LensFacing.BACK),
                transform = Transform(x = 0f, y = 0f, width = 1080f, height = 1920f)
            )
        }

        val scene2Result = sceneManager.createScene("Screen Share + PIP")
        if (scene2Result is AppResult.Success) {
            sourceManager.addSource(
                sceneId = scene2Result.data.id,
                name = "Screen Capture",
                type = SourceType.SCREEN,
                config = SourceConfig.Screen(),
                transform = Transform(x = -1166f, y = 0f, width = 3412f, height = 1920f)
            )
            sourceManager.addSource(
                sceneId = scene2Result.data.id,
                name = "Facecam PIP",
                type = SourceType.CAMERA,
                config = SourceConfig.Camera(lensFacing = SourceConfig.Camera.LensFacing.FRONT),
                transform = Transform(x = 660f, y = 1450f, width = 360f, height = 360f)
            )
        }

        val scene3Result = sceneManager.createScene("BRB Intermission")
        if (scene3Result is AppResult.Success) {
            sourceManager.addSource(
                sceneId = scene3Result.data.id,
                name = "Notice Text",
                type = SourceType.TEXT,
                config = SourceConfig.Text(text = "Be Right Back!"),
                transform = Transform(x = 90f, y = 850f, width = 900f, height = 160f)
            )
        }
    }

    // --- Live Broadcast Operations ---

    fun startLive(streamKey: String, bitrateKbps: Int = _liveBitrateKbps.value) {
        val cleanKey = streamKey.trim()
        if (cleanKey.isBlank()) {
            _activeError.value = AppError.Camera.ConfigurationFailed(
                IllegalArgumentException("YouTube stream key is required")
            )
            return
        }

        viewModelScope.launch {
            val config = preferences.streamConfigFlow.first()
            secureCredentialStore.saveStreamKey("youtube", cleanKey)
            val endpoint = config.serverUrl.trimEnd('/') + "/" + cleanKey
            try {
                ContextCompat.startForegroundService(
                    appContext,
                    Intent(appContext, StudioService::class.java)
                        .setAction(StudioService.ACTION_START_LIVE)
                        .putExtra(StudioService.EXTRA_RTMP_ENDPOINT, endpoint)
                        .putExtra(StudioService.EXTRA_VIDEO_BITRATE_KBPS, bitrateKbps.coerceIn(500, 12000))
                )
            } catch (t: Throwable) {
                _activeError.value = AppError.Camera.ConfigurationFailed(
                    IllegalStateException(t.message ?: "Unable to start live stream", t)
                )
            }
        }
    }

    fun setLiveBitrateKbps(bitrateKbps: Int) {
        val safeBitrate = bitrateKbps.coerceIn(500, 12000)
        if (!broadcastController.isLive()) return
        if (broadcastController.setVideoBitrateKbps(safeBitrate)) {
            _liveBitrateKbps.value = safeBitrate
        } else {
            _activeError.value = AppError.Camera.ConfigurationFailed(
                IllegalStateException("This device encoder could not change bitrate while live.")
            )
        }
    }

    fun stopLive() {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, StudioService::class.java)
                .setAction(StudioService.ACTION_STOP_LIVE)
        )
    }

    fun getSavedStreamKey(): String = secureCredentialStore.getStreamKey("youtube")

    fun clearSavedStreamKey() {
        secureCredentialStore.clearCredentials("youtube")
    }

    // --- Local Recording Operations ---

    fun startRecording() {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, StudioService::class.java)
                .setAction(StudioService.ACTION_START_RECORDING)
        )
    }

    fun stopRecording() {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, StudioService::class.java)
                .setAction(StudioService.ACTION_STOP_RECORDING)
        )
    }

    fun pauseRecording() {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, StudioService::class.java)
                .setAction(StudioService.ACTION_PAUSE_RECORDING)
        )
    }

    fun resumeRecording() {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, StudioService::class.java)
                .setAction(StudioService.ACTION_RESUME_RECORDING)
        )
    }

    // --- Camera Operations ---

    fun startCamera(lifecycleOwner: LifecycleOwner, lens: LensFacing = LensFacing.BACK) {
        val inputSurface = renderPipeline.cameraInputSurface
        if (inputSurface == null) {
            _activeError.value = AppError.Camera.ConfigurationFailed(
                IllegalStateException("OpenGL CameraInputSurface is not yet ready")
            )
            return
        }

        if (!permissionManager.permissionsState.value.hasCameraPermission) {
            _activeError.value = AppError.Permission.CameraDenied()
            return
        }

        cameraSourceEngine.startCamera(
            lifecycleOwner = lifecycleOwner,
            inputSurface = inputSurface,
            lens = lens
        )
    }

    fun switchCameraLens(lifecycleOwner: LifecycleOwner) {
        val inputSurface = renderPipeline.cameraInputSurface ?: return
        cameraSourceEngine.switchLens(lifecycleOwner, inputSurface)
    }

    fun stopCamera() {
        cameraSourceEngine.stopCamera()
    }

    // --- Scene Operations ---

    fun createScene(name: String, sourceType: SourceType = SourceType.CAMERA) {
        viewModelScope.launch {
            val result = sceneManager.createScene(name)
            if (result is AppResult.Success) {
                val sourceName = when (sourceType) {
                    SourceType.CAMERA -> "Camera"
                    SourceType.SCREEN -> "Screen"
                    SourceType.IMAGE -> "Image / Logo"
                    SourceType.TEXT -> "Text"
                    SourceType.MEDIA -> "Video / Media"
                    SourceType.BROWSER -> "Browser"
                }

                val config = when (sourceType) {
                    SourceType.CAMERA -> SourceConfig.Camera(
                        lensFacing = SourceConfig.Camera.LensFacing.BACK
                    )
                    SourceType.SCREEN -> SourceConfig.Screen()
                    SourceType.IMAGE -> SourceConfig.Image()
                    SourceType.TEXT -> SourceConfig.Text(text = "Live Studio Stream")
                    SourceType.MEDIA -> SourceConfig.Media()
                    SourceType.BROWSER -> SourceConfig.Browser()
                }

                val transform = when (sourceType) {
                    SourceType.CAMERA,
                    SourceType.SCREEN -> Transform(x = 0f, y = 0f, width = 1080f, height = 1920f)
                    SourceType.IMAGE,
                    SourceType.MEDIA,
                    SourceType.BROWSER -> Transform(x = 0f, y = 0f, width = 1080f, height = 1920f)
                    SourceType.TEXT -> Transform(x = 90f, y = 820f, width = 900f, height = 180f)
                }

                val sourceResult = sourceManager.addSource(
                    sceneId = result.data.id,
                    name = sourceName,
                    type = sourceType,
                    config = config,
                    transform = transform
                )
                handleResult(sourceResult)
            } else {
                handleResult(result)
            }
        }
    }

    fun renameScene(sceneId: String, newName: String) {
        viewModelScope.launch {
            val result = sceneManager.renameScene(sceneId, newName)
            handleResult(result)
        }
    }

    fun deleteScene(sceneId: String) {
        viewModelScope.launch {
            val result = sceneManager.deleteScene(sceneId)
            handleResult(result)
        }
    }

    fun duplicateScene(sceneId: String) {
        viewModelScope.launch {
            val result = sceneManager.duplicateScene(sceneId)
            handleResult(result)
        }
    }

    fun selectPreviewScene(sceneId: String) {
        viewModelScope.launch {
            val result = sceneManager.setPreviewScene(sceneId)
            handleResult(result)
        }
    }

    fun selectProgramScene(sceneId: String) {
        viewModelScope.launch {
            val result = sceneManager.setProgramScene(sceneId)
            handleResult(result)
        }
    }

    fun executeCut() {
        viewModelScope.launch {
            val result = sceneManager.cut()
            handleResult(result)
        }
    }

    fun executeTransition(transition: Transition = Transition.DefaultFade) {
        viewModelScope.launch {
            val result = sceneManager.transition(transition)
            handleResult(result)
        }
    }

    // --- Source Operations ---

    fun addSource(
        sceneId: String,
        name: String,
        type: SourceType,
        config: SourceConfig? = null,
        transform: Transform = Transform()
    ) {
        viewModelScope.launch {
            val result = sourceManager.addSource(sceneId, name, type, config, transform)
            handleResult(result)
        }
    }

    fun removeSource(sceneId: String, sourceId: String) {
        viewModelScope.launch {
            val result = sourceManager.removeSource(sceneId, sourceId)
            handleResult(result)
        }
    }

    fun duplicateSource(sceneId: String, sourceId: String) {
        viewModelScope.launch {
            val result = sourceManager.duplicateSource(sceneId, sourceId)
            handleResult(result)
        }
    }

    fun renameSource(sceneId: String, sourceId: String, newName: String) {
        viewModelScope.launch {
            val result = sourceManager.renameSource(sceneId, sourceId, newName)
            handleResult(result)
        }
    }

    fun reorderSource(sceneId: String, sourceId: String, action: LayerOrderAction) {
        viewModelScope.launch {
            val result = sourceManager.reorderSource(sceneId, sourceId, action)
            handleResult(result)
        }
    }

    fun toggleSourceVisibility(sceneId: String, sourceId: String) {
        val scene = sceneManager.getScene(sceneId) ?: return
        val source = scene.findSource(sourceId) ?: return
        viewModelScope.launch {
            val result = sourceManager.setSourceVisibility(sceneId, sourceId, !source.visible)
            handleResult(result)
        }
    }

    fun toggleSourceLock(sceneId: String, sourceId: String) {
        val scene = sceneManager.getScene(sceneId) ?: return
        val source = scene.findSource(sourceId) ?: return
        viewModelScope.launch {
            val result = sourceManager.setSourceLock(sceneId, sourceId, !source.locked)
            handleResult(result)
        }
    }

    fun updateTransform(sceneId: String, sourceId: String, transform: Transform) {
        viewModelScope.launch {
            val result = sourceManager.updateTransform(sceneId, sourceId, transform)
            handleResult(result)
        }
    }

    fun updateCrop(sceneId: String, sourceId: String, crop: Crop) {
        viewModelScope.launch {
            val result = sourceManager.updateCrop(sceneId, sourceId, crop)
            handleResult(result)
        }
    }

    fun updateOpacity(sceneId: String, sourceId: String, opacity: Float) {
        viewModelScope.launch {
            val result = sourceManager.updateOpacity(sceneId, sourceId, opacity)
            handleResult(result)
        }
    }

    fun updateChromaKey(sceneId: String, sourceId: String, chromaKey: ChromaKeyConfig?) {
        viewModelScope.launch {
            val result = sourceManager.updateChromaKey(sceneId, sourceId, chromaKey)
            handleResult(result)
        }
    }

    fun prepareScreenShare() {
        val screenScene = appState.value.scenes.firstOrNull { scene ->
            scene.sources.any { it.type == SourceType.SCREEN && it.visible }
        }
        if (screenScene == null) {
            _activeError.value = AppError.Camera.ConfigurationFailed(
                IllegalStateException("No visible Screen Capture source is configured")
            )
            return
        }
        if (appState.value.previewSceneId != screenScene.id) {
            selectPreviewScene(screenScene.id)
        }
        if (appState.value.programSceneId != screenScene.id) {
            selectProgramScene(screenScene.id)
        }
    }

    fun showScreenCaptureError(message: String) {
        _activeError.value = AppError.Camera.ConfigurationFailed(IllegalStateException(message))
    }

    fun dismissError() {
        _activeError.value = null
    }

    fun refreshPermissions() {
        permissionManager.refreshPermissions()
    }

    private fun <T> handleResult(result: AppResult<T>) {
        if (result is AppResult.Error) {
            _activeError.value = result.error
        }
    }

    class Factory(
        private val sceneManager: SceneManager,
        private val sourceManager: SourceManager,
        private val preferences: StudioPreferences,
        private val permissionManager: PermissionManager,
        private val renderPipeline: StudioRenderPipeline,
        private val cameraSourceEngine: CameraSourceEngine,
        private val visualTextureSourceManager: VisualTextureSourceManager,
        private val mediaTextureSourceManager: MediaTextureSourceManager,
        private val appContext: Context,
        private val secureCredentialStore: SecureCredentialStore,
        private val broadcastController: StudioBroadcastController,
        private val recordingController: RecordingController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StudioViewModel(
                sceneManager = sceneManager,
                sourceManager = sourceManager,
                preferences = preferences,
                permissionManager = permissionManager,
                renderPipeline = renderPipeline,
                cameraSourceEngine = cameraSourceEngine,
                visualTextureSourceManager = visualTextureSourceManager,
                mediaTextureSourceManager = mediaTextureSourceManager,
                appContext = appContext,
                secureCredentialStore = secureCredentialStore,
                broadcastController = broadcastController,
                recordingController = recordingController
            ) as T
        }
    }
}
