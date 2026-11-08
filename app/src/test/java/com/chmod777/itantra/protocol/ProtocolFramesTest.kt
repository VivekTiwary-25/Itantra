package com.chmod777.itantra.protocol

import com.chmod777.itantra.protocol.cbor.CborCodec
import com.chmod777.itantra.protocol.cbor.MalformedInputException
import com.chmod777.itantra.protocol.cbor.cborMap
import com.chmod777.itantra.protocol.cbor.Cbor
import com.chmod777.itantra.sos.RssiBucket
import com.chmod777.itantra.sos.SosCandidate
import com.chmod777.itantra.sos.SosCandidateSelector
import com.chmod777.itantra.sos.SosManager
import com.chmod777.itantra.sos.SosRelayRouter
import com.chmod777.itantra.sos.SosWavePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ProtocolFramesTest {
    private val config = ProtocolConfig.DEFAULT
    private val id = ByteArray(16) { it.toByte() }
    private val hash = ByteArray(32) { 9 }

    private fun roundTrip(frame: ProtocolFrame): ProtocolFrame {
        val decoded = ProtocolFrame.decode(frame.encode(), config)
        assertEquals(frame.type, decoded.type)
        assertTrue(frame.encode().contentEquals(decoded.encode()))
        return decoded
    }

    @Test
    fun everyFrameTypeRoundTrips() {
        val summary = BundleSummary(id, hash, id, BundleKind.PRIVATE, Priority.URGENT, 1_000, 300, 4)
        val frames = listOf(
            InventoryFrame(7, 0, listOf(summary)),
            InventoryEndFrame(7, 1),
            WantFrame(7, listOf(WantEntry(id, hash, WantMode.DESTINATION))),
            BundleFrame(WantMode.RELAY, ByteArray(40), RelayState(2, 1, 5_000)),
            BundleAckFrame(id, hash, AckStatus.PERSISTED),
            TombstoneFrame(ByteArray(80)),
            SosOfferFrame(id, SosCategory.MEDICAL, "kn", 10, ByteArray(32), ByteArray(64)),
            SosChatFrame(id, 3, "hi", "नमस्ते"),
            SosRequestFrame(id, id, ByteArray(32), SosCategory.OTHER, "en", 1, 1_000, 0, 2, ByteArray(64)),
            SosCancelFrame(id, SosEndReason.USER_CANCELLED, ByteArray(64)),
            ProbeFrame(ByteArray(8), 1, 0, true, ByteArray(100)),
            ProbeEchoFrame(ByteArray(8), 2, listOf(123L, 456L), ByteArray(0)),
        ) + listOf(FrameType.SOS_ACCEPT, FrameType.SOS_DECLINE, FrameType.SOS_CONFIRM, FrameType.SOS_BUSY, FrameType.SOS_END)
            .map { SosSimpleFrame(it, id) }
        frames.forEach { roundTrip(it) }
        assertEquals(FrameType.entries.toSet(), frames.map { it.type }.toSet())
    }

    @Test
    fun rejectsUnknownTypesExtraFieldsAndOversizedCollections() {
        assertThrows(MalformedInputException::class.java) { ProtocolFrame.decode(CborCodec.encode(cborMap { put(0, 99) }), config) }
        val withExtra = CborCodec.encode(cborMap { put(0, FrameType.SOS_ACCEPT.wire); put(1, id); put(2, 1) })
        assertThrows(MalformedInputException::class.java) { ProtocolFrame.decode(withExtra, config) }
        val summaries = List(config.inventoryPageSize + 1) { BundleSummary(id, hash, id, BundleKind.PRIVATE, Priority.NORMAL, 1, 1, 1) }
        assertThrows(MalformedInputException::class.java) { ProtocolFrame.decode(InventoryFrame(1, 0, summaries).encode(), config) }
        val badId = CborCodec.encode(cborMap { put(0, FrameType.SOS_ACCEPT.wire); put(1, ByteArray(15)) })
        assertThrows(MalformedInputException::class.java) { ProtocolFrame.decode(badId, config) }
        // Excess nesting inside a frame.
        var deep: Cbor = Cbor.UInt(1)
        repeat(6) { deep = Cbor.Arr(listOf(deep)) }
        val nested = CborCodec.encode(cborMap { put(0, FrameType.WANT.wire); put(1, 1); put(2, deep) })
        assertThrows(MalformedInputException::class.java) { ProtocolFrame.decode(nested, config) }
        // Garbage bytes.
        repeat(200) { seed ->
            val junk = Random(seed).nextBytes(Random(seed).nextInt(1, 64))
            runCatching { ProtocolFrame.decode(junk, config) }.exceptionOrNull()?.let { assertTrue(it is MalformedInputException) }
        }
    }

    @Test
    fun sosCandidatesAreBucketedNeverRanged() {
        val selector = SosCandidateSelector(config, Random(1))
        assertEquals(RssiBucket.STRONG, selector.bucket(listOf(-60, -90, -55)))
        assertEquals(RssiBucket.WEAK, selector.bucket(listOf(-95, -91, -99)))
        val ordered = selector.order(
            listOf(
                SosCandidate("weak", listOf(-95), true),
                SosCandidate("strong", listOf(-50), true),
                SosCandidate("not-helping", listOf(-40), false),
                SosCandidate("medium", listOf(-70), true),
            ),
        )
        assertEquals(listOf("strong", "medium", "weak"), ordered.map { it.shortIdHex })
    }

    @Test
    fun wavesExpandOnSchedule() {
        val planner = SosWavePlanner(config)
        assertEquals(3, planner.waveAt(0).directCandidateLimit)
        assertEquals(null, planner.waveAt(0).relayHopLimit)
        assertEquals(8, planner.waveAt(config.sosWave1StartMs).directCandidateLimit)
        assertEquals(2, planner.waveAt(config.sosWave2StartMs).relayHopLimit)
        assertEquals(3, planner.waveAt(config.sosWave3StartMs).relayHopLimit)
    }

    @Test
    fun sosRelayForwardsOnceWithinLimits() {
        val router = SosRelayRouter(config, Random(1))
        assertEquals(SosRelayRouter.Decision.FORWARD, router.decide("f1", "s1", 0, 2, 1_000, 0))
        assertEquals(SosRelayRouter.Decision.DUPLICATE, router.decide("f1", "s1", 0, 2, 1_000, 1))
        assertEquals(SosRelayRouter.Decision.DELIVER_ONLY, router.decide("f2", "s1", 2, 2, 1_000, 2))
        assertEquals(SosRelayRouter.Decision.EXPIRED, router.decide("f3", "s1", 0, 2, 0, 3))
        assertTrue(router.cancel("s1", 4))
        assertTrue(!router.cancel("s1", 5))
        assertEquals(SosRelayRouter.Decision.CANCELLED, router.decide("f4", "s1", 0, 2, 1_000, 6))
        var forwarded = 0
        repeat(config.maxSosRelayFramesPerMinute + 10) { if (router.decide("x$it", "s2", 0, 3, 1_000, 10) == SosRelayRouter.Decision.FORWARD) forwarded++ }
        assertEquals(config.maxSosRelayFramesPerMinute - 1, forwarded)
        val jitter = router.jitterMs()
        assertTrue(jitter in config.sosRelayJitterMinMs..config.sosRelayJitterMaxMs)
    }

    @Test
    fun shortAuthStringDependsOnHandshakeAndIncident() {
        val a = SosManager.shortAuthString(ByteArray(32) { 1 }, id)
        assertEquals(6, a.length)
        assertEquals(a, SosManager.shortAuthString(ByteArray(32) { 1 }, id))
        assertTrue(a != SosManager.shortAuthString(ByteArray(32) { 2 }, id) || a != SosManager.shortAuthString(ByteArray(32) { 1 }, hash.copyOf(16)))
    }
}
