package com.chmod777.itantra.transport

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

internal const val TRANSPORT_PROTOCOL_VERSION = 2
internal const val ACK_FRAME_MARKER = 0
internal const val MAX_TRANSPORT_PAYLOAD_BYTES = 4_096

internal val TRANSPORT_LANGUAGE_CODES = setOf(
    "en", "hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn",
)

internal fun isSupportedTransportLanguageCode(languageCode: String): Boolean =
    languageCode in TRANSPORT_LANGUAGE_CODES

@Throws(IOException::class)
internal fun writeTransportMessage(output: OutputStream, message: TransportMessage) {
    if (!isSupportedTransportLanguageCode(message.languageCode)) {
        throw IOException("Unsupported language code: ${message.languageCode}.")
    }

    val textBytes = message.text.toByteArray(StandardCharsets.UTF_8)
    DataOutputStream(output).apply {
        writeByte(message.version)
        writeInt(message.messageId.toInt())
        writeByte(message.ttl)
        write(message.languageCode.toByteArray(StandardCharsets.US_ASCII))
        writeShort(textBytes.size)
        write(textBytes)
        flush()
    }
}

@Throws(IOException::class)
internal fun readTransportMessage(input: DataInputStream, version: Int): TransportMessage {
    val messageId = input.readInt().toUInt().toLong()
    val ttl = input.readUnsignedByte()
    val languageBytes = ByteArray(2)
    input.readFully(languageBytes)
    val length = input.readUnsignedShort()
    if (length == 0 || length > MAX_TRANSPORT_PAYLOAD_BYTES) {
        throw IOException("Invalid text length: $length.")
    }
    val textBytes = ByteArray(length)
    input.readFully(textBytes)
    val languageCode = String(languageBytes, StandardCharsets.US_ASCII)
    if (!isSupportedTransportLanguageCode(languageCode)) {
        throw IOException("Unsupported language code: $languageCode.")
    }
    return TransportMessage(
        version = version,
        messageId = messageId,
        ttl = ttl,
        languageCode = languageCode,
        text = String(textBytes, StandardCharsets.UTF_8),
    )
}
