package com.malaramofficial.mobilestudio.domain.engine

import com.malaramofficial.mobilestudio.core.model.AppResult
import com.malaramofficial.mobilestudio.domain.model.stream.StreamConfig
import com.malaramofficial.mobilestudio.domain.model.stream.StreamState
import com.malaramofficial.mobilestudio.domain.model.stream.StreamStats
import kotlinx.coroutines.flow.StateFlow

/**
 * Controller interface orchestrating hardware encoder output, RTMP socket connections, and broadcast state.
 */
interface StreamingController {
    val streamState: StateFlow<StreamState>
    val streamStats: StateFlow<StreamStats>

    suspend fun startStreaming(config: StreamConfig): AppResult<Unit>
    suspend fun stopStreaming(): AppResult<Unit>
    suspend fun updateBitrate(newBitrateKbps: Int): AppResult<Unit>
}
