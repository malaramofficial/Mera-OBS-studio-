package com.malaramofficial.mobilestudio.engine.stream

import android.media.MediaCodec
import android.media.MediaFormat
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.rtmp.rtmp.RtmpClient
import com.malaramofficial.mobilestudio.engine.encoder.H264ProgramEncoder
import java.nio.ByteBuffer

/**
 * RTMP transport for the already-composited Program video plus AAC audio.
 */
class RtmpVideoTransport(
    private val listener: Listener = object : Listener {}
) {

    interface Listener {
        fun onStarted(url: String) {}
        fun onConnected() {}
        fun onFailed(reason: String) {}
        fun onDisconnected() {}
        fun onAuthError() {}
        fun onAuthSuccess() {}
        fun onBitrate(bitrate: Long) {}
    }

    private val client = RtmpClient(object : ConnectChecker {
        override fun onConnectionStarted(url: String) = listener.onStarted(url)
        override fun onConnectionSuccess() = listener.onConnected()
        override fun onConnectionFailed(reason: String) = listener.onFailed(reason)
        override fun onDisconnect() = listener.onDisconnected()
        override fun onAuthError() = listener.onAuthError()
        override fun onAuthSuccess() = listener.onAuthSuccess()
        override fun onNewBitrate(bitrate: Long) = listener.onBitrate(bitrate)
    })

    fun configure(width: Int, height: Int, fps: Int) {
        client.setVideoCodec(VideoCodec.H264)
        client.setOnlyVideo(true)
        client.setVideoResolution(width, height)
        client.setFps(fps)
    }

    fun configureAudio(sampleRate: Int, isStereo: Boolean, bitrateKbps: Int) {
        require(sampleRate > 0) { "Audio sample rate must be positive" }
        require(bitrateKbps > 0) { "Audio bitrate must be positive" }
        client.setOnlyVideo(false)
        client.setAudioCodec(AudioCodec.AAC)
        client.setAudioInfo(sampleRate, isStereo)
    }

    fun setRetryCount(count: Int) {
        client.setReTries(count)
    }

    fun setVideoFormat(format: MediaFormat) {
        val sps = format.getByteBuffer("csd-0")?.copyBuffer()
        val pps = format.getByteBuffer("csd-1")?.copyBuffer()
        if (sps != null) client.setVideoInfo(sps, pps, null)
    }

    fun setAudioFormat(format: MediaFormat) {
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        client.setAudioInfo(sampleRate, channels > 1)
    }

    fun connect(endpoint: String) {
        require(endpoint.startsWith("rtmp://", ignoreCase = true) ||
            endpoint.startsWith("rtmps://", ignoreCase = true) ||
            endpoint.startsWith("rtmpt://", ignoreCase = true) ||
            endpoint.startsWith("rtmpts://", ignoreCase = true)) {
            "Unsupported RTMP endpoint"
        }
        client.connect(endpoint)
    }

    fun send(frame: H264ProgramEncoder.EncodedFrame) {
        if (!client.isStreaming) return
        client.sendVideo(ByteBuffer.wrap(frame.data), MediaCodec.BufferInfo().apply {
            offset = 0
            size = frame.data.size
            presentationTimeUs = frame.presentationTimeUs
            flags = frame.flags
        })
    }

    fun sendAudio(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (!client.isStreaming) return
        client.sendAudio(buffer, info)
    }

    fun stop() {
        client.disconnect()
    }

    fun isStreaming(): Boolean = client.isStreaming

    val droppedVideoFrames: Long get() = client.droppedVideoFrames
    val sentVideoFrames: Long get() = client.sentVideoFrames
    val droppedAudioFrames: Long get() = client.droppedAudioFrames
    val sentAudioFrames: Long get() = client.sentAudioFrames
    val bytesSent: Long get() = client.bytesSend

    private fun ByteBuffer.copyBuffer(): ByteBuffer {
        val duplicate = duplicate()
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return ByteBuffer.wrap(bytes)
    }
}
