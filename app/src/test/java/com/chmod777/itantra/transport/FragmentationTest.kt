package com.chmod777.itantra.transport

import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.transport.ble.AdvertisementPayload
import com.chmod777.itantra.transport.ble.FragmentCodec
import com.chmod777.itantra.transport.ble.FragmentReassembler
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FragmentationTest {
    private val config = ProtocolConfig.DEFAULT

    private fun reassemble(fragments: List<ByteArray>, reassembler: FragmentReassembler = FragmentReassembler(config)): ByteArray? {
        var out: ByteArray? = null
        fragments.forEach { f ->
            val result = reassembler.accept(f, 0)
            if (result is FragmentReassembler.Result.Complete) out = result.frame
        }
        return out
    }

    @Test
    fun roundTripsAtDefaultAndLargeMtu() {
        for (attPayload in listOf(20, 182, 512)) {
            for (size in listOf(1, 8, 9, 10, 500, config.maxLinkFrameBytes)) {
                val frame = Random(size).nextBytes(size)
                val fragments = FragmentCodec.fragment(frame, 7, 1, attPayload, config)
                assertTrue(fragments.all { it.size <= attPayload })
                assertArrayEquals(frame, reassemble(fragments))
            }
        }
    }

    @Test
    fun smallestMtuCarriesTheLargestFrameWithinTheFragmentCap() {
        val fragments = FragmentCodec.fragment(ByteArray(config.maxLinkFrameBytes), 1, 1, 20, config)
        assertTrue(fragments.size <= config.maxFragmentsPerFrame)
    }

    @Test
    fun outOfOrderAndDuplicateFragmentsAreHandled() {
        val frame = Random(1).nextBytes(100)
        val fragments = FragmentCodec.fragment(frame, 1, 3, 20, config)
        val reassembler = FragmentReassembler(config)
        assertTrue(reassembler.accept(fragments[2], 0) is FragmentReassembler.Result.Pending)
        assertTrue(reassembler.accept(fragments[2], 0) is FragmentReassembler.Result.Duplicate)
        assertArrayEquals(frame, reassemble(fragments.reversed(), reassembler))
        assertEquals(0, reassembler.bufferedBytesForTest)
    }

    @Test
    fun conflictingFragmentsAbortTheFrame() {
        val fragments = FragmentCodec.fragment(ByteArray(50), 1, 9, 20, config)
        val reassembler = FragmentReassembler(config)
        reassembler.accept(fragments[0], 0)
        val conflicting = fragments[0].copyOf().also { it[it.size - 1] = 1 }
        assertTrue(reassembler.accept(conflicting, 0) is FragmentReassembler.Result.Rejected)
        assertEquals(0, reassembler.bufferedBytesForTest)
        // A fragment claiming a different count for the same frame_id is also a conflict.
        val otherCount = FragmentCodec.fragment(ByteArray(80), 1, 9, 20, config)
        reassembler.accept(fragments[1], 0)
        assertTrue(reassembler.accept(otherCount[0], 0) is FragmentReassembler.Result.Rejected)
    }

    @Test
    fun rejectsMalformedAndForeignSessionFragments() {
        val reassembler = FragmentReassembler(config)
        assertTrue(reassembler.accept(ByteArray(5), 0) is FragmentReassembler.Result.Rejected)
        val badIndex = FragmentCodec.fragment(ByteArray(5), 1, 1, 20, config)[0].also { it[8] = 5 }
        assertTrue(reassembler.accept(badIndex, 0) is FragmentReassembler.Result.Rejected)
        reassembler.accept(FragmentCodec.fragment(ByteArray(50), 1, 1, 20, config)[0], 0)
        val otherSession = FragmentCodec.fragment(ByteArray(50), 2, 2, 20, config)[0]
        assertTrue(reassembler.accept(otherSession, 0) is FragmentReassembler.Result.Rejected)
    }

    @Test
    fun enforcesConcurrencyMemoryAndTimeoutBounds() {
        val reassembler = FragmentReassembler(config)
        reassembler.accept(FragmentCodec.fragment(ByteArray(50), 1, 1, 20, config)[0], 0)
        reassembler.accept(FragmentCodec.fragment(ByteArray(50), 1, 2, 20, config)[0], 0)
        assertTrue(reassembler.accept(FragmentCodec.fragment(ByteArray(50), 1, 3, 20, config)[0], 0) is FragmentReassembler.Result.Rejected)
        assertEquals(2, reassembler.sweep(config.reassemblyTimeoutMs + 1))
        assertEquals(0, reassembler.bufferedBytesForTest)

        // A peer trickling fragments of two huge frames cannot exceed the per-peer memory cap.
        val tight = config.copy(maxReassemblyBytesPerPeer = 1_000)
        val bounded = FragmentReassembler(tight)
        val big = FragmentCodec.fragment(ByteArray(2_000), 1, 1, 100, tight)
        val results = big.dropLast(1).map { bounded.accept(it, 0) }
        assertTrue(results.any { it is FragmentReassembler.Result.Rejected })
        assertTrue(bounded.bufferedBytesForTest <= 1_000)
    }

    @Test
    fun oversizedFramesAreRefusedOnBothSides() {
        assertThrows(IllegalArgumentException::class.java) {
            FragmentCodec.fragment(ByteArray(config.maxLinkFrameBytes + 1), 1, 1, 512, config)
        }
        // A malicious sender lying about sizes is stopped by the receiver's frame cap.
        val small = config.copy(maxLinkFrameBytes = 100)
        val fragments = FragmentCodec.fragment(ByteArray(300), 1, 1, 20, config)
        val results = fragments.map { FragmentReassembler(small).let { r -> fragments.map { f -> r.accept(f, 0) } } }.first()
        assertTrue(results.any { it is FragmentReassembler.Result.Rejected })
    }

    @Test
    fun collisionRuleIsDeterministicAndSymmetric() {
        val policy = ConnectionRolePolicy(config)
        val low = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1)
        val high = byteArrayOf(-1, 0, 0, 0, 0, 0, 0, 0) // 0xFF is larger unsigned
        assertEquals(ConnectionRolePolicy.Decision.INITIATE, policy.decide(low, high, 0, 0, false, 0, 0))
        assertEquals(ConnectionRolePolicy.Decision.WAIT, policy.decide(high, low, 0, 0, false, 0, 0))
        assertEquals(ConnectionRolePolicy.Decision.INITIATE, policy.decide(high, low, 0, config.roleWaitMs, false, 0, 0))
        assertEquals(ConnectionRolePolicy.Decision.SKIP_RATE_LIMITED, policy.decide(low, high, 0, 0, false, 0, config.connectAttemptsPerPeerPerMinute))
        assertEquals(ConnectionRolePolicy.Decision.SKIP_LINK_LIMIT, policy.decide(low, high, 0, 0, false, config.maxLinks, 0))
        // Both sides agree which duplicate link to keep.
        assertEquals(LinkRole.INITIATOR, policy.preferredLocalRole(low, high))
        assertEquals(LinkRole.RESPONDER, policy.preferredLocalRole(high, low))
    }

    @Test
    fun scanStartsAreBounded() {
        val limiter = ScanRestartLimiter(config)
        assertTrue(limiter.tryAcquire(0))
        assertTrue(!limiter.tryAcquire(1_000))
        var t = 0L
        var accepted = 1
        repeat(10) { t += config.minScanStartIntervalMs; if (limiter.tryAcquire(t)) accepted++ }
        assertTrue(accepted < 11)
    }

    @Test
    fun advertisementIsExactlyTenBytesAndStrict() {
        val payload = AdvertisementPayload(1, AdvertisementPayload.FLAG_SOS_ACTIVE, ByteArray(8) { it.toByte() })
        val encoded = payload.encode()
        assertEquals(10, encoded.size)
        assertEquals(payload, AdvertisementPayload.parse(encoded))
        assertTrue(AdvertisementPayload.parse(encoded)!!.sosActive)
        assertNull(AdvertisementPayload.parse(encoded.copyOf(9)))
        assertNull(AdvertisementPayload.parse(byteArrayOf(2) + encoded.copyOfRange(1, 10)))
    }
}
