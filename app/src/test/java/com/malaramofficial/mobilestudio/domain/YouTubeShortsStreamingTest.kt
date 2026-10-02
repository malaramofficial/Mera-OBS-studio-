package com.malaramofficial.mobilestudio.domain

import com.malaramofficial.mobilestudio.data.security.SecureCredentialStore
import com.malaramofficial.mobilestudio.domain.model.stream.StreamConfig
import com.malaramofficial.mobilestudio.domain.model.stream.StreamOrientation
import com.malaramofficial.mobilestudio.domain.model.stream.StreamState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating YouTube Live and YouTube Shorts (9:16 Vertical)
 * stream configuration, orientation constraints, and stream key masking.
 */
class YouTubeShortsStreamingTest {

    @Test
    fun testYouTubeShorts1080pConfiguration() {
        val config = StreamConfig.YOUTUBE_SHORTS_1080P.copy(streamKey = "live_sample_key_123")

        assertTrue("Should be vertical for YouTube Shorts feed", config.isVerticalShorts)
        assertEquals(StreamOrientation.VERTICAL_SHORTS, config.orientation)
        assertEquals(1080, config.width)
        assertEquals(1920, config.height)
        assertEquals(StreamConfig.YOUTUBE_RTMP_URL, config.serverUrl)
        assertTrue(config.isValidForBroadcast)
    }

    @Test
    fun testYouTubeShorts720pConfiguration() {
        val config = StreamConfig.YOUTUBE_SHORTS_720P.copy(streamKey = "live_sample_key_456")

        assertTrue("Should be vertical for YouTube Shorts feed", config.isVerticalShorts)
        assertEquals(StreamOrientation.VERTICAL_SHORTS, config.orientation)
        assertEquals(720, config.width)
        assertEquals(1280, config.height)
        assertEquals(2500, config.videoBitrateKbps)
        assertTrue(config.isValidForBroadcast)
    }

    @Test
    fun testYouTubeLandscapeConfiguration() {
        val config = StreamConfig.YOUTUBE_LANDSCAPE_1080P.copy(streamKey = "live_sample_key_789")

        assertFalse("Landscape should not be vertical", config.isVerticalShorts)
        assertEquals(StreamOrientation.HORIZONTAL_STANDARD, config.orientation)
        assertEquals(1920, config.width)
        assertEquals(1080, config.height)
        assertTrue(config.isValidForBroadcast)
    }

    @Test
    fun testInvalidStreamKeyFailsBroadcastValidation() {
        val emptyKeyConfig = StreamConfig(
            streamKey = "",
            width = 1080,
            height = 1920
        )
        assertFalse("Empty stream key must not be valid for broadcast", emptyKeyConfig.isValidForBroadcast)
    }

    @Test
    fun testStreamKeyMaskingForUI() {
        val originalKey = "a1b2-c3d4-e5f6-g7h8"
        val masked = SecureCredentialStore.maskStreamKey(originalKey)

        assertEquals("••••••••g7h8", masked)
    }

    @Test
    fun testStreamStateLifeCycle() {
        val idleState = StreamState.Idle
        assertFalse(idleState.isLive)
        assertFalse(idleState.isBroadcasting)

        val liveState = StreamState.Live(startedTimestamp = 1000L)
        assertTrue(liveState.isLive)
        assertTrue(liveState.isBroadcasting)
    }
}
