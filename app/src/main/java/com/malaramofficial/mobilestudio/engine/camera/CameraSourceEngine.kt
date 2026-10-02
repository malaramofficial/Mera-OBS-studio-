package com.malaramofficial.mobilestudio.engine.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.malaramofficial.mobilestudio.core.model.AppError
import com.malaramofficial.mobilestudio.core.model.AppResult
import com.malaramofficial.mobilestudio.engine.gpu.CameraInputSurface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor

/**
 * CameraX hardware capture engine.
 * Delivers camera frames directly to a zero-copy [CameraInputSurface] (backed by SurfaceTexture / GL_TEXTURE_EXTERNAL_OES).
 */
class CameraSourceEngine(
    private val context: Context,
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
) {
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Idle)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: Camera? = null
    private var activePreview: Preview? = null
    private var currentLens: LensFacing = LensFacing.BACK

    var currentResolution: Size = Size(1920, 1080)
        private set

    /**
     * Initializes and binds the camera pipeline to the provided [CameraInputSurface].
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        inputSurface: CameraInputSurface,
        lens: LensFacing = LensFacing.BACK,
        targetWidth: Int = 1920,
        targetHeight: Int = 1080
    ) {
        _cameraState.value = CameraState.Starting
        currentLens = lens
        currentResolution = Size(targetWidth, targetHeight)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                bindCameraUseCases(lifecycleOwner, provider, inputSurface, lens, targetWidth, targetHeight)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CameraProvider", e)
                _cameraState.value = CameraState.Error(
                    AppError.Camera.ConfigurationFailed(e)
                )
            }
        }, mainExecutor)
    }

    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        provider: ProcessCameraProvider,
        inputSurface: CameraInputSurface,
        lens: LensFacing,
        targetWidth: Int,
        targetHeight: Int
    ) {
        try {
            provider.unbindAll()

            val selector = when (lens) {
                LensFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                LensFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            }

            if (!provider.hasCamera(selector)) {
                Log.w(TAG, "Selected camera lens $lens not available on this device")
                _cameraState.value = CameraState.Error(
                    AppError.Camera.Disconnected("No physical camera device matching $lens")
                )
                return
            }

            // Configure resolution preference
            val resolutionStrategy = ResolutionStrategy(
                Size(targetWidth, targetHeight),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            )
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(resolutionStrategy)
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()

            // Deliver frames directly to our OpenGL Surface (backed by SurfaceTexture)
            preview.setSurfaceProvider(mainExecutor) { request ->
                val surface = inputSurface.surface
                inputSurface.setDefaultBufferSize(request.resolution.width, request.resolution.height)

                request.provideSurface(surface, mainExecutor) { result ->
                    Log.d(TAG, "Surface result code: ${result.resultCode}")
                }
            }

            activeCamera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview
            )
            activePreview = preview

            val actualRes = preview.resolutionInfo?.resolution ?: Size(targetWidth, targetHeight)
            currentResolution = actualRes

            _cameraState.value = CameraState.Active(
                lensFacing = lens,
                resolutionWidth = actualRes.width,
                resolutionHeight = actualRes.height
            )
            Log.i(TAG, "Camera active: lens=$lens, resolution=${actualRes.width}x${actualRes.height}")
        } catch (e: Exception) {
            Log.e(TAG, "Error binding camera use cases", e)
            _cameraState.value = CameraState.Error(
                AppError.Camera.ConfigurationFailed(e)
            )
        }
    }

    /**
     * Toggles between front and back camera lenses.
     */
    fun switchLens(lifecycleOwner: LifecycleOwner, inputSurface: CameraInputSurface) {
        val nextLens = if (currentLens == LensFacing.BACK) LensFacing.FRONT else LensFacing.BACK
        startCamera(
            lifecycleOwner = lifecycleOwner,
            inputSurface = inputSurface,
            lens = nextLens,
            targetWidth = currentResolution.width,
            targetHeight = currentResolution.height
        )
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            activeCamera = null
            activePreview = null
            _cameraState.value = CameraState.Idle
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }

    fun release() {
        stopCamera()
        cameraProvider = null
    }

    companion object {
        private const val TAG = "CameraSourceEngine"
    }
}
