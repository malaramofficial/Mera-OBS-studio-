package com.malaramofficial.mobilestudio.engine.gpu

import com.malaramofficial.mobilestudio.domain.model.source.SourceConfig

/**
 * Playback identity/config only. Visual scene properties are deliberately excluded.
 */
data class MediaPlaybackSignature(
    val uri: String,
    val isLooping: Boolean,
    val autoPlay: Boolean,
    val volume: Float
) {
    companion object {
        fun from(config: SourceConfig.Media): MediaPlaybackSignature = MediaPlaybackSignature(
            uri = config.uri.trim(),
            isLooping = config.isLooping,
            autoPlay = config.autoPlay,
            volume = config.volume.coerceIn(0f, 1f)
        )
    }
}