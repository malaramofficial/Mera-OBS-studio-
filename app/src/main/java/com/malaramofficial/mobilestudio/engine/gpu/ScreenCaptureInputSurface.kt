package com.malaramofficial.mobilestudio.engine.gpu

import android.graphics.SurfaceTexture
import android.opengl.Matrix
import android.view.Surface

/**
 * Zero-copy MediaProjection input backed by a GL_TEXTURE_EXTERNAL_OES texture.
 * Must be created and consumed on the Studio GL/EGL context.
 */
class ScreenCaptureInputSurface(
    val textureId: Int = GlTexture.createExternalOesTexture(),
    width: Int,
    height: Int
) {
    val surfaceTexture = SurfaceTexture(textureId)
    val surface = Surface(surfaceTexture)
    val texMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    private var onFrameAvailableCallback: (() -> Unit)? = null

    init {
        require(width > 0 && height > 0)
        surfaceTexture.setDefaultBufferSize(width, height)
        surfaceTexture.setOnFrameAvailableListener { onFrameAvailableCallback?.invoke() }
    }

    fun setOnFrameAvailableListener(listener: () -> Unit) {
        onFrameAvailableCallback = listener
    }

    fun updateTexImage(): Long {
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(texMatrix)
        return surfaceTexture.timestamp
    }

    fun release() {
        surface.release()
        surfaceTexture.release()
        GlTexture.deleteTexture(textureId)
    }
}
