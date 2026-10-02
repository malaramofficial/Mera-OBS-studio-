package com.malaramofficial.mobilestudio.domain

import com.malaramofficial.mobilestudio.domain.model.render.RenderPlanBuilder
import com.malaramofficial.mobilestudio.domain.model.scene.ChromaKeyConfig
import com.malaramofficial.mobilestudio.domain.model.scene.Crop
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.Source
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.scene.Transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderPlanTest {

    @Test
    fun testRenderPlanGeneration() {
        val s1 = Source(
            id = "s1",
            name = "Cam",
            type = SourceType.CAMERA,
            visible = true,
            zIndex = 0,
            transform = Transform(x = 0f, y = 0f, width = 1920f, height = 1080f),
            chromaKey = ChromaKeyConfig(enabled = true, keyColorHex = 0xFF00FF00, similarity = 0.4f)
        )
        val s2 = Source(
            id = "s2",
            name = "Hidden Overlay",
            type = SourceType.IMAGE,
            visible = false,
            zIndex = 1,
            transform = Transform(x = 100f, y = 100f, width = 200f, height = 200f)
        )

        val scene = Scene(id = "sc_render", name = "Render Stage", sources = listOf(s1, s2))
        val plan = RenderPlanBuilder.build(scene)

        assertEquals("sc_render", plan.sceneId)
        assertEquals("Render Stage", plan.sceneName)
        assertEquals(2, plan.layers.size)
        assertEquals(1, plan.visibleLayerCount)

        val layer1 = plan.layers[0]
        assertEquals("s1", layer1.sourceId)
        assertEquals(SourceType.CAMERA, layer1.sourceType)
        assertTrue(layer1.isVisible)
        assertNotNull(layer1.chromaKey)
        assertEquals(16, layer1.modelMatrix.size)

        val layer2 = plan.layers[1]
        assertEquals("s2", layer2.sourceId)
        assertEquals(false, layer2.isVisible)
    }

 
    @Test
    fun customScreenAndCameraTransformsArePreservedForProgramOutput() {
        val screenTransform = Transform(x = 30f, y = 80f, width = 900f, height = 700f, rotation = 90f, scaleX = 1.5f, scaleY = 1.5f)
        val cameraTransform = Transform(x = 650f, y = 1200f, width = 320f, height = 420f, rotation = 12f)
        val screen = Source(id = "screen", name = "Gameplay", type = SourceType.SCREEN, transform = screenTransform)
        val camera = Source(id = "camera", name = "Face Camera", type = SourceType.CAMERA, transform = cameraTransform)
        val plan = RenderPlanBuilder.build(Scene(id = "shorts", name = "Shorts Live", sources = listOf(screen, camera)))

        assertEquals(screenTransform, plan.layers.first { it.sourceId == "screen" }.transform)
        assertEquals(cameraTransform, plan.layers.first { it.sourceId == "camera" }.transform)
        assertTrue(plan.layers.first { it.sourceId == "screen" }.modelMatrix.contentEquals(screenTransform.toMatrix4x4(1080f, 1920f)))
    }

    @Test
    fun testRenderPlanAppliesSafeCropClamping() {
        // Create source with crop exceeding width
        val s = Source(
            id = "s_crop",
            name = "Crop Test",
            type = SourceType.IMAGE,
            transform = Transform(width = 100f, height = 100f),
            crop = Crop(left = 70f, top = 20f, right = 70f, bottom = 20f) // 70+70 = 140 > 100
        )
        val scene = Scene(id = "sc", name = "Scene", sources = listOf(s))
        val plan = RenderPlanBuilder.build(scene)

        val layer = plan.layers[0]
        // Ensure clamped crop is valid
        assertTrue(layer.crop.isValidFor(100f, 100f))
    }
}
