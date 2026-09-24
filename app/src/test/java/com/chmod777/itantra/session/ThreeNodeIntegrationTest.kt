package com.chmod777.itantra.session

import com.chmod777.itantra.crypto.toHex
import com.chmod777.itantra.dtn.DeliveryState
import com.chmod777.itantra.dtn.DtnClock
import com.chmod777.itantra.dtn.DtnEvent
import com.chmod777.itantra.dtn.DtnNode
import com.chmod777.itantra.identity.ContactQrCodec
import com.chmod777.itantra.identity.LocalIdentity
import com.chmod777.itantra.identity.TrustedContact
import com.chmod777.itantra.metrics.MetricsSink
import com.chmod777.itantra.metrics.ProbeService
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.protocol.SosCategory
import com.chmod777.itantra.sos.IncomingSos
import com.chmod777.itantra.sos.OutgoingSosState
import com.chmod777.itantra.sos.SosManager
import com.chmod777.itantra.sos.SosTrustState
import com.chmod777.itantra.testing.InMemoryDtnStore
import com.chmod777.itantra.testing.InMemoryPeerLink
import com.chmod777.itantra.testing.InMemoryTrustedContactStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * Full-stack JVM integration over TEST-DOUBLE in-memory links (not radios):
 * real fragmentation at a 20-byte ATT payload, real Noise XX, real E2E crypto,
 * real DTN, probes and SOS. This proves protocol logic, not Android BLE behaviour.
 */
class ThreeNodeIntegrationTest {
    private val config = ProtocolConfig.DEFAULT.copy(minInventoryRoundIntervalMs = 50, noiseHandshakeTimeoutMs = 5_000)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val clock = object : DtnClock {
        override fun elapsedMs() = System.nanoTime() / 1_000_000
        override fun bootCount() = 1
        override fun wallMs() = System.currentTimeMillis()
        override fun monotonicNs() = System.nanoTime()
    }

    inner class Node(val name: String, val shortId: ByteArray, helpers: () -> List<String> = { emptyList() }) {
        val identity = LocalIdentity.generate(name)
        val contacts = InMemoryTrustedContactStore()
        val store = InMemoryDtnStore()
        val dtn = DtnNode(identity, contacts, store, clock, config)
        val delivered: MutableList<String> = Collections.synchronizedList(mutableListOf())
        lateinit var core: NetworkingCore
        val probes = ProbeService(clock, MetricsSink.NONE, config) { core.sessionHandles() }
        val sos = SosManager(scope, clock, config, MetricsSink.NONE, { core.sessionHandles() }, { listOf(-50, -52, -49) }, helpers, {})

        init {
            core = NetworkingCore(scope, dtn, config, clock, MetricsSink.NONE, listOf(probes, sos), PeerCapabilities(1, PeerCapabilities.FLAG_DTN_RELAY))
            core.start()
            scope.launch { dtn.events.filterIsInstance<DtnEvent.MessageDelivered>().collect { delivered += it.message.text } }
        }

        fun trust(other: Node) {
            val capsule = ContactQrCodec.decodeAndVerify(ContactQrCodec.encode(other.identity.signedCapsule()))
            contacts.upsert(TrustedContact.fromVerifiedCapsule(capsule, other.name, 0))
        }
    }

    private fun connect(initiator: Node, responder: Node) {
        val (a, b) = InMemoryPeerLink.pair(initiator.shortId, responder.shortId, config, attPayloadBytes = 20)
        initiator.core.attach(a)
        responder.core.attach(b)
    }

    private suspend fun waitFor(what: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        withTimeout(timeoutMs) { while (!condition()) delay(20) }
        assertTrue(what, condition())
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun trustedMessageCrossesRelayAndReceiptMakesSenderDelivered() = runBlocking<Unit> {
        val a = Node("A", ByteArray(8) { 1 })
        val b = Node("B", ByteArray(8) { 2 })
        val c = Node("C", ByteArray(8) { 3 })
        a.trust(c)
        c.trust(a)
        connect(a, b)
        connect(b, c)
        waitFor("sessions up") { a.core.sessions.value.size == 1 && b.core.sessions.value.size == 2 && c.core.sessions.value.size == 1 }

        val out = a.dtn.createMessage(c.identity.nodeId, "Meet me at the shelter", "hi")
        waitFor("C received") { c.delivered.isNotEmpty() }
        assertEquals(listOf("Meet me at the shelter"), c.delivered.toList())
        waitFor("A delivered") { a.store.outgoing(out.bundleId)?.state == DeliveryState.DELIVERED }
        // The relay carried ciphertext only and never had a local delivery (so it can never speak it).
        assertTrue(b.delivered.isEmpty())
        assertTrue(b.dtn.deliveredMessages().isEmpty())
        delay(300)
        assertEquals(1, c.dtn.deliveredMessages().size)
    }

    @Test
    fun probeEchoesThroughRelayWithRelayProcessingTime() = runBlocking<Unit> {
        val a = Node("A", ByteArray(8) { 1 })
        val b = Node("B", ByteArray(8) { 2 })
        val c = Node("C", ByteArray(8) { 3 })
        connect(a, b)
        connect(b, c)
        waitFor("sessions up") { b.core.sessions.value.size == 2 && a.core.sessions.value.size == 1 }
        val first = a.core.sessionHandles().single()
        val results = List(5) { a.probes.probe(first, hops = 2, payloadBytes = 200, echoPayload = false, timeoutMs = 5_000) }
        assertTrue(results.all { it.success })
        assertTrue(results.all { it.hopsTraversed == 2 && it.relayProcessingNs.size == 1 && it.rttNs!! > 0 })
        val oneHop = a.probes.probe(first, hops = 1, payloadBytes = 64, echoPayload = true, timeoutMs = 5_000)
        assertTrue(oneHop.success && oneHop.relayProcessingNs.isEmpty())
        // Asking for more hops than exist is reported as a failure, never as success.
        val tooFar = a.probes.probe(first, hops = 3, payloadBytes = 8, echoPayload = false, timeoutMs = 5_000)
        assertTrue(!tooFar.success && tooFar.failureCause!!.startsWith("route_short"))
    }

    @Test
    fun directSosEstablishesEncryptedUnverifiedSessionWithMatchingSas() = runBlocking<Unit> {
        val responderShortId = ByteArray(8) { 2 }
        val requester = Node("requester", ByteArray(8) { 1 }, helpers = { listOf(responderShortId.toHex()) })
        val responder = Node("responder", responderShortId)
        responder.sos.availableToHelp = true
        responder.core.localCapabilities = PeerCapabilities(1, PeerCapabilities.FLAG_DTN_RELAY or PeerCapabilities.FLAG_SOS_RESPONDER)
        connect(requester, responder)
        waitFor("session up") { requester.core.sessions.value.size == 1 && responder.core.sessions.value.size == 1 }

        requester.sos.startSos(SosCategory.MEDICAL, "kn")
        waitFor("offer arrives") { responder.sos.incomingOffers.value.any { it.phase == IncomingSos.Phase.OFFERED } }
        val offer = responder.sos.incomingOffers.value.single()
        assertEquals(SosTrustState.ENCRYPTED_UNVERIFIED, offer.trust)
        assertEquals("kn", offer.language)

        responder.sos.accept(offer.sosIdHex)
        waitFor("requester connected") { requester.sos.outgoing.value?.phase == OutgoingSosState.Phase.CONNECTED }
        waitFor("responder active") { responder.sos.incomingOffers.value.single().phase == IncomingSos.Phase.ACTIVE }
        val out = requester.sos.outgoing.value!!
        assertEquals(SosTrustState.ENCRYPTED_UNVERIFIED, out.trust)
        assertEquals(out.shortAuthString, responder.sos.incomingOffers.value.single().shortAuthString)

        requester.sos.sendChat(out.sosIdHex, "I am trapped", "kn")
        waitFor("chat to responder") { responder.sos.incomingOffers.value.single().chat.any { it.text == "I am trapped" } }
        responder.sos.sendChat(out.sosIdHex, "Coming", "kn")
        waitFor("chat to requester") { requester.sos.outgoing.value!!.chat.any { it.text == "Coming" && !it.fromMe } }
        requester.sos.cancelSos()
    }

    @Test
    fun declinedSosKeepsSearching() = runBlocking<Unit> {
        val responderShortId = ByteArray(8) { 2 }
        val requester = Node("requester", ByteArray(8) { 1 }, helpers = { listOf(responderShortId.toHex()) })
        val responder = Node("responder", responderShortId)
        responder.sos.availableToHelp = true
        responder.core.localCapabilities = PeerCapabilities(1, PeerCapabilities.FLAG_SOS_RESPONDER)
        connect(requester, responder)
        waitFor("session up") { requester.core.sessions.value.size == 1 }
        requester.sos.startSos(SosCategory.TRAPPED, "en")
        waitFor("offer") { responder.sos.incomingOffers.value.isNotEmpty() }
        responder.sos.decline(responder.sos.incomingOffers.value.single().sosIdHex)
        waitFor("decline recorded") { requester.sos.outgoing.value?.declinedCount == 1 }
        assertEquals(OutgoingSosState.Phase.SEARCHING, requester.sos.outgoing.value!!.phase)
        requester.sos.cancelSos()
    }
}
