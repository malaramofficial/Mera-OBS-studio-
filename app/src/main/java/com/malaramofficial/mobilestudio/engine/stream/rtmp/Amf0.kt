package com.malaramofficial.mobilestudio.engine.stream.rtmp

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

/**
 * Lightweight AMF0 (Action Message Format 0) serializer for RTMP command transactions.
 */
object Amf0 {

    private const val TYPE_NUMBER: Byte = 0x00
    private const val TYPE_BOOLEAN: Byte = 0x01
    private const val TYPE_STRING: Byte = 0x02
    private const val TYPE_OBJECT: Byte = 0x03
    private const val TYPE_NULL: Byte = 0x05
    private const val TYPE_ECMA_ARRAY: Byte = 0x08
    private const val TYPE_OBJECT_END: Byte = 0x09

    fun writeString(out: DataOutputStream, value: String) {
        out.writeByte(TYPE_STRING.toInt())
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        out.writeShort(bytes.size)
        out.write(bytes)
    }

    fun writeNumber(out: DataOutputStream, value: Double) {
        out.writeByte(TYPE_NUMBER.toInt())
        out.writeDouble(value)
    }

    fun writeBoolean(out: DataOutputStream, value: Boolean) {
        out.writeByte(TYPE_BOOLEAN.toInt())
        out.writeByte(if (value) 1 else 0)
    }

    fun writeNull(out: DataOutputStream) {
        out.writeByte(TYPE_NULL.toInt())
    }

    fun writeObject(out: DataOutputStream, properties: Map<String, Any?>) {
        out.writeByte(TYPE_OBJECT.toInt())
        for ((key, value) in properties) {
            val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
            out.writeShort(keyBytes.size)
            out.write(keyBytes)
            writeValue(out, value)
        }
        // Object end marker: empty string (0x0000) followed by 0x09
        out.writeShort(0)
        out.writeByte(TYPE_OBJECT_END.toInt())
    }

    fun writeEcmaArray(out: DataOutputStream, properties: Map<String, Any?>) {
        out.writeByte(TYPE_ECMA_ARRAY.toInt())
        out.writeInt(properties.size)
        for ((key, value) in properties) {
            val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
            out.writeShort(keyBytes.size)
            out.write(keyBytes)
            writeValue(out, value)
        }
        out.writeShort(0)
        out.writeByte(TYPE_OBJECT_END.toInt())
    }

    fun writeValue(out: DataOutputStream, value: Any?) {
        when (value) {
            null -> writeNull(out)
            is String -> writeString(out, value)
            is Number -> writeNumber(out, value.toDouble())
            is Boolean -> writeBoolean(out, value)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                writeObject(out, value as Map<String, Any?>)
            }
            else -> writeString(out, value.toString())
        }
    }

    fun createCommand(commandName: String, transactionId: Double, commandObject: Any?, vararg args: Any?): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        writeString(dos, commandName)
        writeNumber(dos, transactionId)
        writeValue(dos, commandObject)
        for (arg in args) {
            writeValue(dos, arg)
        }
        dos.flush()
        return baos.toByteArray()
    }
}
