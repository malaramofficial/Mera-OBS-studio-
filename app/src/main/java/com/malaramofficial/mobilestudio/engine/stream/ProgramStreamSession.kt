package com.malaramofficial.mobilestudio.engine.stream

import android.media.MediaCodec
import android.media.MediaFormat
import com.malaramofficial.mobilestudio.domain.model.render.StudioOutputProfile
import com.malaramofficial.mobilestudio.engine.encoder.H264ProgramEncoder
import com.malaramofficial.mobilestudio.engine.gpu.StudioRenderPipeline
import java.nio.ByteBuffer

/**
 * Owns the complete Program output path:
 * GPU Program Surface -> H.264 video + optional microphone AAC -> RTMP.
 *
 * Video is the primary broadcast path. Microphone audio is attached when the
 * Android audio source is available, but a microphone permission/device failure
 * must not prevent the video stream from connecting to the RTMP server.
 */
class ProgramStreamSession(
    private val renderPipeline: StudioRenderPipeline,
    private val listener: Listener = object : Listener {}
) {

    interface Listener {
        fun onConnecting(endpoint: String) {}
        fun onConnected() {}
        fun onFailed(reason: String) {}
        fun onDisconnected() {}
        fun onBitrate(bitrate: Long) {}
    }

    private val encoder = H264ProgramEncoder()
    private val transport = RtmpVideoTransport(object : RtmpVideoTransport.Listener {
        override fun onStarted(url: String) = listener.onConnecting(url)
        override fun onConnected() = listener.onConnected()
        override fun onFailed(reason: String) = listener.onFailed(reason)
        override fun onDisconnected() = listener.onDisconnected()
        override fun onBitrate(bitrate: Long) = listener.onBitrate(bitrate)
    })
    private val audio = StudioMicrophoneAudio()

    private var endpoint: String? = null
    private var started = false
    private var videoFormatReady = false
    private var audioFormatReady = false

    @Synchronized
    fun start(endpoint: String, profile: StudioOutputProfile = StudioOutputProfile.VERTICAL_9_16, bitrateKbps: Int = profile.bitrateKbps) {
        check(!started) { "Program stream session already started" }
        require(endpoint.isNotBlank()) { "RTMP endpoint is required" }

        this.endpoint = endpoint
        started = true
        transport.configure(profile.width, profile.height, profile.fps)
        transport.configureAudio(sampleRate = 48_000, isStereo = true, bitrateKbps = 128)
        transport.setRetryCount(3)

        val surface = encoder.start(
            config = H264ProgramEncoder.Config(
                width = profile.width,
                height = profile.height,
                fps = profile.fps,
                bitrateKbps = bitrateKbps.coerceIn(500, 12000)
            ),
            onFormat = { format: MediaFormat ->
                transport.setVideoFormat(format)
                videoFormatReady = true
                maybeConnect()
            },
            onFrame = { frame ->
                transport.send(frame)
            },
            onError = { error ->
                listener.onFailed(error.message ?: "H.264 encoder error")
            }
        )

        renderPipeline.attachProgramOutputSurface(
            surface = surface,
            width = profile.width,
            height = profile.height
        )

        // Audio is best-effort. The video/RTMP path must not remain stuck in
        // "Preparing" just because microphone permission is unavailable.
        audio.start(object : StudioMicrophoneAudio.Listener {
            override fun onFormat(format: MediaFormat) {
                transport.setAudioFormat(format)
                audioFormatReady = true
                // Video may already be connected. If not, video readiness is
                // sufficient to establish the RTMP session.
                maybeConnect()
            }

            override fun onError(reason: String) {
                audioFormatReady = false
                // Keep the broadcast alive as video-only when microphone audio
                // cannot be initialized.
            }

            override fun onFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
                transport.sendAudio(buffer, info)
            }
        })
    }

    @Synchronized
    private fun maybeConnect() {
        if (!started || !videoFormatReady) return
        val url = endpoint ?: return
        if (!transport.isStreaming()) transport.connect(url)
    }

    @Synchronized
    fun stop() {
        if (!started) return
        audio.stop()
        renderPipeline.detachProgramOutputSurface()
        transport.stop()
        encoder.stop()
        endpoint = null
        videoFormatReady = false
        audioFormatReady = false
        started = false
    }

    /**
     * Applies a new video bitrate to the running MediaCodec encoder.
     * The RTMP connection and GPU output surface remain untouched.
     */
    @Synchronized
    fun setVideoBitrateKbps(bitrateKbps: Int): Boolean {
        if (!started || bitrateKbps !in 500..12000) return false
        return encoder.setBitrateKbps(bitrateKbps)
    }

    fun isStarted(): Boolean = started
    fun isStreaming(): Boolean = transport.isStreaming()
    val sentFrames: Long get() = transport.sentVideoFrames
    val droppedFrames: Long get() = transport.droppedVideoFrames
    val sentAudioFrames: Long get() = transport.sentAudioFrames
    val droppedAudioFrames: Long get() = transport.droppedAudioFrames
    val bytesSent: Long get() = transport.bytesSent
}
