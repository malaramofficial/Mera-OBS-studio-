package com.malaramofficial.mobilestudio.domain.model.render

/**
 * Defines the actual broadcast/recording canvas.
 * Portrait 9:16 is the default because the primary target is vertical live content.
 */
data class StudioOutputProfile(
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 30,
    val bitrateKbps: Int = 6000
) {
    init {
        require(width > 0 && height > 0)
        require(fps in 1..120)
        require(bitrateKbps > 0)
    }

    val isPortrait: Boolean
        get() = height > width

    companion object {
        val VERTICAL_9_16 = StudioOutputProfile(
            width = 1080,
            height = 1920,
            fps = 30,
            bitrateKbps = 6000
        )

        val LANDSCAPE_16_9 = StudioOutputProfile(
            width = 1920,
            height = 1080,
            fps = 30,
            bitrateKbps = 6000
        )
    }
}
