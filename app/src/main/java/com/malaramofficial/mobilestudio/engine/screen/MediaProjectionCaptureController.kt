package com.malaramofficial.mobilestudio.engine.screen

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.Surface

/**
 * Owns a user-approved Android MediaProjection session and routes display
 * frames into a caller-provided Surface. No fake capture state is exposed.
 */
class MediaProjectionCaptureController(
    context: Context,
    private val onStopped: () -> Unit = {}
) {
    private val projectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    fun start(
        resultCode: Int,
        resultData: Intent,
        surface: Surface,
        width: Int,
        height: Int,
        densityDpi: Int
    ) {
        require(width > 0 && height > 0)
        require(densityDpi > 0)

        stop()

        val mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
            ?: error("Invalid MediaProjection permission result")

        projection = mediaProjection
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                virtualDisplay?.release()
                virtualDisplay = null
                projection = null
                onStopped()
            }
        }, null)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "MalaramStudioScreenCapture",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        ) ?: error("Unable to create MediaProjection VirtualDisplay")
    }

    fun stop() {
        virtualDisplay?.release()
        virtualDisplay = null
        projection?.stop()
        projection = null
    }

    fun isRunning(): Boolean = projection != null && virtualDisplay != null
}
