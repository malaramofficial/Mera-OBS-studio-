package com.malaramofficial.mobilestudio.domain.model.recording

/**
 * Operational telemetry for active local MP4 recording.
 */
data class RecordingStats(
    val durationSeconds: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val framesWritten: Long = 0L,
    val availableStorageBytes: Long = 0L
) {
    val fileSizeFormatted: String
        get() = when {
            fileSizeBytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", fileSizeBytes / (1024.0 * 1024.0 * 1024.0))
            fileSizeBytes >= 1024 * 1024 -> String.format("%.1f MB", fileSizeBytes / (1024.0 * 1024.0))
            else -> String.format("%d KB", fileSizeBytes / 1024)
        }
}
