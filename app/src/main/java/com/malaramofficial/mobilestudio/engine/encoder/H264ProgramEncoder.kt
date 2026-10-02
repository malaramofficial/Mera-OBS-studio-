package com.malaramofficial.mobilestudio.engine.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Hardware H.264 encoder fed directly by the GPU compositor's Surface.
 * Encoded access units are delivered to a sink; networking/muxing is kept
 * outside this class so the same encoder can feed RTMP and local recording.
 */
class H264ProgramEncoder {

    data class Config(
        val width: Int = 1080,
        val height: Int = 1920,
        val fps: Int = 30,
        val bitrateKbps: Int = 6000,
        val keyFrameIntervalSeconds: Int = 2
    )

    data class EncodedFrame(
        val data: ByteArray,
        val presentationTimeUs: Long,
        val flags: Int
    )

    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null

    fun start(
        config: Config,
        onFormat: (MediaFormat) -> Unit = {},
        onFrame: (EncodedFrame) -> Unit,
        onError: (Throwable) -> Unit = {}
    ): Surface {
        check(codec == null) { "Encoder is already running" }
        require(config.width > 0 && config.height > 0)
        require(config.fps in 1..120)
        require(config.bitrateKbps > 0)

        val mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            config.width,
            config.height
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitrateKbps * 1000)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.keyFrameIntervalSeconds)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        }

        mediaCodec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                if (info.size <= 0) {
                    codec.releaseOutputBuffer(index, false)
                    return
                }

                try {
                    val buffer: ByteBuffer = codec.getOutputBuffer(index) ?: return
                    val duplicate = buffer.duplicate()
                    duplicate.position(info.offset)
                    duplicate.limit(info.offset + info.size)
                    val bytes = ByteArray(info.size)
                    duplicate.get(bytes)
                    onFrame(
                        EncodedFrame(
                            data = bytes,
                            presentationTimeUs = info.presentationTimeUs,
                            flags = info.flags
                        )
                    )
                } catch (t: Throwable) {
                    onError(t)
                } finally {
                    codec.releaseOutputBuffer(index, false)
                }
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                onFormat(format)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                onError(e)
            }
        })

        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = mediaCodec.createInputSurface()
        mediaCodec.start()
        codec = mediaCodec
        return inputSurface!!
    }

    fun stop() {
        val current = codec ?: return
        try {
            current.stop()
        } catch (_: Exception) {
        }
        try {
            current.release()
        } catch (_: Exception) {
        }
        codec = null

        inputSurface?.release()
        inputSurface = null
    }

    fun isRunning(): Boolean = codec != null

}
