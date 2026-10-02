package com.malaramofficial.mobilestudio.engine.stream.encoder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.malaramofficial.mobilestudio.domain.model.stream.StreamConfig
import java.nio.ByteBuffer

/**
 * Audio recorder and hardware AAC encoder for live broadcast.
 * Ingests microphone audio at 48kHz and feeds AAC frames into the RTMP stream.
 */
class AudioEncoder(
    private val config: StreamConfig
) {
    private var codec: MediaCodec? = null
    private var audioRecord: AudioRecord? = null

    private val audioThread = HandlerThread("StudioAudioEncoder").apply { start() }
    private val audioHandler = Handler(audioThread.looper)
    private var isRunning = false

    var onAudioSequenceHeaderReady: ((Int, Int) -> Unit)? = null
    var onAudioFrameEncoded: ((ByteArray, Long) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        return try {
            val sampleRate = config.audioSampleRate
            val channelConfig = AudioFormat.CHANNEL_IN_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord could not be initialized")
                return false
            }

            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                2
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, config.audioBitrateKbps * 1000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            audioRecord = recorder
            codec = encoder
            isRunning = true

            onAudioSequenceHeaderReady?.invoke(sampleRate, 2)
            recorder.startRecording()

            audioHandler.post {
                recordAndEncodeLoop(recorder, encoder, bufferSize)
            }
            Log.i(TAG, "AudioEncoder started @ ${sampleRate}Hz, ${config.audioBitrateKbps}kbps")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioEncoder", e)
            stop()
            false
        }
    }

    private fun recordAndEncodeLoop(recorder: AudioRecord, encoder: MediaCodec, bufferSize: Int) {
        val pcmBuffer = ByteArray(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        while (isRunning) {
            try {
                val readBytes = recorder.read(pcmBuffer, 0, pcmBuffer.size)
                if (readBytes > 0) {
                    val inputIndex = encoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            inputBuffer.put(pcmBuffer, 0, readBytes)
                            val pts = System.nanoTime() / 1000
                            encoder.queueInputBuffer(inputIndex, 0, readBytes, pts, 0)
                        }
                    }
                }

                var outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                while (outputIndex >= 0) {
                    val outBuffer = encoder.getOutputBuffer(outputIndex)
                    if (outBuffer != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        val aacData = ByteArray(bufferInfo.size)
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.get(aacData)
                        val timestampMs = bufferInfo.presentationTimeUs / 1000
                        onAudioFrameEncoded?.invoke(aacData, timestampMs)
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.w(TAG, "Error in audio encoding loop", e)
                }
                break
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null

        try {
            codec?.stop()
            codec?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio MediaCodec", e)
        }
        codec = null
        audioThread.quitSafely()
    }

    companion object {
        private const val TAG = "AudioEncoder"
    }
}
