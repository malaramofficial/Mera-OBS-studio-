package com.malaramofficial.mobilestudio.engine.stream

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StudioBroadcastController(
    private val renderPipeline: com.malaramofficial.mobilestudio.engine.gpu.StudioRenderPipeline
) {
    private val _state = MutableStateFlow<com.malaramofficial.mobilestudio.domain.model.stream.StreamState>(
        com.malaramofficial.mobilestudio.domain.model.stream.StreamState.Idle
    )
    val state: StateFlow<com.malaramofficial.mobilestudio.domain.model.stream.StreamState> = _state.asStateFlow()

    private var session: ProgramStreamSession? = null

    @Synchronized
    fun start(endpoint: String, bitrateKbps: Int = 6000) {
        check(endpoint.isNotBlank()) { "RTMP endpoint is required" }
        if (_state.value.isBroadcasting) return
        _state.value = com.malaramofficial.mobilestudio.domain.model.stream.StreamState.Preparing
        val newSession = ProgramStreamSession(
            renderPipeline = renderPipeline,
            listener = object : ProgramStreamSession.Listener {
                override fun onConnecting(endpoint: String) {
                    _state.value = com.malaramofficial.mobilestudio.domain.model.stream.StreamState.Connecting
                }

                override fun onConnected() {
                    _state.value = com.malaramofficial.mobilestudio.domain.model.stream.StreamState.Live(System.currentTimeMillis())
                }

                override fun onFailed(reason: String) {
                    _state.value = com.malaramofficial.mobilestudio.domain.model.stream.StreamState.Failed(reason)
                }

                override fun onDisconnected() {
                    _state.value = com.malaramofficial.mobilestudio.domain.model.stream.StreamState.Idle
                }
            }
        )
        session = newSession
        try {
            newSession.start(endpoint, bitrateKbps = bitrateKbps.coerceIn(500, 12000))
        } catch (t: Throwable) {
            session = null
            _state.value = com.malaramofficial.mobilestudio.domain.model.stream.StreamState.Failed(
                t.message ?: "Unable to start live stream"
            )
            throw t
        }
    }

    @Synchronized
    fun stop() {
        _state.value = com.malaramofficial.mobilestudio.domain.model.stream.StreamState.Stopping
        session?.stop()
        session = null
        _state.value = com.malaramofficial.mobilestudio.domain.model.stream.StreamState.Idle
    }

    /**
     * Changes video bitrate while the current live session is running.
     * No RTMP disconnect/reconnect is performed.
     */
    @Synchronized
    fun setVideoBitrateKbps(bitrateKbps: Int): Boolean {
        return session?.setVideoBitrateKbps(bitrateKbps) == true
    }

    fun isLive(): Boolean = _state.value.isBroadcasting
}
