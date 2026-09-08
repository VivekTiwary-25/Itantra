package com.chmod777.itantra.transport

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TransportFrameCodecTest {
    @Test
    fun `all exposed language codes round trip`() {
        val exposedLanguageCodes = listOf("en", "hi", "gu", "mr", "ta", "te", "or", "bn")

        exposedLanguageCodes.forEachIndexed { index, languageCode ->
            val original = TransportMessage(
                version = TRANSPORT_PROTOCOL_VERSION,
                messageId = index.toLong(),
                ttl = 3,
                languageCode = languageCode,
                text = "message-$languageCode",
            )
            val bytes = ByteArrayOutputStream().also { writeTransportMessage(it, original) }.toByteArray()
            val input = DataInputStream(ByteArrayInputStream(bytes))

            assertEquals(TRANSPORT_PROTOCOL_VERSION, input.readUnsignedByte())
            assertEquals(original, readTransportMessage(input, TRANSPORT_PROTOCOL_VERSION))
        }
    }

    @Test
    fun `unsupported language consumes its frame before failing`() {
        val bytes = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).apply {
                writeInt(42)
                writeByte(3)
                writeBytes("zz")
                writeShort(5)
                writeBytes("hello")
                writeByte(ACK_FRAME_MARKER)
            }
        }.toByteArray()
        val input = DataInputStream(ByteArrayInputStream(bytes))

        try {
            readTransportMessage(input, TRANSPORT_PROTOCOL_VERSION)
            fail("Expected unsupported language metadata to fail")
        } catch (exception: IOException) {
            assertTrue(exception.message.orEmpty().contains("Unsupported language code"))
        }
        assertEquals(ACK_FRAME_MARKER, input.readUnsignedByte())
    }
}
