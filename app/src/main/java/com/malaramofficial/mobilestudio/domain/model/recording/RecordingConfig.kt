package com.malaramofficial.mobilestudio.domain.model.recording

/**
 * Settings for local MP4 recording capture.
 */
data class RecordingConfig(
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 30,
    val videoBitrateKbps: Int = 6000,
    val audioBitrateKbps: Int = 128,
    val filePrefix: String = "Malaram_Studio",
    val outputDirectoryName: String = "Movies/MalaramStudio"
) {
    init {
        require(width > 0 && height > 0) { "Resolution dimensions must be positive" }
        require(fps in 24..60) { "FPS must be between 24 and 60, got $fps" }
        require(videoBitrateKbps >= 1000) { "Recording bitrate should be at least 1000 kbps" }
    }
}
