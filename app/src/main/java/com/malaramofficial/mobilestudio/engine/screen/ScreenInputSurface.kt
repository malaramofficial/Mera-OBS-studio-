package com.malaramofficial.mobilestudio.engine.screen

import android.graphics.SurfaceTexture
import android.opengl.Matrix
import android.view.Surface
import com.malaramofficial.mobilestudio.engine.gpu.GlTexture

/**
 * Hardware surface receiver for Android MediaProjection virtual display.
 * Delivers screen frames with zero CPU copies into an OpenGL ES external OES texture.
 */
class ScreenInputSurface(
    val textureId: Int = GlTexture.createExternalOesTexture()
) {
    val surfaceTexture: SurfaceTexture = SurfaceTexture(textureId)
    val surface: Surface = Surface(surfaceTexture)

    val texMatrix = FloatArray(16).apply {
        Matrix.setIdentityM(this, 0)
    }

    private var onFrameAvailableCallback: (() -> Unit)? = null

    init {
        surfaceTexture.setOnFrameAvailableListener {
            onFrameAvailableCallback?.invoke()
        }
    }

    fun setOnFrameAvailableListener(listener: () -> Unit) {
        this.onFrameAvailableCallback = listener
    }

    fun updateTexImage(): Long {
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(texMatrix)
        return surfaceTexture.timestamp
    }

    fun setDefaultBufferSize(width: Int, height: Int) {
        surfaceTexture.setDefaultBufferSize(width, height)
    }

    fun release() {
        surface.release()
        surfaceTexture.release()
        GlTexture.deleteTexture(textureId)
    }
}
