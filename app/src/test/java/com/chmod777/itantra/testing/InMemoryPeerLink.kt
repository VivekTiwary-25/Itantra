package com.chmod777.itantra.testing

import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.transport.LinkClosedException
import com.chmod777.itantra.transport.LinkRole
import com.chmod777.itantra.transport.PeerLink
import com.chmod777.itantra.transport.TransportKind
import com.chmod777.itantra.transport.ble.FragmentCodec
import com.chmod777.itantra.transport.ble.FragmentReassembler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * TEST DOUBLE — not a radio. Two linked [PeerLink]s that push every frame through
 * the real [FragmentCodec]/[FragmentReassembler] at a chosen ATT payload size, so
 * JVM tests exercise the same fragmentation path as BLE GATT.
 *
 * [tamper] lets a test act as an on-path attacker on raw fragments.
 */
class InMemoryPeerLink private constructor(
    override val peerSessionId: ByteArray,
    override val role: LinkRole,
    override val linkId: String,
    private val config: ProtocolConfig,
    private val attPayloadBytes: Int,
) : PeerLink {
    override val transportKind = TransportKind.IN_MEMORY_TEST
    override val maxFrameBytes: Int get() = config.maxLinkFrameBytes
    private val mutableState = MutableStateFlow(PeerLink.State.READY)
    override val stateFlow: StateFlow<PeerLink.State> = mutableState

    private lateinit var remote: InMemoryPeerLink
    private val inbound = Channel<ByteArray>(Channel.UNLIMITED)
    private val reassembler = FragmentReassembler(config)
    private val frameIds = AtomicInteger()
    private val connectionSessionId = (Math.random() * Int.MAX_VALUE).toInt()

    var tamper: ((ByteArray) -> ByteArray?)? = null
    val sentFrames = mutableListOf<ByteArray>()
    val fragmentsSent = AtomicInteger()
    var rejectedFragments = 0
        private set

    override suspend fun connect() = Unit

    override suspend fun send(frame: ByteArray) {
        if (state != PeerLink.State.READY) throw LinkClosedException("link $linkId is $state")
        synchronized(sentFrames) { sentFrames += frame }
        val fragments = FragmentCodec.fragment(frame, connectionSessionId, frameIds.getAndIncrement(), attPayloadBytes, config)
        fragments.forEach { fragment ->
            fragmentsSent.incrementAndGet()
            val delivered = tamper?.invoke(fragment) ?: if (tamper == null) fragment else null
            if (delivered != null) remote.receiveFragment(delivered)
        }
    }

    private fun receiveFragment(bytes: ByteArray) {
        when (val result = synchronized(reassembler) { reassembler.accept(bytes, System.currentTimeMillis()) }) {
            is FragmentReassembler.Result.Complete -> inbound.trySend(result.frame)
            is FragmentReassembler.Result.Rejected -> rejectedFragments++
            else -> Unit
        }
    }

    /** Injects a raw link frame as if it had been reassembled from the radio. */
    fun injectFrame(frame: ByteArray) {
        inbound.trySend(frame)
    }

    override fun incomingFrames(): Flow<ByteArray> = inbound.receiveAsFlow()

    override suspend fun close() {
        if (mutableState.value == PeerLink.State.CLOSED) return
        mutableState.value = PeerLink.State.CLOSED
        inbound.close()
        if (::remote.isInitialized && remote.state != PeerLink.State.CLOSED) remote.close()
    }

    companion object {
        /** Returns (initiator side, responder side). */
        fun pair(
            initiatorId: ByteArray,
            responderId: ByteArray,
            config: ProtocolConfig = ProtocolConfig.DEFAULT,
            attPayloadBytes: Int = 20,
        ): Pair<InMemoryPeerLink, InMemoryPeerLink> {
            val a = InMemoryPeerLink(responderId, LinkRole.INITIATOR, "mem-a", config, attPayloadBytes)
            val b = InMemoryPeerLink(initiatorId, LinkRole.RESPONDER, "mem-b", config, attPayloadBytes)
            a.remote = b
            b.remote = a
            return a to b
        }
    }
}
