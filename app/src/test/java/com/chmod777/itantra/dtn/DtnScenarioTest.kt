package com.chmod777.itantra.dtn

import com.chmod777.itantra.identity.ContactQrCodec
import com.chmod777.itantra.identity.LocalIdentity
import com.chmod777.itantra.identity.TrustedContact
import com.chmod777.itantra.metrics.MetricsSink
import com.chmod777.itantra.protocol.AckStatus
import com.chmod777.itantra.protocol.BundleAckFrame
import com.chmod777.itantra.protocol.Priority
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.protocol.ProtocolFrame
import com.chmod777.itantra.protocol.TombstoneV1
import com.chmod777.itantra.protocol.WantEntry
import com.chmod777.itantra.protocol.WantMode
import com.chmod777.itantra.session.FrameSink
import com.chmod777.itantra.testing.FakeClock
import com.chmod777.itantra.testing.InMemoryDtnStore
import com.chmod777.itantra.testing.InMemoryTrustedContactStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtnScenarioTest {
    private val config = ProtocolConfig.DEFAULT
    private val clock = FakeClock()

    inner class Peer(val name: String) {
        val identity = LocalIdentity.generate(name)
        val contacts = InMemoryTrustedContactStore()
        var store = InMemoryDtnStore()
        var node = DtnNode(identity, contacts, store, clock, config)
        val delivered = mutableListOf<DeliveredMessage>()
        val sessionKey = "session-of-$name"

        fun trust(other: Peer) {
            val capsule = ContactQrCodec.decodeAndVerify(ContactQrCodec.encode(other.identity.signedCapsule()))
            contacts.upsert(TrustedContact.fromVerifiedCapsule(capsule, other.name, 0))
        }

        fun restartProcess() {
            node = DtnNode(identity, contacts, store, clock, config)
        }

        fun copies(bundleId: ByteArray) = store.bundlesById(bundleId)
        fun tokens(bundleId: ByteArray) = copies(bundleId).sumOf { it.copyTokens }
        fun outgoingState(bundleId: ByteArray) = store.outgoing(bundleId)?.state
    }

    /** One physical encounter: both inventories, all wants, bundles, acks and tombstones, through the real codec. */
    private fun meet(a: Peer, b: Peer, rounds: Int = 2) = runBlocking {
        repeat(rounds) {
            val toA = ArrayDeque<ByteArray>()
            val toB = ArrayDeque<ByteArray>()
            val ea = DtnEncounter(a.node, FrameSink { toB.addLast(it.encode()) }, b.sessionKey, config, clock, MetricsSink.NONE)
            val eb = DtnEncounter(b.node, FrameSink { toA.addLast(it.encode()) }, a.sessionKey, config, clock, MetricsSink.NONE)
            ea.startRound(force = true)
            eb.startRound(force = true)
            var guard = 0
            while ((toA.isNotEmpty() || toB.isNotEmpty()) && guard++ < 10_000) {
                toA.removeFirstOrNull()?.let { ea.handle(ProtocolFrame.decode(it, config)) }
                toB.removeFirstOrNull()?.let { eb.handle(ProtocolFrame.decode(it, config)) }
            }
            // Collect delivered events emitted during this encounter.
            a.delivered.clear(); a.delivered += a.node.deliveredMessages()
            b.delivered.clear(); b.delivered += b.node.deliveredMessages()
        }
    }

    private fun trustedPair(): Triple<Peer, Peer, Peer> {
        val vivek = Peer("vivek")
        val rahul = Peer("rahul")
        val relay = Peer("relay")
        vivek.trust(rahul)
        rahul.trust(vivek)
        return Triple(vivek, relay, rahul)
    }

    @Test
    fun testA_directTrustedMessageEndsDeliveredOnlyAfterReceipt() {
        val (vivek, _, rahul) = trustedPair()
        val out = vivek.node.createMessage(rahul.identity.nodeId, "I am safe", "en")
        assertEquals(DeliveryState.QUEUED, vivek.outgoingState(out.bundleId))
        meet(vivek, rahul, rounds = 1)
        assertEquals(listOf("I am safe"), rahul.delivered.map { it.text })
        // Rahul accepted a delivery copy, but no receipt has reached Vivek yet.
        assertEquals(DeliveryState.RELAYED, vivek.outgoingState(out.bundleId))
        meet(vivek, rahul, rounds = 1)
        assertEquals(DeliveryState.DELIVERED, vivek.outgoingState(out.bundleId))
        assertNotNull(vivek.store.receipt(out.bundleId))
    }

    @Test
    fun testB_storeCarryForwardThroughRelayThatCannotRead() {
        val (vivek, relay, rahul) = trustedPair()
        val out = vivek.node.createMessage(rahul.identity.nodeId, "Meet me at the shelter", "hi")
        meet(vivek, relay)
        // Relay accepted a copy: sender is RELAYED, never DELIVERED on a hop ACK.
        assertEquals(DeliveryState.RELAYED, vivek.outgoingState(out.bundleId))
        assertEquals(2, relay.tokens(out.bundleId))
        assertEquals(2, vivek.tokens(out.bundleId))
        assertTrue(relay.node.deliveredMessages().isEmpty())
        relay.copies(out.bundleId).forEach {
            assertFalse(String(it.immutableBytes, Charsets.ISO_8859_1).contains("shelter"))
        }
        // Vivek and Rahul never meet. Relay later meets Rahul.
        meet(relay, rahul)
        assertEquals(listOf("Meet me at the shelter"), rahul.delivered.map { it.text })
        assertEquals("hi", rahul.delivered.single().language)
        // Rahul's tombstone removed the relay copy; the relay now carries Rahul's receipt.
        assertTrue(relay.copies(out.bundleId).isEmpty())
        assertEquals(DeliveryState.RELAYED, vivek.outgoingState(out.bundleId))
        // Relay later meets Vivek again with the receipt.
        meet(relay, vivek)
        assertEquals(DeliveryState.DELIVERED, vivek.outgoingState(out.bundleId))
        assertTrue(vivek.copies(out.bundleId).isEmpty())
    }

    @Test
    fun testC_duplicateRoutesDeliverExactlyOnce() {
        val (vivek, r1, rahul) = trustedPair()
        val r2 = Peer("r2")
        val out = vivek.node.createMessage(rahul.identity.nodeId, "once", "en")
        meet(vivek, r1)
        meet(vivek, r2)
        meet(r1, rahul)
        meet(r2, rahul)
        meet(r1, rahul)
        assertEquals(1, rahul.node.deliveredMessages().size)
        assertTrue(r2.copies(out.bundleId).isEmpty())
    }

    @Test
    fun testD_falseDestinationClaimCannotDeleteHonestCopyOrDeliver() {
        val (vivek, _, rahul) = trustedPair()
        val out = vivek.node.createMessage(rahul.identity.nodeId, "private", "en")
        val stored = vivek.copies(out.bundleId).single()
        val budget = SessionBudget("mallory-session")
        // Mallory claims to be the destination repeatedly.
        var served = 0
        repeat(config.maxDestinationClaimsPerSession + 5) {
            if (vivek.node.serveWant(WantEntry(out.bundleId, stored.bundle.ciphertextHash, WantMode.DESTINATION), budget) != null) served++
        }
        assertEquals(config.maxDestinationClaimsPerSession, served)
        // A rate limit, not a lifetime cap: a legitimate recipient is served again next minute
        // (regression: a long-lived BLE session stalled after 16 messages on real phones).
        clock.advance(61_000)
        assertNotNull(vivek.node.serveWant(WantEntry(out.bundleId, stored.bundle.ciphertextHash, WantMode.DESTINATION), budget))
        // Mallory "acknowledges" as a destination: at most RELAYED, and our copy stays.
        vivek.node.onAck(BundleAckFrame(out.bundleId, stored.bundle.ciphertextHash, AckStatus.ACCEPTED_AS_DESTINATION_ATTEMPT), budget)
        assertEquals(DeliveryState.RELAYED, vivek.outgoingState(out.bundleId))
        assertEquals(1, vivek.copies(out.bundleId).size)
        // A forged tombstone (random secret) is ignored.
        assertFalse(vivek.node.onTombstone(TombstoneV1(out.bundleId, ByteArray(32) { 7 }, 1000).encode()))
        assertEquals(1, vivek.copies(out.bundleId).size)
    }

    @Test
    fun testE_expiryRemovesBundleAndMarksSenderExpired() {
        val (vivek, relay, rahul) = trustedPair()
        val out = vivek.node.createMessage(rahul.identity.nodeId, "urgent", "en", Priority.URGENT)
        meet(vivek, relay)
        clock.advance(config.urgentPrivateLifetimeMs + 1)
        vivek.node.sweep()
        relay.node.sweep()
        assertTrue(vivek.copies(out.bundleId).isEmpty())
        assertTrue(relay.copies(out.bundleId).isEmpty())
        assertEquals(DeliveryState.EXPIRED, vivek.outgoingState(out.bundleId))
        // Expired bundles are not re-accepted from the seen record's perspective and not offered.
        assertTrue(relay.node.inventory().isEmpty())
    }

    @Test
    fun testH_processRestartKeepsBundleRoutable() {
        val (vivek, relay, rahul) = trustedPair()
        val out = vivek.node.createMessage(rahul.identity.nodeId, "survive", "en")
        meet(vivek, relay)
        relay.restartProcess()
        assertEquals(1, relay.copies(out.bundleId).size)
        meet(relay, rahul)
        assertEquals(listOf("survive"), rahul.node.deliveredMessages().map { it.text })
    }

    @Test
    fun rebootResumesAgeFromLastPersistedValue() {
        val (vivek, relay, rahul) = trustedPair()
        val out = vivek.node.createMessage(rahul.identity.nodeId, "age", "en", Priority.URGENT)
        meet(vivek, relay)
        clock.advance(2 * 60 * 60_000L)
        relay.node.sweep() // persists age = 2 h
        // The phone is off for a long time; elapsedRealtime resets. Age must not go negative or reset to 0.
        clock.reboot(offForMs = 10 * 60 * 60_000L)
        val age = relay.copies(out.bundleId).single().age.effectiveAgeMs(clock)
        assertTrue("age $age", age >= 2 * 60 * 60_000L)
        // The service sweeps on start, re-basing ages onto the new boot so they keep counting.
        relay.node.sweep()
        clock.advance(config.urgentPrivateLifetimeMs - 2 * 60 * 60_000L)
        relay.node.sweep()
        assertTrue(relay.copies(out.bundleId).isEmpty())
    }

    @Test
    fun sprayAndWaitNeverExceedsBudgetAcrossRepeatedEncounters() {
        val (vivek, _, rahul) = trustedPair()
        val relays = List(6) { Peer("r$it") }
        val out = vivek.node.createMessage(rahul.identity.nodeId, "spray", "en")
        // Vivek meets everyone, repeatedly; relays also meet each other.
        repeat(2) { relays.forEach { meet(vivek, it) } }
        for (i in relays.indices) for (j in relays.indices) if (i < j) meet(relays[i], relays[j])
        repeat(2) { relays.forEach { meet(vivek, it) } }
        val holders = (relays + vivek).filter { it.copies(out.bundleId).isNotEmpty() }
        val totalTokens = holders.sumOf { it.tokens(out.bundleId) }
        assertEquals(config.normalPrivateCopyBudget, totalTokens)
        assertEquals(config.normalPrivateCopyBudget, holders.size)
        assertTrue(holders.all { it.tokens(out.bundleId) == 1 })
    }

    @Test
    fun lostAckIsReconciledOnTheNextEncounterWithTheSamePeer() {
        val (vivek, relay, rahul) = trustedPair()
        val out = vivek.node.createMessage(rahul.identity.nodeId, "ack lost", "en")
        val stored = vivek.copies(out.bundleId).single()
        val vivekBudgetForRelay = SessionBudget(relay.sessionKey)
        val frame = vivek.node.serveWant(WantEntry(out.bundleId, stored.bundle.ciphertextHash, WantMode.RELAY), vivekBudgetForRelay)!!
        // The relay persists, but its ACK never reaches Vivek.
        val relayBudget = SessionBudget(vivek.sessionKey).apply { outstandingWants[stored.storageKey] = WantMode.RELAY }
        assertEquals(AckStatus.PERSISTED, relay.node.ingest(frame, relayBudget).ack.status)
        assertEquals(4, vivek.tokens(out.bundleId))
        assertEquals(2, vivek.node.inventory().single().copyTokens) // 2 of 4 are promised, not offered twice
        // Next encounter with the same peer session shows the relay holding it: the split commits.
        meet(vivek, relay, rounds = 1)
        assertEquals(2, vivek.tokens(out.bundleId))
        assertNull(vivek.copies(out.bundleId).single().pendingSplit)
    }

    @Test
    fun unsolicitedAndTamperedBundlesAreRejected() {
        val (vivek, relay, rahul) = trustedPair()
        val out = vivek.node.createMessage(rahul.identity.nodeId, "x", "en")
        val stored = vivek.copies(out.bundleId).single()
        val frame = vivek.node.serveWant(WantEntry(out.bundleId, stored.bundle.ciphertextHash, WantMode.RELAY), SessionBudget("k"))!!
        // Relay never asked for it.
        assertEquals(AckStatus.REJECTED, relay.node.ingest(frame, SessionBudget(vivek.sessionKey)).ack.status)
        // Destination copy whose ciphertext was altered: Rahul refuses and records nothing as seen.
        val tampered = frame.immutableBytes.copyOf().also { it[it.size - 5] = (it[it.size - 5].toInt() xor 1).toByte() }
        val rahulBudget = SessionBudget(vivek.sessionKey).apply { outstandingWants[stored.storageKey] = WantMode.DESTINATION }
        val result = rahul.node.ingest(com.chmod777.itantra.protocol.BundleFrame(WantMode.DESTINATION, tampered, frame.relayState), rahulBudget)
        assertEquals(AckStatus.REJECTED, result.ack.status)
        assertNull(rahul.store.seen(stored.storageKey))
        assertTrue(rahul.node.deliveredMessages().isEmpty())
        // The genuine bundle is still accepted afterwards (audit #4).
        meet(vivek, rahul, rounds = 1)
        assertEquals(1, rahul.node.deliveredMessages().size)
    }

    @Test
    fun replayedTombstoneIsIdempotentAndRequiresEvidence() {
        val (vivek, relay, rahul) = trustedPair()
        val out = vivek.node.createMessage(rahul.identity.nodeId, "t", "en")
        val tomb = TombstoneV1(out.bundleId, out.deletionSecret, 1_000).encode()
        // A stranger with no evidence stores nothing.
        assertFalse(relay.node.onTombstone(tomb))
        assertNull(relay.store.tombstone(out.bundleId))
        meet(vivek, relay)
        assertTrue(relay.node.onTombstone(tomb))
        assertTrue(relay.node.onTombstone(tomb))
        assertTrue(relay.copies(out.bundleId).isEmpty())
        // A tombstoned bundle is not re-accepted.
        meet(vivek, relay)
        assertTrue(relay.copies(out.bundleId).isEmpty())
    }

    @Test
    fun inventoryListsUrgentThenOldestFirst() {
        val (vivek, _, rahul) = trustedPair()
        val first = vivek.node.createMessage(rahul.identity.nodeId, "first", "en")
        clock.advance(1_000)
        val second = vivek.node.createMessage(rahul.identity.nodeId, "second", "en")
        clock.advance(1_000)
        val urgent = vivek.node.createMessage(rahul.identity.nodeId, "urgent", "en", Priority.URGENT)
        val order = vivek.node.inventory().map { it.bundleId.toList() }
        assertEquals(listOf(urgent.bundleId, first.bundleId, second.bundleId).map { it.toList() }, order)
    }

    @Test
    fun untrustedRecipientIsRefused() {
        val vivek = Peer("vivek")
        val stranger = Peer("stranger")
        val result = runCatching { vivek.node.createMessage(stranger.identity.nodeId, "hi", "en") }
        assertTrue(result.exceptionOrNull()?.message == "Cannot securely identify this recipient.")
    }
}
