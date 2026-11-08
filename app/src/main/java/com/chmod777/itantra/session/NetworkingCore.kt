package com.chmod777.itantra.session

import com.chmod777.itantra.crypto.NoiseStaticKey
import com.chmod777.itantra.crypto.toHex
import com.chmod777.itantra.dtn.DtnClock
import com.chmod777.itantra.dtn.DtnEncounter
import com.chmod777.itantra.dtn.DtnEvent
import com.chmod777.itantra.dtn.DtnNode
import com.chmod777.itantra.metrics.MetricsSink
import com.chmod777.itantra.protocol.FrameType
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.protocol.ProtocolFrame
import com.chmod777.itantra.protocol.cbor.MalformedInputException
import com.chmod777.itantra.transport.LinkClosedException
import com.chmod777.itantra.transport.PeerLink
import com.chmod777.itantra.transport.SlidingWindowLimiter
import com.chmod777.itantra.transport.TransportKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

fun interface FrameSink {
    suspend fun sendFrame(frame: ProtocolFrame)
}

/** What upper layers may know about a live secure session. No MAC, no device name. */
interface SessionHandle : FrameSink {
    val peerSessionKey: String
    val linkId: String
    val transportKind: TransportKind
    val peerShortIdHex: String
    val capabilities: PeerCapabilities
    val handshakeHash: ByteArray
    val openedAtNs: Long
    val negotiatedMtu: Int?
    suspend fun close()
}

/** A protocol feature layered on sessions (SOS, benchmark probes). */
interface SessionFeature {
    val frameTypes: Set<FrameType>
    fun onSessionOpened(handle: SessionHandle) {}
    suspend fun onFrame(handle: SessionHandle, frame: ProtocolFrame)
    fun onSessionClosed(handle: SessionHandle) {}
}

data class SessionInfo(
    val peerSessionKey: String,
    val linkId: String,
    val transportKind: TransportKind,
    val peerShortIdHex: String,
    val relaysBundles: Boolean,
    val availableToHelp: Boolean,
    val handshakeMs: Double,
    val negotiatedMtu: Int?,
)

/**
 * Hop-secure session orchestration (spec §3, §9, §49): READY PeerLink → Noise XX
 * → strict frame decode → DTN encounter / SOS / probes. Pure Kotlin; the Android
 * service only supplies links, the clock and the metrics sink.
 */
class NetworkingCore(
    private val scope: CoroutineScope,
    private val dtn: DtnNode,
    private val config: ProtocolConfig,
    private val clock: DtnClock,
    private val metrics: MetricsSink,
    private val features: List<SessionFeature>,
    capabilities: PeerCapabilities,
) {
    /** Fresh per Emergency-mode session (audit #6). Replaced only by constructing a new core. */
    private val staticKey = NoiseStaticKey.generate()
    @Volatile var localCapabilities: PeerCapabilities = capabilities
    private val handshakeLimiter = SlidingWindowLimiter(60_000, config.noiseHandshakesPerPeerPerMinute)
    private val encounters = ConcurrentHashMap<String, Pair<SessionHandle, DtnEncounter>>()
    private val roundRequests = ConcurrentHashMap<String, Channel<Unit>>()
    private val mutableSessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions: StateFlow<List<SessionInfo>> = mutableSessions

    private var storeWatcher: Job? = null

    fun start() {
        storeWatcher = scope.launch {
            dtn.events.collect { event ->
                if (event is DtnEvent.StoreChanged) roundRequests.values.forEach { it.trySend(Unit) }
            }
        }
    }

    fun session(peerSessionKey: String): SessionHandle? = encounters[peerSessionKey]?.first
    fun sessionHandles(): List<SessionHandle> = encounters.values.map { it.first }

    /** Takes ownership of a READY link. Returns immediately; the session runs in [scope]. */
    fun attach(link: PeerLink): Job = scope.launch {
        val peerKey = link.peerSessionId.toHex()
        val linkFields = mapOf("link" to link.linkId, "transport" to link.transportKind.label, "role" to link.role.name)
        if (!handshakeLimiter.tryAcquire(peerKey, clock.elapsedMs())) {
            metrics.record("noise_rate_limited", linkFields)
            link.close()
            return@launch
        }
        metrics.record("noise_start", linkFields)
        val secure = try {
            SecureSession.establish(link, this, staticKey, localCapabilities, config, clock::monotonicNs)
        } catch (e: HandshakeFailedException) {
            metrics.record("noise_failed", linkFields + ("cause" to e.message))
            link.close()
            return@launch
        }
        metrics.record("noise_done", linkFields + ("handshake_ns" to secure.handshakeDurationNs))
        runSession(secure)
    }

    private suspend fun runSession(secure: SecureSession) = coroutineScope {
        val handle = object : SessionHandle {
            override val peerSessionKey = secure.peerSessionKey
            override val linkId = secure.link.linkId
            override val transportKind = secure.link.transportKind
            override val peerShortIdHex = secure.link.peerSessionId.toHex()
            override val capabilities = secure.peerCapabilities
            override val handshakeHash get() = secure.handshakeHash
            override val openedAtNs = clock.monotonicNs()
            override val negotiatedMtu get() = secure.link.negotiatedMtu
            override suspend fun sendFrame(frame: ProtocolFrame) = secure.send(frame.encode())
            override suspend fun close() = secure.close()
        }
        val encounter = DtnEncounter(dtn, handle, handle.peerSessionKey, config, clock, metrics)
        encounters[handle.peerSessionKey]?.first?.close() // Same peer session reconnected: keep the newest.
        encounters[handle.peerSessionKey] = handle to encounter
        val rounds = Channel<Unit>(Channel.CONFLATED).also { roundRequests[handle.peerSessionKey] = it }
        publishSessions(handle.peerSessionKey, secure.handshakeDurationNs)
        features.forEach { it.onSessionOpened(handle) }

        val roundLoop = launch {
            try {
                encounter.startRound(force = true)
                while (true) {
                    // A store change or the periodic anti-entropy timer, whichever comes first.
                    withTimeoutOrNull(config.periodicInventoryRoundMs) { rounds.receive() }
                    while (!encounter.startRound()) delay(config.minInventoryRoundIntervalMs)
                }
            } catch (e: LinkClosedException) {
                // The link died under us: end this session cleanly so a new one can form.
                metrics.record("round_send_failed", mapOf("link" to handle.linkId, "cause" to e.message))
                secure.close()
            }
        }
        var strikes = 0
        try {
            secure.incoming().collect { bytes ->
                val frame = try {
                    ProtocolFrame.decode(bytes, config)
                } catch (e: MalformedInputException) {
                    metrics.record("frame_malformed", mapOf("link" to handle.linkId, "cause" to e.message))
                    if (++strikes >= config.maxMalformedFramesPerSession) throw SessionTerminatedException("too many malformed frames")
                    return@collect
                }
                if (frame.type.wire < FrameType.SOS_OFFER.wire) {
                    if (!encounter.handle(frame)) throw SessionTerminatedException("peer exceeded DTN limits")
                } else {
                    features.firstOrNull { frame.type in it.frameTypes }?.onFrame(handle, frame)
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            metrics.record("session_closed", mapOf("link" to handle.linkId, "cause" to (e.message ?: e.javaClass.simpleName)))
        } finally {
            roundLoop.cancel()
            rounds.close()
            if (encounters[handle.peerSessionKey]?.first === handle) {
                encounters.remove(handle.peerSessionKey)
                roundRequests.remove(handle.peerSessionKey)
            }
            features.forEach { it.onSessionClosed(handle) }
            publishSessions(null, 0)
            secure.close()
        }
    }

    private val handshakeNs = ConcurrentHashMap<String, Long>()

    private fun publishSessions(newKey: String?, newHandshakeNs: Long) {
        if (newKey != null) handshakeNs[newKey] = newHandshakeNs
        mutableSessions.value = encounters.values.map { (handle, _) ->
            SessionInfo(
                peerSessionKey = handle.peerSessionKey,
                linkId = handle.linkId,
                transportKind = handle.transportKind,
                peerShortIdHex = handle.peerShortIdHex,
                relaysBundles = handle.capabilities.relaysBundles,
                availableToHelp = handle.capabilities.availableToHelp,
                handshakeMs = (handshakeNs[handle.peerSessionKey] ?: 0) / 1e6,
                negotiatedMtu = handle.negotiatedMtu,
            )
        }
    }

    fun stop() {
        storeWatcher?.cancel()
        encounters.values.forEach { (handle, _) -> scope.launch { handle.close() } }
    }
}
