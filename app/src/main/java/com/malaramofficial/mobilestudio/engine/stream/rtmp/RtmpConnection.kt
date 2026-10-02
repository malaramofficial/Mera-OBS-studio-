package com.malaramofficial.mobilestudio.engine.stream.rtmp

import android.util.Log
import com.malaramofficial.mobilestudio.domain.model.stream.StreamConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.SSLSocketFactory

/**
 * Robust RTMP / RTMPS client connection for YouTube Live.
 * Handles handshake, session negotiation, and media chunk broadcasting.
 */
class RtmpConnection {

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var isConnected = false
    private var isPublishing = false
    private val random = SecureRandom()
    private var chunkSize = RtmpPacket.DEFAULT_CHUNK_SIZE

    var onStateChanged: ((Boolean, String?) -> Unit)? = null

    /**
     * Connects and publishes to the configured RTMP endpoint (e.g. YouTube Live).
     */
    fun connectAndPublish(config: StreamConfig): Boolean {
        return try {
            val url = config.serverUrl.trim()
            val isRtmps = url.startsWith("rtmps://", ignoreCase = true)
            val withoutScheme = url.substringAfter("://")
            val hostAndPort = withoutScheme.substringBefore("/")
            val app = withoutScheme.substringAfter("/", "live2")

            val host = hostAndPort.substringBefore(":")
            val port = if (hostAndPort.contains(":")) {
                hostAndPort.substringAfter(":").toIntOrNull() ?: if (isRtmps) 443 else 1935
            } else {
                if (isRtmps) 443 else 1935
            }

            Log.i(TAG, "Connecting to YouTube RTMP: host=$host, port=$port, app=$app, isRtmps=$isRtmps")

            val rawSocket = if (isRtmps) {
                SSLSocketFactory.getDefault().createSocket()
            } else {
                Socket()
            }
            rawSocket.connect(InetSocketAddress(host, port), 10000)
            rawSocket.tcpNoDelay = true
            rawSocket.soTimeout = 15000

            socket = rawSocket
            inputStream = BufferedInputStream(rawSocket.getInputStream(), 32768)
            outputStream = BufferedOutputStream(rawSocket.getOutputStream(), 32768)

            doHandshake()
            sendSetChunkSize(chunkSize)
            sendConnect(app, url)
            sendCreateStream()
            sendPublish(config.streamKey)
            sendMetadata(config)

            isConnected = true
            isPublishing = true
            onStateChanged?.invoke(true, null)
            Log.i(TAG, "Successfully connected and publishing to YouTube Live!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed RTMP connection to YouTube", e)
            close()
            onStateChanged?.invoke(false, e.message)
            false
        }
    }

    private fun doHandshake() {
        val out = outputStream ?: return
        val inp = inputStream ?: return

        // C0: 0x03 (RTMP version 3)
        out.write(0x03)

        // C1: 1536 bytes (4-byte timestamp, 4-byte zeroes, 1528-byte random)
        val c1 = ByteArray(1536)
        random.nextBytes(c1)
        c1[0] = 0; c1[1] = 0; c1[2] = 0; c1[3] = 0 // timestamp
        c1[4] = 0; c1[5] = 0; c1[6] = 0; c1[7] = 0 // zeroes
        out.write(c1)
        out.flush()

        // S0: 1 byte
        val s0 = inp.read()
        if (s0 != 0x03) {
            throw IllegalStateException("Invalid S0 version: $s0")
        }

        // S1: 1536 bytes
        val s1 = ByteArray(1536)
        readFully(inp, s1)

        // S2: 1536 bytes
        val s2 = ByteArray(1536)
        readFully(inp, s2)

        // C2: Echo S1
        out.write(s1)
        out.flush()
    }

    private fun readFully(inp: InputStream, buffer: ByteArray) {
        var readTotal = 0
        while (readTotal < buffer.size) {
            val count = inp.read(buffer, readTotal, buffer.size - readTotal)
            if (count < 0) throw IllegalStateException("Premature EOF during handshake")
            readTotal += count
        }
    }

    private fun sendSetChunkSize(size: Int) {
        val out = outputStream ?: return
        val data = ByteArray(4).apply {
            this[0] = ((size shr 24) and 0x7F).toByte()
            this[1] = ((size shr 16) and 0xFF).toByte()
            this[2] = ((size shr 8) and 0xFF).toByte()
            this[3] = (size and 0xFF).toByte()
        }
        val packet = RtmpPacket(
            messageType = RtmpPacket.TYPE_SET_CHUNK_SIZE,
            chunkStreamId = RtmpPacket.CSID_PROTOCOL,
            timestamp = 0,
            data = data,
            messageStreamId = 0
        )
        RtmpPacket.writeChunk(out, packet, chunkSize)
    }

    private fun sendConnect(app: String, tcUrl: String) {
        val out = outputStream ?: return
        val connectParams = mapOf(
            "app" to app,
            "flashVer" to "FMLE/3.0 (compatible; FMSc/1.0)",
            "swfUrl" to "",
            "tcUrl" to tcUrl,
            "fpad" to false,
            "capabilities" to 15.0,
            "audioCodecs" to 3191.0,
            "videoCodecs" to 252.0,
            "videoFunction" to 1.0
        )
        val data = Amf0.createCommand("connect", 1.0, connectParams)
        val packet = RtmpPacket(
            messageType = RtmpPacket.TYPE_INVOKE_AMF0,
            chunkStreamId = RtmpPacket.CSID_COMMAND,
            timestamp = 0,
            data = data,
            messageStreamId = 0
        )
        RtmpPacket.writeChunk(out, packet, chunkSize)
    }

    private fun sendCreateStream() {
        val out = outputStream ?: return
        val data = Amf0.createCommand("createStream", 2.0, null)
        val packet = RtmpPacket(
            messageType = RtmpPacket.TYPE_INVOKE_AMF0,
            chunkStreamId = RtmpPacket.CSID_COMMAND,
            timestamp = 0,
            data = data,
            messageStreamId = 0
        )
        RtmpPacket.writeChunk(out, packet, chunkSize)
    }

    private fun sendPublish(streamKey: String) {
        val out = outputStream ?: return
        val data = Amf0.createCommand("publish", 0.0, null, streamKey, "live")
        val packet = RtmpPacket(
            messageType = RtmpPacket.TYPE_INVOKE_AMF0,
            chunkStreamId = RtmpPacket.CSID_COMMAND,
            timestamp = 0,
            data = data,
            messageStreamId = 1
        )
        RtmpPacket.writeChunk(out, packet, chunkSize)
    }

    private fun sendMetadata(config: StreamConfig) {
        val out = outputStream ?: return
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        Amf0.writeString(dos, "@setDataFrame")
        Amf0.writeString(dos, "onMetaData")

        val meta = mapOf(
            "width" to config.width.toDouble(),
            "height" to config.height.toDouble(),
            "framerate" to config.fps.toDouble(),
            "videocodecid" to 7.0, // AVC / H.264
            "videodatarate" to config.videoBitrateKbps.toDouble(),
            "audiocodecid" to 10.0, // AAC
            "audiosamplerate" to config.audioSampleRate.toDouble(),
            "audiosamplesize" to 16.0,
            "stereo" to true
        )
        Amf0.writeEcmaArray(dos, meta)
        dos.flush()

        val packet = RtmpPacket(
            messageType = RtmpPacket.TYPE_DATA_AMF0,
            chunkStreamId = RtmpPacket.CSID_COMMAND,
            timestamp = 0,
            data = baos.toByteArray(),
            messageStreamId = 1
        )
        RtmpPacket.writeChunk(out, packet, chunkSize)
    }

    /**
     * Sends H.264 AVCDecoderConfigurationRecord (SPS + PPS) sequence header.
     */
    fun sendVideoSequenceHeader(sps: ByteArray, pps: ByteArray) {
        val out = outputStream ?: return
        val baos = ByteArrayOutputStream()
        // FLV Video Tag Header: FrameType=1 (Keyframe), CodecID=7 (AVC) -> 0x17
        baos.write(0x17)
        // AVCPacketType = 0 (AVC sequence header)
        baos.write(0x00)
        // CompositionTime = 0 (3 bytes)
        baos.write(0x00); baos.write(0x00); baos.write(0x00)

        // AVCDecoderConfigurationRecord
        baos.write(0x01) // configurationVersion = 1
        baos.write(sps[1].toInt()) // AVCProfileIndication
        baos.write(sps[2].toInt()) // profile_compatibility
        baos.write(sps[3].toInt()) // AVCLevelIndication
        baos.write(0xFF) // lengthSizeMinusOne = 3 (4-byte NALU length)

        // SPS
        baos.write(0xE1) // numOfSequenceParameterSets = 1
        baos.write((sps.size shr 8) and 0xFF)
        baos.write(sps.size and 0xFF)
        baos.write(sps)

        // PPS
        baos.write(0x01) // numOfPictureParameterSets = 1
        baos.write((pps.size shr 8) and 0xFF)
        baos.write(pps.size and 0xFF)
        baos.write(pps)

        val packet = RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            chunkStreamId = RtmpPacket.CSID_VIDEO,
            timestamp = 0,
            data = baos.toByteArray(),
            messageStreamId = 1
        )
        synchronized(this) {
            RtmpPacket.writeChunk(out, packet, chunkSize)
        }
    }

    /**
     * Sends an H.264 video NALU frame packet.
     */
    fun sendVideoPacket(data: ByteArray, isKeyFrame: Boolean, timestampMs: Long) {
        val out = outputStream ?: return
        if (!isPublishing) return

        val baos = ByteArrayOutputStream(data.size + 9)
        // 0x17 for Keyframe, 0x27 for Interframe
        baos.write(if (isKeyFrame) 0x17 else 0x27)
        // AVCPacketType = 1 (AVC NALU)
        baos.write(0x01)
        // CompositionTime = 0
        baos.write(0x00); baos.write(0x00); baos.write(0x00)

        // NALU Length (4 bytes)
        baos.write((data.size shr 24) and 0xFF)
        baos.write((data.size shr 16) and 0xFF)
        baos.write((data.size shr 8) and 0xFF)
        baos.write(data.size and 0xFF)
        baos.write(data)

        val packet = RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            chunkStreamId = RtmpPacket.CSID_VIDEO,
            timestamp = timestampMs.toInt(),
            data = baos.toByteArray(),
            messageStreamId = 1
        )
        synchronized(this) {
            RtmpPacket.writeChunk(out, packet, chunkSize)
        }
    }

    /**
     * Sends AAC sequence header (AudioSpecificConfig).
     */
    fun sendAudioSequenceHeader(sampleRate: Int = 48000, channels: Int = 2) {
        val out = outputStream ?: return
        val baos = ByteArrayOutputStream()
        // FLV Audio Tag: Format=10 (AAC), Rate=3 (44kHz/48kHz), Size=1 (16-bit), Type=1 (Stereo) -> 0xAF
        baos.write(0xAF)
        // AACPacketType = 0 (AAC sequence header)
        baos.write(0x00)

        // AudioSpecificConfig (2 bytes: profile=2 (LC), freqIndex=3 (48kHz), channelConfig=2)
        val profile = 2 // AAC-LC
        val freqIdx = when (sampleRate) {
            96000 -> 0; 88200 -> 1; 64000 -> 2; 48000 -> 3; 44100 -> 4; 32000 -> 5; else -> 3
        }
        val byte1 = ((profile shl 3) or (freqIdx shr 1)).toByte()
        val byte2 = (((freqIdx and 0x01) shl 7) or (channels shl 3)).toByte()
        baos.write(byte1.toInt())
        baos.write(byte2.toInt())

        val packet = RtmpPacket(
            messageType = RtmpPacket.TYPE_AUDIO,
            chunkStreamId = RtmpPacket.CSID_AUDIO,
            timestamp = 0,
            data = baos.toByteArray(),
            messageStreamId = 1
        )
        synchronized(this) {
            RtmpPacket.writeChunk(out, packet, chunkSize)
        }
    }

    /**
     * Sends an encoded AAC audio frame packet.
     */
    fun sendAudioPacket(data: ByteArray, timestampMs: Long) {
        val out = outputStream ?: return
        if (!isPublishing) return

        val baos = ByteArrayOutputStream(data.size + 2)
        baos.write(0xAF)
        baos.write(0x01) // AACPacketType = 1 (AAC raw)
        baos.write(data)

        val packet = RtmpPacket(
            messageType = RtmpPacket.TYPE_AUDIO,
            chunkStreamId = RtmpPacket.CSID_AUDIO,
            timestamp = timestampMs.toInt(),
            data = baos.toByteArray(),
            messageStreamId = 1
        )
        synchronized(this) {
            RtmpPacket.writeChunk(out, packet, chunkSize)
        }
    }

    fun close() {
        isPublishing = false
        isConnected = false
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing socket", e)
        }
        socket = null
        inputStream = null
        outputStream = null
    }

    companion object {
        private const val TAG = "RtmpConnection"
    }
}
