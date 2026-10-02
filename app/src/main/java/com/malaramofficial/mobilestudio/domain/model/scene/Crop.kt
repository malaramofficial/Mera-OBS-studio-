package com.malaramofficial.mobilestudio.domain.model.scene

/**
 * Pixel crop offsets for a layer.
 * Values indicate pixels clipped from each edge before rendering.
 */
data class Crop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
) {
    init {
        require(left >= 0f) { "Crop left must be non-negative: $left" }
        require(top >= 0f) { "Crop top must be non-negative: $top" }
        require(right >= 0f) { "Crop right must be non-negative: $right" }
        require(bottom >= 0f) { "Crop bottom must be non-negative: $bottom" }
    }

    val isZero: Boolean get() = left == 0f && top == 0f && right == 0f && bottom == 0f

    fun withLeft(newLeft: Float): Crop = copy(left = newLeft.coerceAtLeast(0f))
    fun withTop(newTop: Float): Crop = copy(top = newTop.coerceAtLeast(0f))
    fun withRight(newRight: Float): Crop = copy(right = newRight.coerceAtLeast(0f))
    fun withBottom(newBottom: Float): Crop = copy(bottom = newBottom.coerceAtLeast(0f))

    /**
     * Checks if this crop is physically valid for the given source dimensions.
     * Prevents inverted/impossible crop rectangles where cropped pixels exceed original size.
     */
    fun isValidFor(sourceWidth: Float, sourceHeight: Float): Boolean {
        if (sourceWidth <= 0f || sourceHeight <= 0f) return false
        return (left + right < sourceWidth) && (top + bottom < sourceHeight)
    }

    /**
     * Clamps crop values so that at least 1 pixel of visible content remains.
     */
    fun clamp(sourceWidth: Float, sourceHeight: Float): Crop {
        val maxHorizontal = (sourceWidth - 1f).coerceAtLeast(0f)
        val maxVertical = (sourceHeight - 1f).coerceAtLeast(0f)

        val safeLeft = left.coerceIn(0f, maxHorizontal)
        val safeRight = right.coerceIn(0f, (maxHorizontal - safeLeft).coerceAtLeast(0f))

        val safeTop = top.coerceIn(0f, maxVertical)
        val safeBottom = bottom.coerceIn(0f, (maxVertical - safeTop).coerceAtLeast(0f))

        return Crop(left = safeLeft, top = safeTop, right = safeRight, bottom = safeBottom)
    }

    /**
     * Returns the cropped output dimension.
     */
    fun getCroppedWidth(sourceWidth: Float): Float = (sourceWidth - left - right).coerceAtLeast(0f)
    fun getCroppedHeight(sourceHeight: Float): Float = (sourceHeight - top - bottom).coerceAtLeast(0f)
}
