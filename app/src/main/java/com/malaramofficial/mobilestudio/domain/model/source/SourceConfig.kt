package com.malaramofficial.mobilestudio.domain.model.source

/**
 * Domain-level polymorphic configuration models for individual source types.
 * Decoupled from hardware drivers and rendering engines so Phase 2+ can plug in backends.
 */
sealed interface SourceConfig {

    data class Camera(
        val lensFacing: LensFacing = LensFacing.BACK,
        val targetWidth: Int = 1920,
        val targetHeight: Int = 1080,
        val sensorOrientation: Int = 90
    ) : SourceConfig {
        enum class LensFacing { FRONT, BACK, EXTERNAL }
    }

    data class Screen(
        val captureWidth: Int = 1920,
        val captureHeight: Int = 1080,
        val captureFps: Int = 60,
        val captureSystemAudio: Boolean = true
    ) : SourceConfig

    data class Image(
        val uri: String = "",
        val scaleMode: ScaleMode = ScaleMode.FIT,
        val alpha: Float = 1.0f,
        val intrinsicWidthPx: Int = 0,
        val intrinsicHeightPx: Int = 0
    ) : SourceConfig {
        enum class ScaleMode { FIT, FILL, STRETCH }
    }

    data class Text(
        val text: String = "Live Studio Stream",
        val fontSizeSp: Float = 36f,
        val textColorHex: Long = 0xFFFFFFFF,
        val backgroundColorHex: Long = 0x00000000,
        val fontFamilyName: String = "DEFAULT"
    ) : SourceConfig

    data class Media(
        val uri: String = "",
        val isLooping: Boolean = true,
        val autoPlay: Boolean = true,
        val volume: Float = 1.0f
    ) : SourceConfig

    data class Browser(
        val url: String = "https://example.com",
        val customCss: String = "",
        val renderFps: Int = 30
    ) : SourceConfig
}
