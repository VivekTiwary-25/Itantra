package com.chmod777.itantra.protocol

import com.chmod777.itantra.protocol.cbor.MalformedInputException

/**
 * Link-frame typing: `type(1) || body`. This is the only structure below Noise.
 *
 * - HELLO (plaintext) carries only data already public in the advertisement, so
 *   links can be de-duplicated after a connection collision.
 * - After HELLO, only Noise handshake/transport frames are accepted, so no
 *   protocol frame can ever travel in plaintext (spec §9, Phase 3 acceptance).
 */
enum class LinkFrameType(val wire: Int) {
    HELLO(0x01),
    NOISE_HANDSHAKE(0x02),
    NOISE_TRANSPORT(0x03);

    companion object {
        fun fromWire(value: Int): LinkFrameType? = entries.firstOrNull { it.wire == value }
    }
}

class LinkFrame(val type: LinkFrameType, val body: ByteArray) {
    fun encode(): ByteArray = byteArrayOf(type.wire.toByte()) + body

    companion object {
        const val HELLO_VERSION = 1
        const val SHORT_ID_BYTES = 8

        @Throws(MalformedInputException::class)
        fun decode(bytes: ByteArray): LinkFrame {
            if (bytes.isEmpty()) throw MalformedInputException("empty link frame")
            val type = LinkFrameType.fromWire(bytes[0].toInt() and 0xFF)
                ?: throw MalformedInputException("unknown link frame type ${bytes[0]}")
            return LinkFrame(type, bytes.copyOfRange(1, bytes.size))
        }

        fun hello(shortId: ByteArray, attMtu: Int? = null): LinkFrame {
            require(shortId.size == SHORT_ID_BYTES)
            val mtu = attMtu?.let { byteArrayOf((it ushr 8).toByte(), it.toByte()) } ?: ByteArray(0)
            return LinkFrame(LinkFrameType.HELLO, byteArrayOf(HELLO_VERSION.toByte()) + shortId + mtu)
        }

        /**
         * Returns the peer's advertised short ID and, if present, the ATT MTU the GATT
         * client negotiated. Android does not report MTU to a GATT server when a client
         * reuses an existing LE connection, so the client states it here (optional u16).
         */
        @Throws(MalformedInputException::class)
        fun parseHello(frame: LinkFrame): Hello {
            if (frame.type != LinkFrameType.HELLO) throw MalformedInputException("expected HELLO")
            val body = frame.body
            if ((body.size != 1 + SHORT_ID_BYTES && body.size != 3 + SHORT_ID_BYTES) || body[0].toInt() != HELLO_VERSION) {
                throw MalformedInputException("malformed HELLO")
            }
            val mtu = if (body.size == 3 + SHORT_ID_BYTES) {
                ((body[9].toInt() and 0xFF) shl 8) or (body[10].toInt() and 0xFF)
            } else {
                null
            }
            return Hello(body.copyOfRange(1, 1 + SHORT_ID_BYTES), mtu)
        }
    }
}

class Hello(val shortId: ByteArray, val attMtu: Int?)
