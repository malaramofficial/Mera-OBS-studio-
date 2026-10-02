package com.malaramofficial.mobilestudio.engine.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.malaramofficial.mobilestudio.core.model.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Production Android MediaProjection Screen Capture Engine.
 * Delivers device display frames directly to a zero-copy [ScreenInputSurface]
 * optimized for YouTube Shorts Live (Vertical 9:16) and gaming broadcasts.
 */
class ScreenCaptureEngine(
    private val context: Context
) {
    private val mediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private val _screenState = MutableStateFlow<ScreenState>(ScreenState.Idle)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection stopped by system")
            stopScreenCapture()
        }
    }

    /**
     * Starts screen capture session using granted MediaProjection Intent result.
     * [isVerticalShorts]: If true, stream canvas is oriented vertically (9:16) for YouTube Shorts feed.
     */
    fun startScreenCapture(
        resultCode: Int,
        resultData: Intent,
        inputSurface: ScreenInputSurface,
        isVerticalShorts: Boolean = true
    ) {
        if (resultCode != Activity.RESULT_OK) {
            _screenState.value = ScreenState.Error(
                AppError.Permission.MediaProjectionDenied()
            )
            return
        }

        try {
            stopScreenCapture()

            val projection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                _screenState.value = ScreenState.Error(
                    AppError.Permission.MediaProjectionDenied()
                )
                return
            }
            mediaProjection = projection
            projection.registerCallback(projectionCallback, mainHandler)

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            // When in Vertical Shorts mode, target vertical resolution (e.g. 1080x1920)
            // Capture the complete current display aspect ratio. The compositor
            // is responsible for fitting this source into the vertical output.
            // Do not force a portrait buffer here: that was the reason landscape
            // gameplay could arrive already cropped/squeezed.
            val sourceWidth = metrics.widthPixels.coerceAtLeast(1)
            val sourceHeight = metrics.heightPixels.coerceAtLeast(1)
            val maxDimension = 1920f
            val scale = minOf(1f, maxDimension / maxOf(sourceWidth, sourceHeight).toFloat())
            val captureWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
            val captureHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)

            inputSurface.setDefaultBufferSize(captureWidth, captureHeight)

            val display = projection.createVirtualDisplay(
                "MobileStudioShortsCapture",
                captureWidth,
                captureHeight,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface.surface,
                null,
                mainHandler
            )
            virtualDisplay = display

            _screenState.value = ScreenState.Active(
                width = captureWidth,
                height = captureHeight,
                dpi = metrics.densityDpi,
                isVerticalShorts = isVerticalShorts
            )
            Log.i(TAG, "Screen capture started: ${captureWidth}x${captureHeight}, verticalShorts=$isVerticalShorts")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen capture", e)
            _screenState.value = ScreenState.Error(
                AppError.Camera.ConfigurationFailed(e)
            )
        }
    }

    fun stopScreenCapture() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null

            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
            mediaProjection = null

            _screenState.value = ScreenState.Idle
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping screen capture", e)
        }
    }

    fun release() {
        stopScreenCapture()
    }

    companion object {
        private const val TAG = "ScreenCaptureEngine"
    }
}
