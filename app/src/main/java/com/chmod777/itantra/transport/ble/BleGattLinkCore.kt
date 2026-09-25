package com.chmod777.itantra.transport.ble

import com.chmod777.itantra.crypto.Primitives
import com.chmod777.itantra.crypto.toHex
import com.chmod777.itantra.dtn.DtnClock
import com.chmod777.itantra.metrics.MetricsSink
import com.chmod777.itantra.protocol.LinkFrame
import com.chmod777.itantra.protocol.LinkFrameType
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.protocol.cbor.MalformedInputException
import com.chmod777.itantra.transport.LinkClosedException
import com.chmod777.itantra.transport.LinkRole
import com.chmod777.itantra.transport.PeerLink
import com.chmod777.itantra.transport.TransportKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer

/**
 * Shared machinery for both GATT roles (spec §7, audit H):
 * - one outstanding GATT write/indication per connection, awaited with a timeout;
 * - application fragmentation sized to the negotiated MTU;
 * - bounded reassembly; complete frames only are passed upward;
 * - a plaintext HELLO carrying only the advertised short ID, then READY.
 *
 * Subclasses implement [startFragmentWrite] (client: characteristic write with
 * response; server: indication) and call [onFragmentWriteComplete] /
 * [onFragmentReceived] from their Android callbacks.
 */
abstract class BleGattLinkCore(
    final override val role: LinkRole,
    protected val config: ProtocolConfig,
    protected val clock: DtnClock,
    protected val metrics: MetricsSink,
    private val localShortId: ByteArray,
    initialPeerShortId: ByteArray?,
) : PeerLink {
    final override val transportKind = TransportKind.BLE_GATT
    final override val linkId: String = "ble-" + (if (role == LinkRole.INITIATOR) "c-" else "s-") + Primitives.randomBytes(3).toHex()
    final override val maxFrameBytes: Int get() = config.maxLinkFrameBytes

    protected val mutableState = MutableStateFlow(PeerLink.State.IDLE)
    final override val stateFlow: StateFlow<PeerLink.State> = mutableState

    @Volatile private var peerShortId: ByteArray? = initialPeerShortId
    final override val peerSessionId: ByteArray get() = peerShortId ?: ByteArray(LinkFrame.SHORT_ID_BYTES)

    @Volatile protected var attMtu: Int = GattProfile.DEFAULT_ATT_MTU
    final override val negotiatedMtu: Int? get() = attMtu

    private val connectionSessionId = ByteBuffer.wrap(Primitives.randomBytes(4)).int
    private var nextFrameId = 0
    private val reassembler = FragmentReassembler(config)
    private val inbound = Channel<ByteArray>(capacity = 64)
    private val sendMutex = Mutex()
    @Volatile private var pendingWrite: CompletableDeferred<Boolean>? = null
    private val helloReceived = CompletableDeferred<ByteArray>()
    protected val openedAtNs: Long = clock.monotonicNs()

    /** Starts one GATT write/indication. Returns false when Android refused to start it. */
    protected abstract fun startFragmentWrite(fragment: ByteArray): Boolean

    /** Releases the Android objects. Must be idempotent. */
    protected abstract fun releaseTransport()

    protected fun mark(event: String, extra: Map<String, Any?> = emptyMap()) {
        metrics.record(event, mapOf("link" to linkId, "role" to role.name, "since_open_ns" to clock.monotonicNs() - openedAtNs) + extra)
    }

    /** Called by the subclass once the byte pipe is usable (client: CCCD written; server: CCCD enabled). */
    protected suspend fun completeHelloAndBecomeReady() {
        writeFrameRaw(LinkFrame.hello(localShortId).encode())
        val peerId = try {
            withTimeout(config.gattOperationTimeoutMs * 2) { helloReceived.await() }
        } catch (e: TimeoutCancellationException) {
            throw LinkClosedException("no HELLO from peer", e)
        }
        peerShortId = peerId
        mutableState.value = PeerLink.State.READY
        mark("link_ready", mapOf("mtu" to attMtu, "peer" to peerId.toHex().take(8)))
    }

    final override suspend fun send(frame: ByteArray) {
        if (state != PeerLink.State.READY) throw LinkClosedException("link $linkId is $state")
        writeFrameRaw(frame)
    }

    private suspend fun writeFrameRaw(frame: ByteArray) = sendMutex.withLock {
        val fragments = FragmentCodec.fragment(frame, connectionSessionId, nextFrameId++ and 0xFFFF, GattProfile.fragmentBudget(attMtu), config)
        val started = clock.monotonicNs()
        for (fragment in fragments) writeOneFragment(fragment)
        metrics.record(
            "frame_tx",
            mapOf(
                "link" to linkId, "type" to (frame[0].toInt() and 0xFF), "bytes" to frame.size, "fragments" to fragments.size,
                "fragment_bytes" to fragments.sumOf { it.size }, "mtu" to attMtu, "write_ns" to clock.monotonicNs() - started,
            ),
        )
    }

    private suspend fun writeOneFragment(fragment: ByteArray) {
        var attempt = 0
        while (true) {
            if (state == PeerLink.State.CLOSED || state == PeerLink.State.FAILED) throw LinkClosedException("link closed mid-frame")
            val completion = CompletableDeferred<Boolean>()
            pendingWrite = completion
            val started = try {
                startFragmentWrite(fragment)
            } catch (e: RuntimeException) {
                // A frame that is only partly on air would desynchronise Noise nonces: close instead.
                pendingWrite = null
                failAndClose("GATT write threw ${e.javaClass.simpleName}: ${e.message}")
                throw LinkClosedException("GATT write threw", e)
            }
            if (!started) {
                pendingWrite = null
                // Android returns false/busy when an operation is still settling; back off briefly.
                if (++attempt > 5) {
                    failAndClose("GATT refused to start a write")
                    throw LinkClosedException("GATT write could not start")
                }
                kotlinx.coroutines.delay(15L * attempt)
                continue
            }
            val ok = try {
                withTimeout(config.gattOperationTimeoutMs) { completion.await() }
            } catch (e: TimeoutCancellationException) {
                failAndClose("GATT write timed out")
                throw LinkClosedException("GATT write timeout", e)
            } finally {
                pendingWrite = null
            }
            if (ok) return
            if (++attempt > 2) {
                failAndClose("GATT write failed")
                throw LinkClosedException("GATT write failed")
            }
        }
    }

    /** Android callback thread: the outstanding write/indication finished. */
    protected fun onFragmentWriteComplete(success: Boolean) {
        pendingWrite?.complete(success)
    }

    /** Android callback thread: raw fragment bytes arrived from the peer. */
    protected fun onFragmentReceived(bytes: ByteArray) {
        val result = synchronized(reassembler) { reassembler.accept(bytes, clock.elapsedMs()) }
        when (result) {
            is FragmentReassembler.Result.Complete -> onFrame(result.frame)
            is FragmentReassembler.Result.Rejected -> mark("fragment_rejected", mapOf("cause" to result.reason))
            else -> Unit
        }
    }

    private fun onFrame(frame: ByteArray) {
        val isHello = frame.isNotEmpty() && frame[0].toInt() == LinkFrameType.HELLO.wire
        if (isHello) {
            try {
                val peerId = LinkFrame.parseHello(LinkFrame.decode(frame))
                if (!helloReceived.complete(peerId)) mark("hello_duplicate")
            } catch (e: MalformedInputException) {
                mark("hello_malformed", mapOf("cause" to e.message))
            }
            return
        }
        if (state == PeerLink.State.CLOSED || state == PeerLink.State.FAILED) return
        // Frames arriving just before this side turns READY (the peer finished HELLO first)
        // are queued, not dropped: the next frame is usually Noise message 1.
        metrics.record("frame_rx", mapOf("link" to linkId, "type" to (frame[0].toInt() and 0xFF), "bytes" to frame.size))
        if (inbound.trySend(frame).isFailure) {
            // The upper layer is not keeping up; dropping would desynchronise Noise, so close.
            failAndClose("inbound frame queue overflow")
        }
    }

    final override fun incomingFrames(): Flow<ByteArray> = inbound.receiveAsFlow()

    protected fun failAndClose(reason: String) {
        if (state == PeerLink.State.CLOSED || state == PeerLink.State.FAILED) return
        mark("link_failed", mapOf("cause" to reason))
        mutableState.value = PeerLink.State.FAILED
        shutdown()
    }

    /** Called by subclasses when Android reports the connection is gone. */
    protected fun onTransportDisconnected(reason: String) {
        if (state == PeerLink.State.CLOSED || state == PeerLink.State.FAILED) return
        mark("link_disconnected", mapOf("cause" to reason))
        mutableState.value = PeerLink.State.CLOSED
        shutdown()
    }

    private fun shutdown() {
        pendingWrite?.complete(false)
        helloReceived.completeExceptionally(LinkClosedException("link closed"))
        inbound.close()
        synchronized(reassembler) { reassembler.clear() }
        releaseTransport()
    }

    final override suspend fun close() {
        if (state == PeerLink.State.CLOSED || state == PeerLink.State.FAILED) return
        mutableState.value = PeerLink.State.CLOSING
        mark("link_close_requested")
        mutableState.value = PeerLink.State.CLOSED
        shutdown()
    }

    /** Periodic reassembly timeout sweep, driven by the link manager. */
    fun sweepReassembly() {
        val dropped = synchronized(reassembler) { reassembler.sweep(clock.elapsedMs()) }
        if (dropped > 0) mark("reassembly_timeout", mapOf("frames" to dropped))
    }
}
