package com.malaramofficial.mobilestudio.domain.model.stream

/**
 * Real-time operational statistics for active network streaming.
 */
data class StreamStats(
    val currentBitrateKbps: Int = 0,
    val currentFps: Float = 0f,
    val droppedFrames: Long = 0L,
    val totalFramesSent: Long = 0L,
    val durationSeconds: Long = 0L,
    val reconnectCount: Int = 0,
    val bitrateKbps: Int = currentBitrateKbps,
    val fps: Double = currentFps.toDouble(),
    val durationMs: Long = durationSeconds * 1000L,
    val totalBytesSent: Long = 0L
) {
    val dropRatePercentage: Float
        get() = if (totalFramesSent + droppedFrames > 0) {
            (droppedFrames.toFloat() / (totalFramesSent + droppedFrames)) * 100f
        } else {
            0f
        }
}
