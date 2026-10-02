package com.malaramofficial.mobilestudio.domain.model.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface representing a hardware or virtual audio source.
 */
interface AudioSource {
    val id: String
    val name: String
    val type: AudioSourceType
    val isRunning: StateFlow<Boolean>

    suspend fun start()
    suspend fun stop()
    fun release()
}
