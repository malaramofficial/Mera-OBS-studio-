package com.malaramofficial.mobilestudio.engine.gpu

import android.opengl.EGLSurface
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.graphics.SurfaceTexture
import com.malaramofficial.mobilestudio.domain.model.render.RenderPlan
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dedicated OpenGL ES rendering thread and pipeline orchestrator.
 * Manages EGL lifecycle, frame synchronization, and renders [RenderPlan]s
 * to the display surface without blocking the UI thread.
 */
class StudioRenderPipeline {

    private val glThread = HandlerThread("StudioGLCompositor").apply { start() }
    private val glHandler = Handler(glThread.looper)

    private var eglCore: EglCore? = null
    private var displaySurface: EGLSurface? = null
    private val programOutputSurfaces = mutableMapOf<String, EGLSurface>()
    private val mediaInputs = mutableMapOf<String, MediaInput>()
    private var compositor: GpuCompositor? = null

    var cameraInputSurface: CameraInputSurface? = null
        private set

    var screenCaptureInputSurface: ScreenCaptureInputSurface? = null
        private set

    private var screenCaptureSourceId: String? = null

    private var activeRenderPlan: RenderPlan? = null
    private var surfaceWidth: Int = 1080
    private var surfaceHeight: Int = 1920
    private var outputSurfaceWidth: Int = 1080
    private var outputSurfaceHeight: Int = 1920

    private var isInitialized = false

    // Camera callbacks can arrive faster than the GL thread can draw. Keep at
    // most one render task queued and collapse intermediate frame requests.
    private val renderScheduled = AtomicBoolean(false)
    private val renderDirty = AtomicBoolean(false)

    fun init(onReady: (CameraInputSurface) -> Unit) {
        glHandler.post {
            try {
                val core = EglCore()
                eglCore = core

                val offscreen = core.createOffscreenSurface(1, 1)
                core.makeCurrent(offscreen)

                val comp = GpuCompositor()
                comp.initializeGl()
                compositor = comp

                val camInput = CameraInputSurface()
                camInput.setOnFrameAvailableListener {
                    requestRender()
                }
                cameraInputSurface = camInput

                isInitialized = true
                onReady(camInput)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed or skipped OpenGL initialization (expected in headless tests): ${t.message}")
            }
        }
    }

    /**
     * Attaches an on-screen target surface (e.g. from SurfaceView or TextureView).
     */
    fun onSurfaceCreated(surface: Surface, width: Int, height: Int) {
        glHandler.post {
            val core = eglCore ?: return@post
            if (displaySurface != null) {
                core.releaseSurface(displaySurface)
                displaySurface = null
            }
            try {
                displaySurface = core.createWindowSurface(surface)
                core.makeCurrent(displaySurface)
                surfaceWidth = width
                surfaceHeight = height
                requestRender()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create window surface", e)
            }
        }
    }

    fun onSurfaceSizeChanged(width: Int, height: Int) {
        glHandler.post {
            surfaceWidth = width
            surfaceHeight = height
            requestRender()
        }
    }

    fun onSurfaceDestroyed() {
        glHandler.post {
            val core = eglCore ?: return@post
            if (displaySurface != null) {
                core.makeNothingCurrent()
                core.releaseSurface(displaySurface)
                displaySurface = null
            }
        }
    }

    /**
     * Attaches the real program output target supplied by MediaCodec.
     * This target is independent from the Compose preview SurfaceView, so
     * encoding can continue while the Activity is backgrounded.
     */
    fun attachProgramOutputSurface(surface: Surface, width: Int, height: Int) {
        glHandler.post {
            val core = eglCore ?: return@post
            programOutputSurfaces[DEFAULT_PROGRAM_OUTPUT_KEY]?.let { core.releaseSurface(it) }
            programOutputSurfaces[DEFAULT_PROGRAM_OUTPUT_KEY] = core.createWindowSurface(surface) ?: return@post
            outputSurfaceWidth = width.coerceAtLeast(1)
            outputSurfaceHeight = height.coerceAtLeast(1)
            requestRender()
        }
    }

    fun detachProgramOutputSurface() {
        detachProgramOutputSurface(DEFAULT_PROGRAM_OUTPUT_KEY)
    }

    fun attachProgramOutputSurface(
        key: String,
        surface: Surface,
        width: Int,
        height: Int
    ) {
        require(key.isNotBlank()) { "Program output key is required" }
        glHandler.post {
            val core = eglCore ?: return@post
            programOutputSurfaces[key]?.let { core.releaseSurface(it) }
            programOutputSurfaces[key] = core.createWindowSurface(surface) ?: return@post
            outputSurfaceWidth = width.coerceAtLeast(1)
            outputSurfaceHeight = height.coerceAtLeast(1)
            requestRender()
        }
    }

    fun detachProgramOutputSurface(key: String) {
        require(key.isNotBlank()) { "Program output key is required" }
        glHandler.post {
            val core = eglCore ?: return@post
            programOutputSurfaces.remove(key)?.let { core.releaseSurface(it) }
            requestRender()
        }
    }

    /**
     * Updates the active scene compositing blueprint and triggers a re-render.
     */
    fun updateRenderPlan(plan: RenderPlan?) {
        glHandler.post {
            activeRenderPlan = plan
            requestRender()
        }
    }

    /**
     * Binds an external OES texture to a source on the dedicated GL thread.
     * The texture must be created in the same EGL context.
     */
    fun createScreenCaptureInput(
        sourceId: String,
        width: Int,
        height: Int,
        onReady: (ScreenCaptureInputSurface) -> Unit
    ) {
        glHandler.post {
            if (!isInitialized) return@post
            screenCaptureInputSurface?.release()
            val input = ScreenCaptureInputSurface(width = width, height = height)
            input.setOnFrameAvailableListener { requestRender() }
            screenCaptureInputSurface = input
            screenCaptureSourceId = sourceId
            compositor?.bindExternalTexture(sourceId, input.textureId)
            onReady(input)
            requestRender()
        }
    }

    fun releaseScreenCaptureInputForAnyScreenSource() {
        glHandler.post {
            screenCaptureInputSurface?.release()
            screenCaptureInputSurface = null
            screenCaptureSourceId = null
            requestRender()
        }
    }

    fun releaseScreenCaptureInput(sourceId: String) {
        glHandler.post {
            compositor?.unbindExternalTexture(sourceId)
            screenCaptureInputSurface?.release()
            screenCaptureInputSurface = null
            screenCaptureSourceId = null
            requestRender()
        }
    }

    /**
     * Creates an OES Surface for a video/media player. Frames go directly into GL.
     */
    fun createMediaInputSurface(
        sourceId: String,
        width: Int = 1920,
        height: Int = 1080,
        onReady: (Surface) -> Unit
    ) {
        require(sourceId.isNotBlank()) { "Media source id is required" }
        glHandler.post {
            if (!isInitialized) return@post
            mediaInputs.remove(sourceId)?.release()
            val textureId = createOesTexture()
            val texture = SurfaceTexture(textureId).apply {
                setDefaultBufferSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
                setOnFrameAvailableListener({ requestRender() }, glHandler)
            }
            val surface = Surface(texture)
            mediaInputs[sourceId] = MediaInput(textureId, texture, surface)
            compositor?.bindExternalTexture(sourceId, textureId)
            onReady(surface)
            requestRender()
        }
    }

    fun releaseMediaInput(sourceId: String) {
        glHandler.post {
            compositor?.unbindExternalTexture(sourceId)
            mediaInputs.remove(sourceId)?.release()
            requestRender()
        }
    }

    fun bindExternalTexture(sourceId: String, textureId: Int) {
        glHandler.post {
            compositor?.bindExternalTexture(sourceId, textureId)
            requestRender()
        }
    }

    fun unbindExternalTexture(sourceId: String) {
        glHandler.post {
            compositor?.unbindExternalTexture(sourceId)
            requestRender()
        }
    }

    /**
     * Binds a GL_TEXTURE_2D texture to a source on the dedicated GL thread.
     */
    fun bindBitmap2D(sourceId: String, bitmap: Bitmap) {
        glHandler.post {
            try {
                compositor?.bindBitmap2D(sourceId, bitmap)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            requestRender()
        }
    }

    fun unbindBitmap2D(sourceId: String) {
        glHandler.post {
            compositor?.unbindBitmap2D(sourceId)
            requestRender()
        }
    }

    fun bindTexture2D(sourceId: String, textureId: Int) {
        glHandler.post {
            compositor?.bindTexture2D(sourceId, textureId)
            requestRender()
        }
    }

    fun unbindTexture2D(sourceId: String) {
        glHandler.post {
            compositor?.unbindTexture2D(sourceId)
            requestRender()
        }
    }

    /**
     * Executes a single OpenGL draw pass.
     *
     * Camera input is optional: scenes containing only image/text or other
     * externally-bound sources must still render.
     */
    fun requestRender() {
        renderDirty.set(true)
        if (!renderScheduled.compareAndSet(false, true)) return

        glHandler.post {
            try {
                // Consume the newest available camera/screen/media frame only.
                renderDirty.set(false)
                renderFrame()
            } finally {
                renderScheduled.set(false)
                // A request that arrived while drawing is rendered once, not
                // replayed as a backlog of stale frames.
                if (renderDirty.get()) requestRender()
            }
        }
    }

    private fun renderFrame() {
        val core = eglCore ?: return
        val comp = compositor ?: return
        val plan = activeRenderPlan ?: return
        val targetSurface = displaySurface
        if (targetSurface == null && programOutputSurfaces.isEmpty()) return

        val camSurface = cameraInputSurface
        if (camSurface != null) {
            try {
                camSurface.updateTexImage()
            } catch (_: Exception) {
                // Frame may not yet be ready or camera may be tearing down.
            }
        }

        screenCaptureInputSurface?.let {
            try {
                it.updateTexImage()
            } catch (_: Exception) {
                // Frame may not yet be ready or capture may be tearing down.
            }
        }

        mediaInputs.values.forEach { input ->
            try {
                input.texture.updateTexImage()
            } catch (_: Exception) {
                // No new media frame yet or player is tearing down.
            }
        }

        val screenSourceId = screenCaptureSourceId
        val screenTextureMatrix = screenCaptureInputSurface?.texMatrix
        val externalMatrices = if (screenSourceId != null && screenTextureMatrix != null) {
            mapOf(screenSourceId to screenTextureMatrix)
        } else {
            emptyMap()
        }
        val cameraTextureId = camSurface?.textureId
        val cameraTextureMatrix = camSurface?.texMatrix

        fun renderTo(target: EGLSurface, width: Int, height: Int) {
            core.makeCurrent(target)
            comp.render(
                renderPlan = plan,
                cameraTextureId = cameraTextureId,
                cameraTexMatrix = cameraTextureMatrix,
                externalTexMatrices = externalMatrices,
                viewportWidth = width,
                viewportHeight = height
            )
            core.swapBuffers(target)
        }

        targetSurface?.let { renderTo(it, surfaceWidth, surfaceHeight) }
        programOutputSurfaces.values.forEach { renderTo(it, outputSurfaceWidth, outputSurfaceHeight) }
    }

    fun release() {
        glHandler.post {
            cameraInputSurface?.release()
            cameraInputSurface = null

            screenCaptureInputSurface?.release()
            screenCaptureInputSurface = null
            screenCaptureSourceId = null

            mediaInputs.values.forEach { it.release() }
            mediaInputs.clear()

            compositor?.release()
            compositor = null

            if (displaySurface != null) {
                eglCore?.releaseSurface(displaySurface)
                displaySurface = null
            }

            programOutputSurfaces.values.forEach { eglCore?.releaseSurface(it) }
            programOutputSurfaces.clear()

            eglCore?.release()
            eglCore = null
            isInitialized = false
        }
        glThread.quitSafely()
    }

    private fun createOesTexture(): Int {
        val ids = IntArray(1)
        android.opengl.GLES20.glGenTextures(1, ids, 0)
        val id = ids[0]
        android.opengl.GLES20.glBindTexture(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
        android.opengl.GLES20.glTexParameteri(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, android.opengl.GLES20.GL_TEXTURE_MIN_FILTER, android.opengl.GLES20.GL_LINEAR)
        android.opengl.GLES20.glTexParameteri(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, android.opengl.GLES20.GL_TEXTURE_MAG_FILTER, android.opengl.GLES20.GL_LINEAR)
        android.opengl.GLES20.glTexParameteri(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, android.opengl.GLES20.GL_TEXTURE_WRAP_S, android.opengl.GLES20.GL_CLAMP_TO_EDGE)
        android.opengl.GLES20.glTexParameteri(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, android.opengl.GLES20.GL_TEXTURE_WRAP_T, android.opengl.GLES20.GL_CLAMP_TO_EDGE)
        android.opengl.GLES20.glBindTexture(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        return id
    }

    private data class MediaInput(
        val textureId: Int,
        val texture: SurfaceTexture,
        val surface: Surface
    ) {
        fun release() {
            surface.release()
            texture.release()
            android.opengl.GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
    }

    companion object {
        private const val TAG = "StudioRenderPipeline"
        private const val DEFAULT_PROGRAM_OUTPUT_KEY = "live"
    }
}
