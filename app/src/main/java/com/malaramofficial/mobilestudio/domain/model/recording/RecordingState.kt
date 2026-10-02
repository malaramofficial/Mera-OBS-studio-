package com.malaramofficial.mobilestudio.domain.model.recording

/**
 * State of local MP4 file recording.
 */
sealed interface RecordingState {
    data object Idle : RecordingState
    data object Preparing : RecordingState
    data class Recording(val startedTimestamp: Long, val outputPath: String) : RecordingState
    data class Paused(val elapsedDurationSeconds: Long) : RecordingState
    data object Stopping : RecordingState
    data class Failed(val error: String) : RecordingState

    val isCapturingToFile: Boolean
        get() = this is Recording || this is Paused
}
