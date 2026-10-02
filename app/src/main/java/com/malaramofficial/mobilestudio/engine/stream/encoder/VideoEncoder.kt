package com.malaramofficial.mobilestudio.engine.stream.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.malaramofficial.mobilestudio.domain.model.stream.StreamConfig
import java.nio.ByteBuffer

/**
 * Hardware H.264 video encoder consuming directly from an OpenGL surface.
 * Bypasses CPU memory copies and outputs NAL units for RTMP broadcasting.
 */
class VideoEncoder(
    private val config: StreamConfig
) {
    private var codec: MediaCodec? = null
    var inputSurface: Surface? = null
        private set

    private val encoderThread = HandlerThread("StudioVideoEncoder").apply { start() }
    private val encoderHandler = Handler(encoderThread.looper)
    private var isRunning = false

    var onSpsPpsReady: ((ByteArray, ByteArray) -> Unit)? = null
    var onFrameEncoded: ((ByteArray, Boolean, Long) -> Unit)? = null

    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    private var startTimeNs: Long = 0

    fun start(): Boolean {
        return try {
            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                config.width,
                config.height
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrateKbps * 1000)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.keyframeIntervalSec)
                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                )
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            codec = encoder
            isRunning = true
            startTimeNs = System.nanoTime()

            encoderHandler.post {
                pollEncodedBuffers()
            }
            Log.i(TAG, "VideoEncoder started: ${config.width}x${config.height} @ ${config.fps}fps, ${config.videoBitrateKbps}kbps")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VideoEncoder", e)
            stop()
            false
        }
    }

    private fun pollEncodedBuffers() {
        val bufferInfo = MediaCodec.BufferInfo()
        val encoder = codec ?: return

        while (isRunning) {
            try {
                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputIndex >= 0) {
                    val buffer = encoder.getOutputBuffer(outputIndex)
                    if (buffer != null) {
                        handleOutputBuffer(buffer, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.w(TAG, "Error dequeuing buffer from video encoder", e)
                }
                break
            }
        }
    }

    private fun handleOutputBuffer(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // Extract SPS / PPS from configuration buffer
            val configBytes = ByteArray(info.size)
            buffer.position(info.offset)
            buffer.get(configBytes)
            extractSpsPps(configBytes)
            return
        }

        val isKeyFrame = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
        val timestampMs = (info.presentationTimeUs / 1000).coerceAtLeast(0)

        // Read NALU data (skip 4-byte start code 0x00 0x00 0x00 0x01 if present)
        buffer.position(info.offset)
        var offset = info.offset
        var size = info.size
        if (size > 4 && buffer.get(offset) == 0.toByte() && buffer.get(offset + 1) == 0.toByte() &&
            buffer.get(offset + 2) == 0.toByte() && buffer.get(offset + 3) == 1.toByte()
        ) {
            offset += 4
            size -= 4
        }

        val frameData = ByteArray(size)
        buffer.position(offset)
        buffer.get(frameData)

        onFrameEncoded?.invoke(frameData, isKeyFrame, timestampMs)
    }

    private fun extractSpsPps(configBytes: ByteArray) {
        // Look for 0x00 0x00 0x00 0x01 NAL separators
        val nalPositions = mutableListOf<Int>()
        for (i in 0 until configBytes.size - 4) {
            if (configBytes[i] == 0.toByte() && configBytes[i + 1] == 0.toByte() &&
                configBytes[i + 2] == 0.toByte() && configBytes[i + 3] == 1.toByte()
            ) {
                nalPositions.add(i)
            }
        }

        if (nalPositions.size >= 2) {
            val spsStart = nalPositions[0] + 4
            val spsEnd = nalPositions[1]
            val ppsStart = nalPositions[1] + 4
            val ppsEnd = configBytes.size

            sps = configBytes.copyOfRange(spsStart, spsEnd)
            pps = configBytes.copyOfRange(ppsStart, ppsEnd)

            val s = sps
            val p = pps
            if (s != null && p != null) {
                onSpsPpsReady?.invoke(s, p)
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            codec?.stop()
            codec?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MediaCodec", e)
        }
        codec = null
        inputSurface = null
        encoderThread.quitSafely()
    }

    companion object {
        private const val TAG = "VideoEncoder"
    }
}
