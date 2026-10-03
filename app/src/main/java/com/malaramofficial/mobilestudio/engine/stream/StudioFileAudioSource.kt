package com.malaramofficial.mobilestudio.engine.stream

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.pedro.encoder.audio.AudioEncoder
import com.pedro.encoder.audio.GetAudioData
import com.pedro.encoder.input.sources.audio.AudioFileSource
import java.nio.ByteBuffer

/**
 * Decodes a user-selected local audio file and feeds PCM into RootEncoder's AAC
 * encoder. AudioFileSource loops the track and does not play it through the
 * phone speaker unless playAudioDevice() is explicitly called (we never do).
 */
class StudioFileAudioSource(
    private val context: Context,
    private val uri: Uri,
    private val bitrateKbps: Int = 128
) {
    interface Listener {
        fun onFormat(format: MediaFormat) {}
        fun onFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {}
        fun onError(reason: String) {}
    }

    private var listener: Listener? = null
    private var source: AudioFileSource? = null
    private var started = false

    private val encoder = AudioEncoder(object : GetAudioData {
        override fun getAudioData(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            listener?.onFrame(audioBuffer, info)
        }

        override fun onAudioFormat(mediaFormat: MediaFormat) {
            listener?.onFormat(mediaFormat)
        }
    })

    fun start(listener: Listener) {
        check(!started) { "File audio source is already started" }
        this.listener = listener
        try {
            val (sampleRate, channelCount) = readAudioFormat()
            require(sampleRate > 0) { "Audio file has an invalid sample rate" }
            require(channelCount in 1..2) { "Please choose a mono or stereo audio file" }
            val stereo = channelCount == 2

            val fileSource = AudioFileSource(context, uri, true)
            check(fileSource.init(sampleRate, stereo, false, false)) {
                "Unable to initialize selected audio file"
            }
            source = fileSource

            check(encoder.prepareAudioEncoder(bitrateKbps * 1000, sampleRate, stereo)) {
                "AAC encoder could not be prepared for this audio file"
            }
            // Encoder must be ready before the decoder begins delivering PCM.
            encoder.start()
            fileSource.start(encoder)
            started = true
        } catch (t: Throwable) {
            stop()
            listener.onError(t.message ?: "Unable to play selected audio file")
        }
    }

    private fun readAudioFormat(): Pair<Int, Int> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    return sampleRate to channels
                }
            }
            throw IllegalArgumentException("Selected file does not contain an audio track")
        } finally {
            extractor.release()
        }
    }

    fun stop() {
        source?.let { audioSource ->
            runCatching { audioSource.stop() }
            runCatching { audioSource.release() }
        }
        source = null
        if (encoder.isRunning) runCatching { encoder.stop() }
        listener = null
        started = false
    }
}
