package com.malaramofficial.mobilestudio.domain

import com.malaramofficial.mobilestudio.domain.model.scene.Crop
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.Source
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.scene.Transform
import com.malaramofficial.mobilestudio.domain.model.scene.Transition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneModelTest {

    @Test
    fun testSceneCreationAndDefaults() {
        val scene = Scene(
            id = "test_scene_1",
            name = "Main Broadcast"
        )
        assertEquals("test_scene_1", scene.id)
        assertEquals("Main Broadcast", scene.name)
        assertTrue(scene.sources.isEmpty())
        assertEquals(Transition.Type.CUT, scene.transition.type)
    }

    @Test
    fun testSourceOrderingByZIndex() {
        val sourceBg = Source(
            id = "src_bg",
            name = "Background",
            type = SourceType.IMAGE,
            zIndex = 0
        )
        val sourceFg = Source(
            id = "src_fg",
            name = "Overlay",
            type = SourceType.TEXT,
            zIndex = 10
        )
        val sourceMid = Source(
            id = "src_mid",
            name = "Camera",
            type = SourceType.CAMERA,
            zIndex = 5
        )

        // Add out of order
        val scene = Scene(
            id = "scene_order",
            name = "Ordering Test",
            sources = listOf(sourceFg, sourceBg, sourceMid)
        )

        val sorted = scene.sortedSources
        assertEquals(3, sorted.size)
        assertEquals("src_bg", sorted[0].id)
        assertEquals("src_mid", sorted[1].id)
        assertEquals("src_fg", sorted[2].id)
    }

    @Test
    fun testVisibilityAndLockState() {
        val source = Source(
            id = "src_1",
            name = "Layer 1",
            type = SourceType.CAMERA,
            visible = true,
            locked = false
        )
        assertTrue(source.visible)
        assertFalse(source.locked)

        val modified = source.copy(visible = false, locked = true)
        assertFalse(modified.visible)
        assertTrue(modified.locked)
    }

    @Test
    fun testTransformAndCropValues() {
        val transform = Transform(
            x = 100f,
            y = 200f,
            width = 1280f,
            height = 720f,
            rotation = 45f,
            scaleX = 1.5f,
            scaleY = 1.5f
        )
        assertEquals(100f, transform.x, 0.001f)
        assertEquals(200f, transform.y, 0.001f)
        assertEquals(1280f, transform.width, 0.001f)
        assertEquals(720f, transform.height, 0.001f)
        assertEquals(45f, transform.rotation, 0.001f)
        assertEquals(16f / 9f, transform.aspectRatio, 0.01f)

        val crop = Crop(left = 10f, top = 20f, right = 30f, bottom = 40f)
        assertFalse(crop.isZero)
        assertEquals(10f, crop.left, 0.001f)
    }

    @Test
    fun testSceneSourceUpdateHelpers() {
        val s1 = Source(id = "s1", name = "S1", type = SourceType.CAMERA)
        val s2 = Source(id = "s2", name = "S2", type = SourceType.IMAGE)
        val scene = Scene(id = "sc1", name = "Scene 1", sources = listOf(s1, s2))

        val updatedS1 = s1.copy(name = "Updated S1")
        val sceneWithUpdated = scene.withSourceUpdated(updatedS1)

        assertEquals("Updated S1", sceneWithUpdated.findSource("s1")?.name)
        assertEquals(2, sceneWithUpdated.sources.size)

        val sceneWithRemoved = sceneWithUpdated.withSourceRemoved("s2")
        assertEquals(1, sceneWithRemoved.sources.size)
        assertEquals(null, sceneWithRemoved.findSource("s2"))
    }
}
