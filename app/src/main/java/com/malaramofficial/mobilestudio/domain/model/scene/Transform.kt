package com.malaramofficial.mobilestudio.domain.model.scene

import kotlin.math.cos
import kotlin.math.sin

/**
 * Deterministic 2D transformation for a source layer inside the studio canvas.
 * Coordinates are expressed relative to the reference canvas (e.g. 1920x1080).
 * Platform-independent and decoupled from Compose UI coordinates.
 */
data class Transform(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1920f,
    val height: Float = 1080f,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
) {
    init {
        require(width > 0f) { "Width must be positive, got $width" }
        require(height > 0f) { "Height must be positive, got $height" }
        require(scaleX > 0f) { "ScaleX must be positive, got $scaleX" }
        require(scaleY > 0f) { "ScaleY must be positive, got $scaleY" }
    }

    val aspectRatio: Float get() = width / height

    val effectiveWidth: Float get() = width * scaleX
    val effectiveHeight: Float get() = height * scaleY

    fun moveBy(dx: Float, dy: Float): Transform {
        return copy(x = x + dx, y = y + dy)
    }

    fun moveTo(newX: Float, newY: Float): Transform {
        return copy(x = newX, y = newY)
    }

    fun resize(newWidth: Float, newHeight: Float): Transform {
        require(newWidth > 0f) { "New width must be positive: $newWidth" }
        require(newHeight > 0f) { "New height must be positive: $newHeight" }
        return copy(width = newWidth, height = newHeight)
    }

    fun rotateBy(degrees: Float): Transform {
        val normalized = normalizeRotation(rotation + degrees)
        return copy(rotation = normalized)
    }

    fun setRotation(degrees: Float): Transform {
        return copy(rotation = normalizeRotation(degrees))
    }

    fun scaleBy(factorX: Float, factorY: Float): Transform {
        require(factorX > 0f) { "Scale factorX must be positive: $factorX" }
        require(factorY > 0f) { "Scale factorY must be positive: $factorY" }
        return copy(scaleX = scaleX * factorX, scaleY = scaleY * factorY)
    }

    fun setScale(newScaleX: Float, newScaleY: Float): Transform {
        require(newScaleX > 0f) { "ScaleX must be positive: $newScaleX" }
        require(newScaleY > 0f) { "ScaleY must be positive: $newScaleY" }
        return copy(scaleX = newScaleX, scaleY = newScaleY)
    }

    fun reset(): Transform {
        return Transform(
            x = 0f,
            y = 0f,
            width = 1920f,
            height = 1080f,
            rotation = 0f,
            scaleX = 1f,
            scaleY = 1f
        )
    }

    /**
     * Computes a 4x4 column-major model matrix representing this transform
     * on a target canvas of size [canvasWidth] x [canvasHeight].
     */
    fun toMatrix4x4(canvasWidth: Float = 1920f, canvasHeight: Float = 1080f): FloatArray {
        val matrix = FloatArray(16)
        // Initialize as Identity
        matrix[0] = 1f; matrix[5] = 1f; matrix[10] = 1f; matrix[15] = 1f

        val rad = Math.toRadians(rotation.toDouble()).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)

        // Normalized translations (-1.0 to 1.0 OpenGL coordinates)
        val normW = (width * scaleX) / canvasWidth
        val normH = (height * scaleY) / canvasHeight
        val normX = (x / canvasWidth) * 2f - 1f + normW
        val normY = 1f - (y / canvasHeight) * 2f - normH

        // Scale & Rotation
        matrix[0] = normW * cosA
        matrix[1] = normW * sinA
        matrix[4] = -normH * sinA
        matrix[5] = normH * cosA
        matrix[12] = normX
        matrix[13] = normY
        return matrix
    }

    companion object {
        fun normalizeRotation(degrees: Float): Float {
            var rot = degrees % 360f
            if (rot < 0f) rot += 360f
            return rot
        }
    }
}
