package com.malaramofficial.mobilestudio.domain.model.audio

/**
 * Global mixer state representing all audio channels and the master mixdown bus.
 */
data class AudioMixerState(
    val channels: List<AudioChannelState> = emptyList(),
    val masterGain: Float = 1.0f,
    val isMasterMuted: Boolean = false,
    val masterPeakDbfs: Float = -60.0f,
    val masterRmsDbfs: Float = -60.0f
) {
    init {
        require(masterGain in 0f..2f) { "Master gain must be between 0.0 and 2.0" }
    }

    fun findChannel(id: String): AudioChannelState? = channels.find { it.id == id }

    fun withChannelUpdated(updatedChannel: AudioChannelState): AudioMixerState {
        return copy(channels = channels.map { if (it.id == updatedChannel.id) updatedChannel else it })
    }
}
