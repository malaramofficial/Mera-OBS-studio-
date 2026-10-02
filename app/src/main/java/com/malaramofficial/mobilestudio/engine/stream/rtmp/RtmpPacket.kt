package com.malaramofficial.mobilestudio.engine.stream.rtmp

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream

/**
 * RTMP packet and chunk formatting utility.
 */
class RtmpPacket(
    val messageType: Int,
    val chunkStreamId: Int,
    val timestamp: Int,
    val data: ByteArray,
    val messageStreamId: Int = 1
) {
    companion object {
        const val TYPE_SET_CHUNK_SIZE = 1
        const val TYPE_ABORT = 2
        const val TYPE_ACKNOWLEDGEMENT = 3
        const val TYPE_USER_CONTROL = 4
        const val TYPE_WINDOW_ACK_SIZE = 5
        const val TYPE_SET_PEER_BANDWIDTH = 6
        const val TYPE_AUDIO = 8
        const val TYPE_VIDEO = 9
        const val TYPE_DATA_AMF0 = 18
        const val TYPE_INVOKE_AMF0 = 20

        const val CSID_PROTOCOL = 2
        const val CSID_COMMAND = 3
        const val CSID_AUDIO = 4
        const val CSID_VIDEO = 6

        const val DEFAULT_CHUNK_SIZE = 4096

        fun writeChunk(
            out: OutputStream,
            packet: RtmpPacket,
            chunkSize: Int = DEFAULT_CHUNK_SIZE
        ) {
            val data = packet.data
            var offset = 0
            var remaining = data.size
            var isFirstChunk = true

            while (remaining > 0) {
                val currentChunkLength = minOf(remaining, chunkSize)

                if (isFirstChunk) {
                    // Type 0 Chunk Header (11 bytes message header + 1 byte basic header)
                    val basicHeader = (0 shl 6) or (packet.chunkStreamId and 0x3F)
                    out.write(basicHeader)

                    // 3-byte timestamp
                    val ts = if (packet.timestamp >= 0xFFFFFF) 0xFFFFFF else packet.timestamp
                    out.write((ts shr 16) and 0xFF)
                    out.write((ts shr 8) and 0xFF)
                    out.write(ts and 0xFF)

                    // 3-byte message length
                    out.write((data.size shr 16) and 0xFF)
                    out.write((data.size shr 8) and 0xFF)
                    out.write(data.size and 0xFF)

                    // 1-byte message type
                    out.write(packet.messageType and 0xFF)

                    // 4-byte message stream ID (little-endian)
                    out.write(packet.messageStreamId and 0xFF)
                    out.write((packet.messageStreamId shr 8) and 0xFF)
                    out.write((packet.messageStreamId shr 16) and 0xFF)
                    out.write((packet.messageStreamId shr 24) and 0xFF)

                    // Extended timestamp if >= 0xFFFFFF
                    if (packet.timestamp >= 0xFFFFFF) {
                        out.write((packet.timestamp shr 24) and 0xFF)
                        out.write((packet.timestamp shr 16) and 0xFF)
                        out.write((packet.timestamp shr 8) and 0xFF)
                        out.write(packet.timestamp and 0xFF)
                    }
                    isFirstChunk = false
                } else {
                    // Type 3 Chunk Header (continuation, only 1 byte basic header)
                    val basicHeader = (3 shl 6) or (packet.chunkStreamId and 0x3F)
                    out.write(basicHeader)

                    if (packet.timestamp >= 0xFFFFFF) {
                        out.write((packet.timestamp shr 24) and 0xFF)
                        out.write((packet.timestamp shr 16) and 0xFF)
                        out.write((packet.timestamp shr 8) and 0xFF)
                        out.write(packet.timestamp and 0xFF)
                    }
                }

                out.write(data, offset, currentChunkLength)
                offset += currentChunkLength
                remaining -= currentChunkLength
            }
            out.flush()
        }
    }
}
