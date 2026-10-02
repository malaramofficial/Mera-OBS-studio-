package com.malaramofficial.mobilestudio.domain

import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.Source
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LayerOrderingTest {

    private fun createTestScene(): Scene {
        val s0 = Source(id = "layer_0", name = "Background", type = SourceType.IMAGE, zIndex = 0)
        val s1 = Source(id = "layer_1", name = "Camera", type = SourceType.CAMERA, zIndex = 1)
        val s2 = Source(id = "layer_2", name = "Text Overlay", type = SourceType.TEXT, zIndex = 2)
        val s3 = Source(id = "layer_3", name = "Alert Banner", type = SourceType.BROWSER, zIndex = 3)

        return Scene(id = "scene_layers", name = "Layer Test", sources = listOf(s0, s1, s2, s3))
    }

    @Test
    fun testNormalizeLayersEliminatesGapsAndDuplicates() {
        val sA = Source(id = "sA", name = "A", type = SourceType.IMAGE, zIndex = 10)
        val sB = Source(id = "sB", name = "B", type = SourceType.CAMERA, zIndex = 10) // Duplicate Z
        val sC = Source(id = "sC", name = "C", type = SourceType.TEXT, zIndex = 50) // Gap

        val scene = Scene(id = "sc", name = "Norm", sources = listOf(sA, sB, sC))
        val normalized = scene.normalizeLayers()

        assertEquals(0, normalized.sortedSources[0].zIndex)
        assertEquals(1, normalized.sortedSources[1].zIndex)
        assertEquals(2, normalized.sortedSources[2].zIndex)
    }

    @Test
    fun testBringToFront() {
        val scene = createTestScene()
        // Bring background (layer_0) all the way to top
        val updated = scene.bringToFront("layer_0")
        val sorted = updated.sortedSources

        assertEquals("layer_1", sorted[0].id)
        assertEquals("layer_2", sorted[1].id)
        assertEquals("layer_3", sorted[2].id)
        assertEquals("layer_0", sorted[3].id) // Now at top
        assertEquals(3, sorted[3].zIndex)
    }

    @Test
    fun testSendToBack() {
        val scene = createTestScene()
        // Send alert banner (layer_3) all the way to bottom
        val updated = scene.sendToBack("layer_3")
        val sorted = updated.sortedSources

        assertEquals("layer_3", sorted[0].id) // Now at bottom
        assertEquals("layer_0", sorted[1].id)
        assertEquals("layer_1", sorted[2].id)
        assertEquals("layer_2", sorted[3].id)
        assertEquals(0, sorted[0].zIndex)
    }

    @Test
    fun testMoveUpAndMoveDown() {
        val scene = createTestScene()
        // Move camera (layer_1) up
        val movedUp = scene.moveUp("layer_1")
        assertEquals(listOf("layer_0", "layer_2", "layer_1", "layer_3"), movedUp.sortedSources.map { it.id })

        // Move camera (now at index 2) down
        val movedDown = movedUp.moveDown("layer_1")
        assertEquals(listOf("layer_0", "layer_1", "layer_2", "layer_3"), movedDown.sortedSources.map { it.id })

        // Move bottom layer down (should be no-op)
        val downBoundary = scene.moveDown("layer_0")
        assertEquals(scene.sortedSources.map { it.id }, downBoundary.sortedSources.map { it.id })

        // Move top layer up (should be no-op)
        val upBoundary = scene.moveUp("layer_3")
        assertEquals(scene.sortedSources.map { it.id }, upBoundary.sortedSources.map { it.id })
    }

    @Test
    fun testSceneDuplicationPreservesLayerOrderingAndGeneratesNewIds() {
        val scene = createTestScene()
        val duplicated = scene.duplicate(newSceneId = "scene_dup", newSceneName = "Duplicated Scene")

        assertEquals("scene_dup", duplicated.id)
        assertEquals("Duplicated Scene", duplicated.name)
        assertEquals(4, duplicated.sources.size)

        // Ensure all source IDs are new and distinct from originals
        val originalIds = scene.sources.map { it.id }.toSet()
        val duplicatedIds = duplicated.sources.map { it.id }.toSet()
        val intersection = originalIds.intersect(duplicatedIds)
        assertEquals(0, intersection.size)

        // Ensure layer ordering and z-indices are preserved
        for (i in 0 until 4) {
            assertEquals(scene.sortedSources[i].name, duplicated.sortedSources[i].name)
            assertEquals(scene.sortedSources[i].zIndex, duplicated.sortedSources[i].zIndex)
            assertEquals(scene.sortedSources[i].type, duplicated.sortedSources[i].type)
        }
    }
}
