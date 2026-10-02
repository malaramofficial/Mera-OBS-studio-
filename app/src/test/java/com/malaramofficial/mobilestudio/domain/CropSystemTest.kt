package com.malaramofficial.mobilestudio.domain

import com.malaramofficial.mobilestudio.domain.model.scene.Crop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropSystemTest {

    @Test
    fun testCropPropertiesAndZeroCheck() {
        val defaultCrop = Crop()
        assertTrue(defaultCrop.isZero)

        val customCrop = defaultCrop.withLeft(10f).withTop(20f).withRight(30f).withBottom(40f)
        assertFalse(customCrop.isZero)
        assertEquals(10f, customCrop.left, 0.001f)
        assertEquals(20f, customCrop.top, 0.001f)
        assertEquals(30f, customCrop.right, 0.001f)
        assertEquals(40f, customCrop.bottom, 0.001f)
    }

    @Test
    fun testCropValidityBounds() {
        val crop = Crop(left = 100f, top = 50f, right = 200f, bottom = 100f)

        // Source 1920x1080: horizontal total = 300 < 1920, vertical total = 150 < 1080 -> valid
        assertTrue(crop.isValidFor(1920f, 1080f))

        // Source 300x200: horizontal total = 300 >= 300 -> invalid!
        assertFalse(crop.isValidFor(300f, 200f))

        // Source 400x150: vertical total = 150 >= 150 -> invalid!
        assertFalse(crop.isValidFor(400f, 150f))

        // Zero or negative source dimensions -> invalid
        assertFalse(crop.isValidFor(0f, 100f))
        assertFalse(crop.isValidFor(-100f, -100f))
    }

    @Test
    fun testCropClampBehavior() {
        // Crop larger than source 100x100
        val oversizedCrop = Crop(left = 80f, top = 80f, right = 80f, bottom = 80f)
        val clamped = oversizedCrop.clamp(sourceWidth = 100f, sourceHeight = 100f)

        assertTrue(clamped.isValidFor(100f, 100f))
        // Must leave at least 1 pixel of width and height
        assertTrue(clamped.getCroppedWidth(100f) >= 1f)
        assertTrue(clamped.getCroppedHeight(100f) >= 1f)
    }
}
