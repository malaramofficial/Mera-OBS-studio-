package com.malaramofficial.mobilestudio.domain.model.audio

/**
 * Audio processing filters available on channels.
 */
sealed interface AudioFilter {
    val enabled: Boolean

    data class NoiseSuppressor(
        override val enabled: Boolean = true,
        val thresholdDb: Float = -40f
    ) : AudioFilter

    data class HighPass(
        override val enabled: Boolean = true,
        val cutoffHz: Int = 80
    ) : AudioFilter

    data class Limiter(
        override val enabled: Boolean = true,
        val ceilingDb: Float = -0.5f,
        val releaseMs: Int = 50
    ) : AudioFilter
}
