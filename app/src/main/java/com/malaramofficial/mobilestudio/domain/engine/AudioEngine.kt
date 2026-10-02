package com.malaramofficial.mobilestudio.domain.engine

import com.malaramofficial.mobilestudio.core.model.AppResult
import com.malaramofficial.mobilestudio.domain.model.audio.AudioMixerState
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface orchestrating low-latency audio capture, real-time volume mixing, and DSP filtering.
 */
interface AudioEngine {
    val mixerState: StateFlow<AudioMixerState>

    suspend fun startAudioPipeline(): AppResult<Unit>
    suspend fun stopAudioPipeline(): AppResult<Unit>
    suspend fun setChannelGain(channelId: String, gain: Float): AppResult<Unit>
    suspend fun setChannelMuted(channelId: String, muted: Boolean): AppResult<Unit>
    suspend fun setMasterGain(gain: Float): AppResult<Unit>
    suspend fun setMasterMuted(muted: Boolean): AppResult<Unit>
}
