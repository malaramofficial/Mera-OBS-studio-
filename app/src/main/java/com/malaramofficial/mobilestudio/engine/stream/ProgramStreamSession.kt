package com.malaramofficial.mobilestudio.engine.stream

import android.media.MediaCodec
import android.media.MediaFormat
import android.content.Context
import android.net.Uri
import com.malaramofficial.mobilestudio.domain.model.render.StudioOutputProfile
import com.malaramofficial.mobilestudio.engine.encoder.H264ProgramEncoder
import com.malaramofficial.mobilestudio.engine.gpu.StudioRenderPipeline
import java.nio.ByteBuffer

/**
 * Owns the complete Program output path:
 * GPU Program Surface -> H.264 video + optional looping file AAC -> RTMP.
 *
 * Live microphone capture is intentionally disabled. A selected local audio
 * file is the only audio input for poster/music broadcasts.
 */
class ProgramStreamSession(
    private val renderPipeline: StudioRenderPipeline,
    private val context: Context?,
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
    private var audio: StudioFileAudioSource? = null

    private var endpoint: String? = null
    private var started = false
    private var videoFormatReady = false
    private var audioFormatReady = false
    private var audioExpected = false

    @Synchronized
    fun start(
        endpoint: String,
        profile: StudioOutputProfile = StudioOutputProfile.VERTICAL_9_16,
        bitrateKbps: Int = profile.bitrateKbps,
        audioUri: String? = null
    ) {
        check(!started) { "Program stream session already started" }
        require(endpoint.isNotBlank()) { "RTMP endpoint is required" }

        this.endpoint = endpoint
        started = true
        transport.configure(profile.width, profile.height, profile.fps)
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

        // The phone microphone is deliberately NOT opened for poster/music
        // streams. Only the selected local audio file is encoded into the RTMP mix.
        val selectedAudioUri = audioUri?.takeIf { it.isNotBlank() }
        audioExpected = selectedAudioUri != null
        if (selectedAudioUri != null) {
            val appContext = context
            if (appContext != null) {
                val fileAudio = StudioFileAudioSource(appContext, Uri.parse(selectedAudioUri))
                audio = fileAudio
                fileAudio.start(object : StudioFileAudioSource.Listener {
                    override fun onFormat(format: MediaFormat) {
                        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        transport.configureAudio(sampleRate, channels > 1, 128)
                        transport.setAudioFormat(format)
                        audioFormatReady = true
                        maybeConnect()
                    }

                    override fun onError(reason: String) {
                        audioFormatReady = false
                        android.util.Log.e("ProgramStreamSession", "Music audio unavailable: $reason")
                        audioExpected = false
                        maybeConnect()
                        // Keep the video-only live stream alive if this audio file is unsupported.
                    }

                    override fun onFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
                        transport.sendAudio(buffer, info)
                    }
                })
            } else {
                android.util.Log.e("ProgramStreamSession", "Audio source context is unavailable")
                audioExpected = false
                maybeConnect()
            }
        }
    }

    @Synchronized
    private fun maybeConnect() {
        if (!started || !videoFormatReady || (audioExpected && !audioFormatReady)) return
        val url = endpoint ?: return
        if (!transport.isStreaming()) transport.connect(url)
    }

    @Synchronized
    fun stop() {
        if (!started) return
        audio?.stop()
        audio = null
        renderPipeline.detachProgramOutputSurface()
        transport.stop()
        encoder.stop()
        endpoint = null
        videoFormatReady = false
        audioFormatReady = false
        audioExpected = false
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
