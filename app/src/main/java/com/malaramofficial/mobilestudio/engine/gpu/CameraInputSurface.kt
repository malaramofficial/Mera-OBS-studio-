package com.malaramofficial.mobilestudio.engine.gpu

import android.graphics.SurfaceTexture
import android.opengl.Matrix
import android.view.Surface

/**
 * Manages zero-copy Camera hardware surface delivery into an OpenGL ES external OES texture.
 * Connects directly to CameraX Preview without CPU buffer copies.
 */
class CameraInputSurface(
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

    /**
     * Updates the texture image to the most recent frame from the camera stream,
     * and extracts the 4x4 texture coordinate transformation matrix.
     */
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
