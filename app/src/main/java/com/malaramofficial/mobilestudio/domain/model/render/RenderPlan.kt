package com.malaramofficial.mobilestudio.domain.model.render

/**
 * Immutable frame compositing blueprint containing the ordered draw calls
 * for a specific scene snapshot.
 */
data class RenderPlan(
    val sceneId: String,
    val sceneName: String,
    val layers: List<RenderableLayer>, // Strictly sorted by zIndex ascending (0 -> N)
    val canvasWidth: Int = 1080,
    val canvasHeight: Int = 1920,
    val backgroundColorHex: Long = 0xFF000000, // Black background default
    val timestampNs: Long = 0L
) {
    val visibleLayerCount: Int
        get() = layers.count { it.isVisible }
}
