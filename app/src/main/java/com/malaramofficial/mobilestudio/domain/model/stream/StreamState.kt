package com.malaramofficial.mobilestudio.domain.model.stream

import com.malaramofficial.mobilestudio.core.model.AppError

/**
 * State of the RTMP/RTMPS streaming broadcast engine.
 */
sealed interface StreamState {
    data object Idle : StreamState
    data object Preparing : StreamState
    data object Connecting : StreamState
    data class Live(val startedTimestamp: Long = System.currentTimeMillis()) : StreamState
    data class Reconnecting(val attempt: Int, val maxAttempts: Int = 5) : StreamState
    data object Stopping : StreamState
    data class Failed(val error: String, val canRetry: Boolean = true) : StreamState
    data class Error(val appError: AppError) : StreamState

    val isBroadcasting: Boolean
        get() = this is Live || this is Reconnecting

    val isLive: Boolean
        get() = this is Live
}
