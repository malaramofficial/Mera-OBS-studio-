package com.malaramofficial.mobilestudio.domain.model.audio

/**
 * State of an individual channel in the Studio Audio Mixer.
 */
data class AudioChannelState(
    val id: String,
    val name: String,
    val type: AudioSourceType,
    val volumeGain: Float = 1.0f, // 0.0 (silent) to 2.0 (+6dB boost)
    val isMuted: Boolean = false,
    val peakDbfs: Float = -60.0f, // Peak level in dBFS (-60 to 0)
    val rmsDbfs: Float = -60.0f,  // RMS energy level in dBFS
    val filters: List<AudioFilter> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Channel id cannot be blank" }
        require(volumeGain in 0f..2f) { "Volume gain must be between 0.0 and 2.0" }
    }
}
