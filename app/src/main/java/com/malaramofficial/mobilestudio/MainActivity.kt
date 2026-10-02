package com.malaramofficial.mobilestudio

import android.Manifest
import androidx.media3.common.util.UnstableApi
import android.os.Bundle
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.core.content.ContextCompat
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.source.SourceConfig
import com.malaramofficial.mobilestudio.service.StudioService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.malaramofficial.mobilestudio.ui.StudioScreen
import com.malaramofficial.mobilestudio.ui.StudioViewModel
import com.malaramofficial.mobilestudio.ui.theme.MalaramStudioTheme

/**
 * Main entrance Activity for Malaram Mobile Studio.
 * Hosts the studio workspace shell with edge-to-edge Compose rendering,
 * manages camera permissions, and coordinates hardware lifecycle.
 */
@UnstableApi
class MainActivity : ComponentActivity() {

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult

        var scene = viewModel.sceneManager.getScene(viewModel.appState.value.previewSceneId ?: "")
        var source = scene?.sources?.firstOrNull { it.type == SourceType.SCREEN && it.visible }

        if (source == null) {
            scene = viewModel.appState.value.scenes.firstOrNull { candidate ->
                candidate.sources.any { it.type == SourceType.SCREEN && it.visible }
            }
            if (scene != null) {
                viewModel.selectPreviewScene(scene.id)
                viewModel.executeCut()
                source = scene.sources.firstOrNull { it.type == SourceType.SCREEN && it.visible }
            }
        }

        if (scene == null || source == null) {
            viewModel.showScreenCaptureError("पहले Scene में Screen Capture source जोड़ें")
            return@registerForActivityResult
        }

        // Capture the actual display aspect ratio. The compositor will fit it
        // into the 9:16 program canvas without cutting the gameplay.
        val metrics = resources.displayMetrics
        val sourceWidth = metrics.widthPixels.coerceAtLeast(1)
        val sourceHeight = metrics.heightPixels.coerceAtLeast(1)
        val maxDimension = 1920f
        val scale = minOf(1f, maxDimension / maxOf(sourceWidth, sourceHeight).toFloat())
        val width = (sourceWidth * scale).toInt().coerceAtLeast(1)
        val height = (sourceHeight * scale).toInt().coerceAtLeast(1)
        val density = metrics.densityDpi

        ContextCompat.startForegroundService(
            this,
            Intent(this, StudioService::class.java)
                .setAction(StudioService.ACTION_START_SCREEN_CAPTURE)
                .putExtra(StudioService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(StudioService.EXTRA_RESULT_DATA, result.data)
                .putExtra(StudioService.EXTRA_SOURCE_ID, source.id)
                .putExtra(StudioService.EXTRA_CAPTURE_WIDTH, width)
                .putExtra(StudioService.EXTRA_CAPTURE_HEIGHT, height)
                .putExtra(StudioService.EXTRA_DENSITY_DPI, density)
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.refreshPermissions()
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        if (cameraGranted) {
            viewModel.startCamera(this)
        }
    }

    private val viewModel: StudioViewModel by viewModels {
        val app = application as MalaramStudioApplication
        StudioViewModel.Factory(
            sceneManager = app.sceneManager,
            sourceManager = app.sourceManager,
            preferences = app.studioPreferences,
            permissionManager = app.permissionManager,
            renderPipeline = app.renderPipeline,
            cameraSourceEngine = app.cameraSourceEngine,
            visualTextureSourceManager = app.visualTextureSourceManager,
            mediaTextureSourceManager = app.mediaTextureSourceManager,
            appContext = applicationContext,
            secureCredentialStore = app.secureCredentialStore,
            broadcastController = app.broadcastController,
            recordingController = app.recordingController
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MalaramStudioTheme {
                StudioScreen(viewModel = viewModel, onStartScreenShare = { startScreenShare() })

                LaunchedEffect(Unit) {
                    // Check and request camera & audio permissions on launch
                    if (!viewModel.permissionManager.permissionsState.value.hasCameraPermission) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO
                            )
                        )
                    } else {
                        viewModel.startCamera(this@MainActivity)
                    }
                }
            }
        }
    }

    private fun startScreenShare() {
        // Android requires the user consent intent before the mediaProjection
        // foreground service is started.
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
        if (viewModel.permissionManager.permissionsState.value.hasCameraPermission) {
            viewModel.startCamera(this)
        }
    }

    override fun onDestroy() {
        viewModel.stopCamera()
        super.onDestroy()
    }

}
