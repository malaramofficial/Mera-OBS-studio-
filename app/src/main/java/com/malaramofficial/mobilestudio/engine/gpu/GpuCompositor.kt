package com.malaramofficial.mobilestudio.engine.gpu

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.graphics.Bitmap
import android.opengl.Matrix
import com.malaramofficial.mobilestudio.domain.model.render.RenderPlan
import com.malaramofficial.mobilestudio.domain.model.render.RenderableLayer
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Production OpenGL ES 2.0/3.0 Compositor.
 * Renders an ordered stack of visual layers according to an immutable [RenderPlan].
 * Compatible with zero-copy hardware buffers (SurfaceTexture / GL_TEXTURE_EXTERNAL_OES).
 */
class GpuCompositor {

    private var oesShader: GlShader? = null
    private var standardShader: GlShader? = null

    private data class StandardLocations(
        val mvp: Int,
        val texMatrix: Int,
        val opacity: Int,
        val crop: Int,
        val texture: Int,
        val position: Int,
        val textureCoord: Int
    )

    private data class OesLocations(
        val mvp: Int,
        val texMatrix: Int,
        val opacity: Int,
        val crop: Int,
        val chromaEnabled: Int,
        val chromaColor: Int,
        val chromaSimilarity: Int,
        val chromaSmoothness: Int,
        val chromaSpill: Int,
        val texture: Int,
        val position: Int,
        val textureCoord: Int
    )

    private var standardLocations: StandardLocations? = null
    private var oesLocations: OesLocations? = null

    private val externalTextures = mutableMapOf<String, Int>()
    private val standardTextures = mutableMapOf<String, Int>()
    private val ownedStandardTextures = mutableSetOf<Int>()

    fun bindExternalTexture(sourceId: String, textureId: Int) {
        require(textureId > 0) { "textureId must be positive" }
        externalTextures[sourceId] = textureId
    }
    fun unbindExternalTexture(sourceId: String) { externalTextures.remove(sourceId) }
    fun bindBitmap2D(sourceId: String, bitmap: Bitmap) {
        require(!bitmap.isRecycled) { "bitmap must not be recycled" }

        val oldTexture = standardTextures.remove(sourceId)
        if (oldTexture != null) {
            GLES20.glDeleteTextures(1, intArrayOf(oldTexture), 0)
            ownedStandardTextures.remove(oldTexture)
        }

        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]
        require(textureId > 0) { "Failed to allocate GL texture" }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        try {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        } finally {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            if (!bitmap.isRecycled) bitmap.recycle()
        }

        standardTextures[sourceId] = textureId
        ownedStandardTextures.add(textureId)
    }

    fun unbindBitmap2D(sourceId: String) {
        val textureId = standardTextures.remove(sourceId) ?: return
        if (ownedStandardTextures.remove(textureId)) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
    }

    fun bindTexture2D(sourceId: String, textureId: Int) {
        require(textureId > 0) { "textureId must be positive" }
        standardTextures[sourceId] = textureId
    }
    fun unbindTexture2D(sourceId: String) { standardTextures.remove(sourceId) }
    fun clearTextureBindings() {
        if (ownedStandardTextures.isNotEmpty()) {
            val ids = ownedStandardTextures.toIntArray()
            GLES20.glDeleteTextures(ids.size, ids, 0)
            ownedStandardTextures.clear()
        }
        externalTextures.clear()
        standardTextures.clear()
    }

    // Quad geometry (2 triangles as triangle strip)
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    private val identityMatrix = FloatArray(16).apply {
        Matrix.setIdentityM(this, 0)
    }

    init {
        val quadVertices = floatArrayOf(
            -1.0f, -1.0f, 0.0f,
             1.0f, -1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f,
             1.0f,  1.0f, 0.0f
        )
        vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(quadVertices)
                position(0)
            }

        val texCoords = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(texCoords)
                position(0)
            }
    }

    fun initializeGl() {
        val oes = GlShader(GlShader.VERTEX_SHADER, GlShader.FRAGMENT_SHADER_OES)
        val standard = GlShader(GlShader.VERTEX_SHADER, GlShader.FRAGMENT_SHADER_2D)
        oesShader = oes
        standardShader = standard

        // Resolve shader handles once. glGet*Location can cross into the driver
        // and must not run repeatedly for every layer on every frame.
        val oesProgram = oes.programHandle
        oesLocations = OesLocations(
            mvp = GLES20.glGetUniformLocation(oesProgram, "uMVPMatrix"),
            texMatrix = GLES20.glGetUniformLocation(oesProgram, "uTexMatrix"),
            opacity = GLES20.glGetUniformLocation(oesProgram, "uOpacity"),
            crop = GLES20.glGetUniformLocation(oesProgram, "uCrop"),
            chromaEnabled = GLES20.glGetUniformLocation(oesProgram, "uChromaEnabled"),
            chromaColor = GLES20.glGetUniformLocation(oesProgram, "uChromaKeyColor"),
            chromaSimilarity = GLES20.glGetUniformLocation(oesProgram, "uChromaSimilarity"),
            chromaSmoothness = GLES20.glGetUniformLocation(oesProgram, "uChromaSmoothness"),
            chromaSpill = GLES20.glGetUniformLocation(oesProgram, "uChromaSpill"),
            texture = GLES20.glGetUniformLocation(oesProgram, "uTexture"),
            position = GLES20.glGetAttribLocation(oesProgram, "aPosition"),
            textureCoord = GLES20.glGetAttribLocation(oesProgram, "aTextureCoord")
        )

        val standardProgram = standard.programHandle
        standardLocations = StandardLocations(
            mvp = GLES20.glGetUniformLocation(standardProgram, "uMVPMatrix"),
            texMatrix = GLES20.glGetUniformLocation(standardProgram, "uTexMatrix"),
            opacity = GLES20.glGetUniformLocation(standardProgram, "uOpacity"),
            crop = GLES20.glGetUniformLocation(standardProgram, "uCrop"),
            texture = GLES20.glGetUniformLocation(standardProgram, "uTexture"),
            position = GLES20.glGetAttribLocation(standardProgram, "aPosition"),
            textureCoord = GLES20.glGetAttribLocation(standardProgram, "aTextureCoord")
        )
    }

    /**
     * Renders all visible layers of the [renderPlan] onto the currently bound OpenGL framebuffer or surface.
     */
    fun render(
        renderPlan: RenderPlan,
        cameraTextureId: Int? = null,
        cameraTexMatrix: FloatArray? = null,
        externalTexMatrices: Map<String, FloatArray> = emptyMap(),
        viewportWidth: Int = renderPlan.canvasWidth,
        viewportHeight: Int = renderPlan.canvasHeight
    ) {
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // Render layers in ascending zIndex order (Background -> Foreground)
        renderPlan.layers.forEach { layer ->
            if (layer.isVisible) {
                renderLayer(
                    layer = layer,
                    cameraTextureId = cameraTextureId,
                    cameraTexMatrix = cameraTexMatrix ?: identityMatrix,
                    externalTexMatrix = externalTexMatrices[layer.sourceId] ?: identityMatrix
                )
            }
        }

        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun renderLayer(
        layer: RenderableLayer,
        cameraTextureId: Int?,
        cameraTexMatrix: FloatArray,
        externalTexMatrix: FloatArray
    ) {
        when (layer.sourceType) {
            SourceType.CAMERA -> {
                val texture = externalTextures[layer.sourceId] ?: cameraTextureId
                if (texture != null && texture != 0) drawOesLayer(layer, texture, cameraTexMatrix)
            }
            SourceType.SCREEN, SourceType.MEDIA, SourceType.BROWSER -> {
                externalTextures[layer.sourceId]?.let { drawOesLayer(layer, it, externalTexMatrix) }
            }
            SourceType.IMAGE, SourceType.TEXT -> {
                standardTextures[layer.sourceId]?.let { draw2dLayer(layer, it) }
            }
        }
    }

    private fun draw2dLayer(layer: RenderableLayer, textureId: Int) {
        val shader = standardShader ?: return
        val locations = standardLocations ?: return
        shader.use()
        GLES20.glUniformMatrix4fv(locations.mvp, 1, false, layer.modelMatrix, 0)
        GLES20.glUniformMatrix4fv(locations.texMatrix, 1, false, identityMatrix, 0)
        GLES20.glUniform1f(locations.opacity, layer.opacity)
        val w = layer.transform.width.coerceAtLeast(1f)
        val h = layer.transform.height.coerceAtLeast(1f)
        GLES20.glUniform4f(locations.crop, layer.crop.left / w, layer.crop.top / h, layer.crop.right / w, layer.crop.bottom / h)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(locations.texture, 0)
        GLES20.glEnableVertexAttribArray(locations.position)
        GLES20.glVertexAttribPointer(locations.position, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(locations.textureCoord)
        GLES20.glVertexAttribPointer(locations.textureCoord, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(locations.position)
        GLES20.glDisableVertexAttribArray(locations.textureCoord)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun drawOesLayer(
        layer: RenderableLayer,
        textureId: Int,
        texMatrix: FloatArray
    ) {
        val shader = oesShader ?: return
        val locations = oesLocations ?: return
        shader.use()

        // Pass 4x4 Model-View-Projection matrix from layer
        GLES20.glUniformMatrix4fv(locations.mvp, 1, false, layer.modelMatrix, 0)
        // Pass 4x4 hardware surface texture matrix (handles camera orientation)
        GLES20.glUniformMatrix4fv(locations.texMatrix, 1, false, texMatrix, 0)

        // Pass Layer Opacity
        GLES20.glUniform1f(locations.opacity, layer.opacity)

        // Pass Normalized Crop (left, top, right, bottom relative to layer size)
        val w = layer.transform.width.coerceAtLeast(1f)
        val h = layer.transform.height.coerceAtLeast(1f)
        val normCropLeft = layer.crop.left / w
        val normCropTop = layer.crop.top / h
        val normCropRight = layer.crop.right / w
        val normCropBottom = layer.crop.bottom / h
        GLES20.glUniform4f(locations.crop, normCropLeft, normCropTop, normCropRight, normCropBottom)

        // Pass Chroma Key parameters
        val chroma = layer.chromaKey
        if (chroma != null && chroma.enabled) {
            GLES20.glUniform1i(locations.chromaEnabled, 1)
            val r = ((chroma.keyColorHex shr 16) and 0xFF) / 255f
            val g = ((chroma.keyColorHex shr 8) and 0xFF) / 255f
            val b = (chroma.keyColorHex and 0xFF) / 255f
            GLES20.glUniform3f(locations.chromaColor, r, g, b)
            GLES20.glUniform1f(locations.chromaSimilarity, chroma.similarity)
            GLES20.glUniform1f(locations.chromaSmoothness, chroma.smoothness)
            GLES20.glUniform1f(locations.chromaSpill, chroma.spillReduction)
        } else {
            GLES20.glUniform1i(locations.chromaEnabled, 0)
        }

        // Bind OES Camera Texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(locations.texture, 0)

        // Supply vertex geometry
        GLES20.glEnableVertexAttribArray(locations.position)
        GLES20.glVertexAttribPointer(locations.position, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(locations.textureCoord)
        GLES20.glVertexAttribPointer(locations.textureCoord, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        // Execute draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(locations.position)
        GLES20.glDisableVertexAttribArray(locations.textureCoord)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    fun release() {
        oesShader?.release()
        oesShader = null
        standardShader?.release()
        standardShader = null
        oesLocations = null
        standardLocations = null
        clearTextureBindings()
    }
}
