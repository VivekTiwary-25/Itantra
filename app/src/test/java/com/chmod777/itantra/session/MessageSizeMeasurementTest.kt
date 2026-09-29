package com.chmod777.itantra.session

import com.chmod777.itantra.dtn.DtnClock
import com.chmod777.itantra.dtn.DtnEvent
import com.chmod777.itantra.dtn.DtnNode
import com.chmod777.itantra.identity.ContactQrCodec
import com.chmod777.itantra.identity.LocalIdentity
import com.chmod777.itantra.identity.TrustedContact
import com.chmod777.itantra.metrics.MetricsSink
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.testing.InMemoryDtnStore
import com.chmod777.itantra.testing.InMemoryPeerLink
import com.chmod777.itantra.testing.InMemoryTrustedContactStore
import com.chmod777.itantra.transport.ble.FragmentCodec
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
import org.junit.Test
import java.io.File
import java.util.Collections

/**
 * Task 5b measurement, not a regression test: builds a real outgoing private message (real Noise XX link,
 * real end-to-end sealing, real DTN, real fragmentation) for three fixed sentences and records the bytes.
 * Test-double links, no radio. Writes docs/sih-metrics/raw/task-msgsize/message-size.txt.
 *
 * Two ATT payload sizes: 20 B (default ATT MTU 23, the fallback when MTU negotiation fails) and 512 B
 * (the app requests MTU 517; Android allows at most 512-byte attribute values: GattProfile.fragmentBudget).
 */
class MessageSizeMeasurementTest {
    private val config = ProtocolConfig.DEFAULT.copy(minInventoryRoundIntervalMs = 50, noiseHandshakeTimeoutMs = 5_000)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = object : DtnClock {
        override fun elapsedMs() = System.nanoTime() / 1_000_000
        override fun bootCount() = 1
        override fun wallMs() = System.currentTimeMillis()
        override fun monotonicNs() = System.nanoTime()
    }

    private inner class Node(val name: String, val shortId: ByteArray) {
        val identity = LocalIdentity.generate(name)
        val contacts = InMemoryTrustedContactStore()
        val store = InMemoryDtnStore()
        val dtn = DtnNode(identity, contacts, store, clock, config)
        val delivered: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val core: NetworkingCore = NetworkingCore(
            scope, dtn, config, clock, MetricsSink.NONE, emptyList(),
            PeerCapabilities(1, PeerCapabilities.FLAG_DTN_RELAY),
        )

        init {
            core.start()
            scope.launch { dtn.events.filterIsInstance<DtnEvent.MessageDelivered>().collect { delivered += it.message.text } }
        }

        fun trust(other: Node) {
            val capsule = ContactQrCodec.decodeAndVerify(ContactQrCodec.encode(other.identity.signedCapsule()))
            contacts.upsert(TrustedContact.fromVerifiedCapsule(capsule, other.name, 0))
        }
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun measureRealMessageSizes() = runBlocking<Unit> {
        val sentences = listOf(
            "en" to "Meet me at the shelter, the water is rising.",
            "hi" to "आश्रय स्थल पर मिलिए, पानी बढ़ रहा है।",
            "ta" to "தண்ணீர் உயர்கிறது, தங்குமிடத்தில் என்னைச் சந்திக்கவும்.",
        )
        val lines = mutableListOf(
            "lang,att_payload_bytes,text_utf8_bytes,sealed_bundle_bytes,link_frame_bytes_carrying_bundle," +
                "fragments_for_bundle_frame,bytes_per_fragment_incl_11B_header,total_bytes_on_air_for_bundle_frame,other_frames_sent_bytes",
        )
        for (att in listOf(20, 512)) {
            for ((lang, text) in sentences) {
                val a = Node("A", ByteArray(8) { 1 })
                val b = Node("B", ByteArray(8) { 2 })
                a.trust(b); b.trust(a)
                val (linkA, linkB) = InMemoryPeerLink.pair(a.shortId, b.shortId, config, attPayloadBytes = att)
                a.core.attach(linkA)
                b.core.attach(linkB)
                withTimeout(15_000) { while (a.core.sessions.value.size != 1 || b.core.sessions.value.size != 1) delay(20) }
                delay(300) // let the post-handshake capability/inventory frames settle
                val framesBefore = synchronized(linkA.sentFrames) { linkA.sentFrames.size }

                val out = a.dtn.createMessage(b.identity.nodeId, text, lang)
                // read now: the sender drops its copy once delivery is confirmed
                val sealed = a.store.bundlesById(out.bundleId).single().sizeBytes
                withTimeout(15_000) { while (b.delivered.isEmpty()) delay(20) }
                assertEquals(text, b.delivered.first())

                val frames = synchronized(linkA.sentFrames) { linkA.sentFrames.drop(framesBefore) }
                val bundleFrame = frames.maxByOrNull { it.size }!!
                val fragments = FragmentCodec.fragment(bundleFrame, 1, 0, att, config)
                val onAir = fragments.sumOf { it.size }
                val others = frames.filter { it !== bundleFrame }.sumOf { it.size }
                lines += "$lang,$att,${text.toByteArray(Charsets.UTF_8).size},$sealed,${bundleFrame.size}," +
                    "${fragments.size},${fragments.first().size},$onAir,$others"
                a.core.stop(); b.core.stop()
            }
        }
        val out = File(File(System.getProperty("user.dir")).parentFile, "docs/sih-metrics/raw/task-msgsize/message-size.txt")
        out.parentFile.mkdirs()
        out.writeText(lines.joinToString("\n") + "\n")
        lines.forEach { println("MSIZE $it") }
    }
}
