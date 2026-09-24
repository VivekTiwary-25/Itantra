package com.chmod777.itantra.protocol.cbor

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CborCodecTest {
    private val limits = CborLimits(maxInputBytes = 1024)

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun encodesShortestIntegersAndSortedKeys() {
        val value = cborMap {
            put(10, 1_000_000L)
            put(1, 23)
            put(2, 24)
            put(3, 256)
        }
        // {1: 23, 2: 24, 3: 256, 10: 1000000}
        assertArrayEquals(hex("a40117021818031901000a1a000f4240"), CborCodec.encode(value))
    }

    @Test
    fun roundTripsEverySupportedType() {
        val value = cborMap {
            put(0, true)
            put(1, byteArrayOf(1, 2, 3))
            put(2, "नमस्ते")
            put(3, Cbor.Arr(listOf(Cbor.UInt(Long.MAX_VALUE), Cbor.Bool(false))))
        }
        assertEquals(value, CborCodec.decode(CborCodec.encode(value), limits))
    }

    @Test
    fun rejectsNonShortestInteger() {
        // 0x18 0x05 encodes 5 in two bytes instead of one.
        assertThrows(MalformedInputException::class.java) { CborCodec.decode(hex("1805"), limits) }
        assertThrows(MalformedInputException::class.java) { CborCodec.decode(hex("190010"), limits) }
    }

    @Test
    fun rejectsUnsortedAndDuplicateMapKeys() {
        assertThrows(MalformedInputException::class.java) { CborCodec.decode(hex("a202000100"), limits) }
        assertThrows(MalformedInputException::class.java) { CborCodec.decode(hex("a201000100"), limits) }
    }

    @Test
    fun rejectsIndefiniteLengthsTagsFloatsNullAndNegatives() {
        listOf("5f41014102ff", "c11a514b67b0", "f93c00", "f6", "20", "9f01ff").forEach { input ->
            assertThrows(input, MalformedInputException::class.java) { CborCodec.decode(hex(input), limits) }
        }
    }

    @Test
    fun rejectsTrailingBytesTruncationAndInvalidUtf8() {
        assertThrows(MalformedInputException::class.java) { CborCodec.decode(hex("0000"), limits) }
        assertThrows(MalformedInputException::class.java) { CborCodec.decode(hex("4301"), limits) }
        assertThrows(MalformedInputException::class.java) { CborCodec.decode(hex("62c328"), limits) }
    }

    @Test
    fun enforcesDepthCountAndLengthLimitsBeforeAllocating() {
        val deep = hex("81".repeat(10) + "00")
        assertThrows(MalformedInputException::class.java) { CborCodec.decode(deep, limits.copy(maxDepth = 5)) }
        // Claims a 4 GiB byte string in 5 bytes; must fail without allocating.
        assertThrows(MalformedInputException::class.java) { CborCodec.decode(hex("5affffffff"), limits) }
        // Claims 65535 array items.
        assertThrows(MalformedInputException::class.java) { CborCodec.decode(hex("99ffff"), limits) }
        assertThrows(MalformedInputException::class.java) {
            CborCodec.decode(CborCodec.encode(Cbor.Bytes(ByteArray(64))), limits.copy(maxByteStringBytes = 32))
        }
        assertThrows(MalformedInputException::class.java) {
            CborCodec.decode(ByteArray(2048), limits)
        }
    }

    @Test
    fun rejectsNonIntegerMapKeys() {
        // {"a": 1}
        assertThrows(MalformedInputException::class.java) { CborCodec.decode(hex("a1616101"), limits) }
    }

    @Test
    fun typedReadersRejectWrongTypesAndUnknownFields() {
        val map = CborCodec.decode(CborCodec.encode(cborMap { put(1, 5); put(2, "x") }), limits).asMap()
        assertThrows(MalformedInputException::class.java) { map.bytes(1, 1) }
        assertThrows(MalformedInputException::class.java) { map.uint(1, 0L..4L) }
        assertThrows(MalformedInputException::class.java) { map.requireOnlyKeys(1) }
        assertEquals("x", map.text(2, 1))
    }
}
