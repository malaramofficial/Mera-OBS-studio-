package com.malaramofficial.mobilestudio.engine.gpu

import com.malaramofficial.mobilestudio.domain.model.render.RenderPlan
import com.malaramofficial.mobilestudio.domain.model.render.RenderableLayer
import com.malaramofficial.mobilestudio.domain.model.scene.ChromaKeyConfig
import com.malaramofficial.mobilestudio.domain.model.scene.Crop
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.scene.Transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuCompositorTest {

    @Test
    fun testShaderSourceDefinitionsIncludeRequiredSamplersAndUniforms() {
        val oesCode = GlShader.FRAGMENT_SHADER_OES
        assertTrue(oesCode.contains("samplerExternalOES uTexture;"))
        assertTrue(oesCode.contains("GL_OES_EGL_image_external"))
        assertTrue(oesCode.contains("uniform vec4 uCrop;"))
        assertTrue(oesCode.contains("uniform float uOpacity;"))
        assertTrue(oesCode.contains("uniform int uChromaEnabled;"))

        val vertexCode = GlShader.VERTEX_SHADER
        assertTrue(vertexCode.contains("uMVPMatrix"))
        assertTrue(vertexCode.contains("uTexMatrix"))
        assertTrue(vertexCode.contains("aPosition"))
    }

    @Test
    fun testChromaKeyColorComponentExtraction() {
        // Green screen hex: 0xFF00FF00 (A=255, R=0, G=255, B=0)
        val chroma = ChromaKeyConfig(enabled = true, keyColorHex = 0xFF00FF00, similarity = 0.4f)
        val r = ((chroma.keyColorHex shr 16) and 0xFF) / 255f
        val g = ((chroma.keyColorHex shr 8) and 0xFF) / 255f
        val b = (chroma.keyColorHex and 0xFF) / 255f

        assertEquals(0.0f, r, 0.001f)
        assertEquals(1.0f, g, 0.001f)
        assertEquals(0.0f, b, 0.001f)
    }

    @Test
    fun testNormalizedCropUniformMapping() {
        val layerWidth = 1920f
        val layerHeight = 1080f
        val crop = Crop(left = 192f, top = 108f, right = 192f, bottom = 108f)

        val normLeft = crop.left / layerWidth
        val normTop = crop.top / layerHeight
        val normRight = crop.right / layerWidth
        val normBottom = crop.bottom / layerHeight

        assertEquals(0.10f, normLeft, 0.001f)
        assertEquals(0.10f, normTop, 0.001f)
        assertEquals(0.10f, normRight, 0.001f)
        assertEquals(0.10f, normBottom, 0.001f)
    }

    @Test
    fun testRenderPlanLayersCameraIdentification() {
        val t = Transform(x = 0f, y = 0f, width = 1920f, height = 1080f)
        val layer = RenderableLayer(
            layerId = "cam_layer",
            sourceId = "src_cam",
            sourceType = SourceType.CAMERA,
            zIndex = 0,
            isVisible = true,
            opacity = 1.0f,
            transform = t,
            crop = Crop(),
            chromaKey = null,
            modelMatrix = t.toMatrix4x4()
        )

        val plan = RenderPlan(
            sceneId = "sc1",
            sceneName = "Camera Main",
            layers = listOf(layer)
        )

        assertEquals(1, plan.layers.size)
        assertEquals(SourceType.CAMERA, plan.layers[0].sourceType)
        assertTrue(plan.layers[0].isVisible)
        assertEquals(16, plan.layers[0].modelMatrix.size)
    }
}
