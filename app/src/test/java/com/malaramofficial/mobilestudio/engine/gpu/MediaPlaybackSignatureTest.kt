package com.malaramofficial.mobilestudio.engine.gpu

import com.malaramofficial.mobilestudio.domain.model.source.SourceConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaPlaybackSignatureTest {
    @Test
    fun signatureContainsOnlyPlaybackRelevantFields() {
        val config = SourceConfig.Media("content://video-a", isLooping = true, autoPlay = true, volume = 0.65f)
        val signature = MediaPlaybackSignature.from(config)

        assertEquals(
            MediaPlaybackSignature(
                uri = "content://video-a",
                isLooping = true,
                autoPlay = true,
                volume = 0.65f
            ),
            signature
        )
    }

    @Test
    fun uriIsTrimmedAndVolumeIsClamped() {
        val config = SourceConfig.Media("  content://video-b  ", isLooping = false, autoPlay = false, volume = 4f)
        val signature = MediaPlaybackSignature.from(config)

        assertEquals("content://video-b", signature.uri)
        assertEquals(1f, signature.volume)
    }
}
