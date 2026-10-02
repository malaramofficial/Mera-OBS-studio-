package com.malaramofficial.mobilestudio.engine.stream

import android.media.MediaCodec
import android.media.MediaFormat
import com.pedro.encoder.audio.AudioEncoder
import com.pedro.encoder.audio.GetAudioData
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import java.nio.ByteBuffer

/**
 * Captures microphone PCM with RootEncoder and encodes it to AAC.
 *
 * System/game audio is intentionally not mixed here yet; this is the stable
 * microphone leg of the Program audio path.
 */
class StudioMicrophoneAudio(
    private val sampleRate: Int = 48_000,
    private val stereo: Boolean = true,
    private val bitrateKbps: Int = 128
) {

    interface Listener {
        fun onFormat(format: MediaFormat) {}
        fun onFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {}
        fun onError(reason: String) {}
    }

    private var listener: Listener? = null
    private var started = false

    private val microphone = MicrophoneSource()
    private val encoder = AudioEncoder(object : GetAudioData {
        override fun getAudioData(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            listener?.onFrame(audioBuffer, info)
        }

        override fun onAudioFormat(mediaFormat: MediaFormat) {
            listener?.onFormat(mediaFormat)
        }
    })

    fun start(listener: Listener) {
        check(!started) { "Microphone audio is already started" }
        this.listener = listener

        try {
            check(microphone.init(sampleRate, stereo, false, false)) {
                "Microphone initialization failed"
            }
            check(encoder.prepareAudioEncoder(bitrateKbps * 1000, sampleRate, stereo)) {
                "AAC encoder initialization failed"
            }

            // Start the encoder before the source so incoming PCM is not lost.
            encoder.start()
            microphone.start(encoder)
            started = true
        } catch (t: Throwable) {
            stop()
            listener.onError(t.message ?: "Microphone audio initialization failed")
        }
    }

    fun stop() {
        if (!started && !microphone.isRunning() && !encoder.isRunning) return
        runCatching { microphone.stop() }
        runCatching { encoder.stop() }
        runCatching { microphone.release() }
        listener = null
        started = false
    }
}
