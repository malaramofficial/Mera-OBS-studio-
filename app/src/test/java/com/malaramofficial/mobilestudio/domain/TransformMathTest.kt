package com.malaramofficial.mobilestudio.domain

import com.malaramofficial.mobilestudio.domain.model.scene.Transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransformMathTest {

    @Test
    fun testMoveByAndMoveTo() {
        val t = Transform(x = 100f, y = 100f)
        val moved = t.moveBy(50f, -30f)
        assertEquals(150f, moved.x, 0.001f)
        assertEquals(70f, moved.y, 0.001f)

        val relocated = moved.moveTo(500f, 600f)
        assertEquals(500f, relocated.x, 0.001f)
        assertEquals(600f, relocated.y, 0.001f)
    }

    @Test
    fun testResizeAndAspectRatio() {
        val t = Transform(width = 1920f, height = 1080f)
        assertEquals(16f / 9f, t.aspectRatio, 0.001f)

        val resized = t.resize(1280f, 720f)
        assertEquals(1280f, resized.width, 0.001f)
        assertEquals(720f, resized.height, 0.001f)
        assertEquals(16f / 9f, resized.aspectRatio, 0.001f)

        assertThrows(IllegalArgumentException::class.java) {
            t.resize(-100f, 720f)
        }
    }

    @Test
    fun testRotateAndNormalization() {
        val t = Transform(rotation = 0f)
        val r1 = t.rotateBy(45f)
        assertEquals(45f, r1.rotation, 0.001f)

        // Overflow past 360
        val r2 = r1.rotateBy(350f) // 45 + 350 = 395 -> 35
        assertEquals(35f, r2.rotation, 0.001f)

        // Negative rotation normalization
        val r3 = r2.rotateBy(-90f) // 35 - 90 = -55 -> 305
        assertEquals(305f, r3.rotation, 0.001f)

        val r4 = t.setRotation(-180f)
        assertEquals(180f, r4.rotation, 0.001f)
    }

    @Test
    fun testScaleAndEffectiveDimensions() {
        val t = Transform(width = 400f, height = 300f, scaleX = 1f, scaleY = 1f)
        assertEquals(400f, t.effectiveWidth, 0.001f)
        assertEquals(300f, t.effectiveHeight, 0.001f)

        val scaled = t.scaleBy(1.5f, 2.0f)
        assertEquals(1.5f, scaled.scaleX, 0.001f)
        assertEquals(2.0f, scaled.scaleY, 0.001f)
        assertEquals(600f, scaled.effectiveWidth, 0.001f)
        assertEquals(600f, scaled.effectiveHeight, 0.001f)

        assertThrows(IllegalArgumentException::class.java) {
            t.scaleBy(-1f, 1f)
        }
    }

    @Test
    fun testReset() {
        val custom = Transform(x = 50f, y = 80f, width = 640f, height = 480f, rotation = 90f, scaleX = 2f, scaleY = 2f)
        val reset = custom.reset()
        assertEquals(0f, reset.x, 0.001f)
        assertEquals(0f, reset.y, 0.001f)
        assertEquals(1920f, reset.width, 0.001f)
        assertEquals(1080f, reset.height, 0.001f)
        assertEquals(0f, reset.rotation, 0.001f)
        assertEquals(1f, reset.scaleX, 0.001f)
    }

    @Test
    fun testToMatrix4x4Calculation() {
        val t = Transform(x = 0f, y = 0f, width = 1920f, height = 1080f, rotation = 0f)
        val matrix = t.toMatrix4x4(1920f, 1080f)

        assertEquals(16, matrix.size)
        // Fullscreen unrotated quad fills normalized device coordinates
        assertEquals(1.0f, matrix[0], 0.001f)  // scale X
        assertEquals(1.0f, matrix[5], 0.001f)  // scale Y
        assertEquals(0.0f, matrix[12], 0.001f) // center X
        assertEquals(0.0f, matrix[13], 0.001f) // center Y
    }
}
