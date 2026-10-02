package com.malaramofficial.mobilestudio.domain.model.stream

/**
 * Output video & audio configuration for RTMP live streaming.
 *
 * The studio's primary delivery target is YouTube vertical live / Shorts
 * discovery, so the default broadcast canvas must stay 9:16.
 */
data class StreamConfig(
    val serverUrl: String = "rtmp://a.rtmp.youtube.com/live2",
    val streamKey: String = "",
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 30,
    val videoBitrateKbps: Int = 6000,
    val keyframeIntervalSec: Int = 2,
    val audioBitrateKbps: Int = 128,
    val audioSampleRate: Int = 48000
) {
    init {
        require(width > 0 && height > 0) { "Resolution dimensions must be positive" }
        require(fps in 15..60) { "FPS must be between 15 and 60, got $fps" }
        require(videoBitrateKbps >= 500) { "Video bitrate must be at least 500 kbps" }
        require(keyframeIntervalSec in 1..5) { "Keyframe interval must be in 1..5 seconds" }
        require(audioBitrateKbps in 64..320) { "Audio bitrate must be between 64 and 320 kbps" }
    }

    val isPortrait: Boolean
        get() = height > width

    val isValidForBroadcast: Boolean
        get() = serverUrl.isNotBlank() && streamKey.isNotBlank()
}
