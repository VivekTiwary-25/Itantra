package com.chmod777.itantra.sos

import com.chmod777.itantra.crypto.Domains
import com.chmod777.itantra.crypto.Primitives
import com.chmod777.itantra.crypto.toHex
import com.chmod777.itantra.dtn.DtnClock
import com.chmod777.itantra.metrics.MetricsSink
import com.chmod777.itantra.protocol.FrameType
import com.chmod777.itantra.protocol.ID_BYTES
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.protocol.ProtocolFrame
import com.chmod777.itantra.protocol.SosCancelFrame
import com.chmod777.itantra.protocol.SosCategory
import com.chmod777.itantra.protocol.SosChatFrame
import com.chmod777.itantra.protocol.SosEndReason
import com.chmod777.itantra.protocol.SosOfferFrame
import com.chmod777.itantra.protocol.SosRequestFrame
import com.chmod777.itantra.protocol.SosSimpleFrame
import com.chmod777.itantra.session.SessionFeature
import com.chmod777.itantra.session.SessionHandle
import com.chmod777.itantra.transport.SlidingWindowLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Trust labels shown to users (spec §37, §58). There is no "verified identity" state in v1. */
enum class SosTrustState(val label: String) {
    ENCRYPTED_UNVERIFIED("Encrypted connection — identity not verified"),
    NEARBY_DEVICE_VERIFIED("Nearby device verified"),
}

enum class SosConnectionKind(val label: String) {
    NEARBY_LIVE("Nearby live connection"),
    RELAYED("Relayed emergency connection"),
}

data class SosChatLine(val fromMe: Boolean, val language: String, val text: String, val wallMs: Long)

/** Requester-side view. */
data class OutgoingSosState(
    val sosIdHex: String,
    val category: SosCategory,
    val language: String,
    val phase: Phase,
    val wave: Int,
    val offeredCount: Int,
    val declinedCount: Int,
    val responderSessionKey: String?,
    val trust: SosTrustState?,
    val shortAuthString: String?,
    val chat: List<SosChatLine>,
) {
    enum class Phase(val label: String) { SEARCHING("Looking for nearby people…"), CONNECTED("Connected"), ENDED("Ended") }
}

/** Responder-side view of one offer or relayed request. */
data class IncomingSos(
    val sosIdHex: String,
    val sessionKey: String?,
    val category: SosCategory,
    val language: String,
    val ageMs: Long,
    val proximity: RssiBucket?,
    val connection: SosConnectionKind,
    val phase: Phase,
    val trust: SosTrustState,
    val shortAuthString: String?,
    val chat: List<SosChatLine>,
) {
    enum class Phase { OFFERED, ACCEPTED_WAITING, ACTIVE, DECLINED, TAKEN_BY_OTHER, ENDED, RELAYED_NOTICE }
}

/**
 * SOS protocol (spec §32–§43, audit C/G). Direct nearby SOS over hop-level Noise XX
 * sessions is the v1 critical path. Multi-hop requests are flooded under control
 * and surface as "Relayed emergency connection" notices; relayed *interactive*
 * sessions are deliberately not built yet (spec §41, IMPLEMENTATION_NOTES §3.6).
 */
class SosManager(
    private val scope: CoroutineScope,
    private val clock: DtnClock,
    private val config: ProtocolConfig,
    private val metrics: MetricsSink,
    private val sessions: () -> List<SessionHandle>,
    /** Advertised short ID (hex) → recent RSSI samples, for candidate ordering and proximity buckets. */
    private val rssiSamples: (String) -> List<Int>,
    /** Short IDs (hex) of nearby phones currently advertising "available to help". */
    private val helpersInRange: () -> List<String>,
    /** Asks the link layer to prioritise connecting to these advertised short IDs. */
    private val prioritizeConnections: (List<String>) -> Unit,
) : SessionFeature {
    override val frameTypes = setOf(
        FrameType.SOS_OFFER, FrameType.SOS_ACCEPT, FrameType.SOS_DECLINE, FrameType.SOS_CONFIRM,
        FrameType.SOS_BUSY, FrameType.SOS_CHAT, FrameType.SOS_END, FrameType.SOS_REQUEST, FrameType.SOS_CANCEL,
    )

    private val selector = SosCandidateSelector(config)
    private val planner = SosWavePlanner(config)
    private val relayRouter = SosRelayRouter(config)
    private val offerLimiter = SlidingWindowLimiter(60_000, config.maxSosRequestsPerPeerPerMinute)
    private val lock = Any()

    /** Whether this phone accepts SOS offers ("Available to help nearby users", spec §35). */
    @Volatile var availableToHelp: Boolean = false

    // ---------------------------------------------------------------- requester

    private class Incident(
        val sosId: ByteArray,
        val signingSeed: ByteArray,
        val publicKey: ByteArray,
        val category: SosCategory,
        val language: String,
        val startElapsedMs: Long,
    ) {
        val offeredSessions = LinkedHashSet<String>()
        val declinedSessions = HashSet<String>()
        var responderSessionKey: String? = null
        var trust: SosTrustState? = null
        var relayWaveSent = 0
        var chatSequence = 0L
        val chat = ArrayList<SosChatLine>()
        var ended = false
    }

    private var incident: Incident? = null
    private var waveJob: Job? = null
    private val mutableOutgoing = MutableStateFlow<OutgoingSosState?>(null)
    val outgoing: StateFlow<OutgoingSosState?> = mutableOutgoing

    /** Incident keys are fresh and never linked to the long-term identity (spec §33). */
    fun startSos(category: SosCategory, language: String) {
        synchronized(lock) {
            incident?.let { endIncidentLocked(it, SosEndReason.USER_CANCELLED) }
            val seed = Primitives.randomBytes(32)
            incident = Incident(Primitives.randomBytes(ID_BYTES), seed, Primitives.ed25519PublicKey(seed), category, language, clock.elapsedMs())
            publishOutgoingLocked()
        }
        metrics.record("sos_start", mapOf("category" to category.name))
        waveJob?.cancel()
        waveJob = scope.launch {
            while (isActive) {
                tickWaves()
                delay(1_000)
            }
        }
    }

    fun cancelSos() {
        val ended = synchronized(lock) { incident?.also { endIncidentLocked(it, SosEndReason.USER_CANCELLED) } } ?: return
        broadcastCancel(ended, SosEndReason.USER_CANCELLED)
    }

    /** Requester or responder marks the SAS as matching after comparing it in person (State 2). */
    fun markNearbyVerified(sosIdHex: String) = synchronized(lock) {
        incident?.takeIf { it.sosId.toHex() == sosIdHex && it.responderSessionKey != null }?.let {
            it.trust = SosTrustState.NEARBY_DEVICE_VERIFIED
            publishOutgoingLocked()
        }
        incoming[sosIdHex]?.let { incoming[sosIdHex] = it.copy(trust = SosTrustState.NEARBY_DEVICE_VERIFIED) }
        publishIncomingLocked()
    }

    suspend fun sendChat(sosIdHex: String, text: String, language: String) {
        val (target, frame) = synchronized(lock) {
            val own = incident
            if (own != null && own.sosId.toHex() == sosIdHex && own.responderSessionKey != null) {
                own.chat += SosChatLine(true, language, text, clock.wallMs())
                publishOutgoingLocked()
                own.responderSessionKey to SosChatFrame(own.sosId, own.chatSequence++, language, text)
            } else {
                val state = incoming[sosIdHex]?.takeIf { it.phase == IncomingSos.Phase.ACTIVE } ?: return
                incoming[sosIdHex] = state.copy(chat = state.chat + SosChatLine(true, language, text, clock.wallMs()))
                publishIncomingLocked()
                state.sessionKey to SosChatFrame(hexToBytes(sosIdHex), responderSequence++, language, text)
            }
        }
        sessionFor(target)?.sendFrame(frame)
    }

    private suspend fun tickWaves() {
        val (own, offersToSend, relayHopLimit) = synchronized(lock) {
            val own = incident?.takeIf { !it.ended } ?: return
            val elapsed = clock.elapsedMs() - own.startElapsedMs
            if (own.responderSessionKey == null && elapsed > config.sosDiscoveryTtlMs + config.sosWave3StartMs) {
                endIncidentLocked(own, SosEndReason.EXPIRED)
                return
            }
            if (own.responderSessionKey != null) return
            val wave = planner.waveAt(elapsed)
            val ordered = selector.order(helpersInRange().map { SosCandidate(it, rssiSamples(it), true) })
            val chosen = ordered.take(wave.directCandidateLimit).map { it.shortIdHex }
            prioritizeConnections(chosen)
            val offers = sessions().filter {
                it.capabilities.availableToHelp && it.peerShortIdHex in chosen &&
                    it.peerSessionKey !in own.offeredSessions && it.peerSessionKey !in own.declinedSessions
            }
            offers.forEach { own.offeredSessions += it.peerSessionKey }
            val hop = wave.relayHopLimit?.takeIf { it > own.relayWaveSent }
            if (hop != null) own.relayWaveSent = hop
            publishOutgoingLocked(wave.index)
            Triple(own, offers, hop)
        }
        val signed = SosOfferFrame.signedBytes(own.sosId, own.category, own.language, own.publicKey)
        val signature = Primitives.ed25519Sign(own.signingSeed, Domains.SOS_OFFER, signed)
        offersToSend.forEach { session ->
            val age = clock.elapsedMs() - own.startElapsedMs
            session.sendFrame(SosOfferFrame(own.sosId, own.category, own.language, age, own.publicKey, signature))
            metrics.record("sos_offer_tx", mapOf("link" to session.linkId))
        }
        if (relayHopLimit != null) broadcastRequest(own, relayHopLimit)
    }

    private suspend fun broadcastRequest(own: Incident, hopLimit: Int) {
        val requestFrameId = Primitives.randomBytes(ID_BYTES)
        val signature = Primitives.ed25519Sign(
            own.signingSeed,
            Domains.SOS_REQUEST,
            SosRequestFrame.signedBytes(own.sosId, requestFrameId, own.publicKey, own.category, own.language, hopLimit),
        )
        relayRouter.decide(requestFrameId.toHex(), own.sosId.toHex(), 0, hopLimit, config.sosDiscoveryTtlMs, clock.elapsedMs())
        val age = clock.elapsedMs() - own.startElapsedMs
        val frame = SosRequestFrame(own.sosId, requestFrameId, own.publicKey, own.category, own.language, age, config.sosDiscoveryTtlMs, 0, hopLimit, signature)
        sessions().forEach { it.sendFrame(frame) }
        metrics.record("sos_request_tx", mapOf("hop_limit" to hopLimit, "sessions" to sessions().size))
    }

    private fun endIncidentLocked(own: Incident, reason: SosEndReason) {
        own.ended = true
        waveJob?.cancel()
        publishOutgoingLocked(phase = OutgoingSosState.Phase.ENDED)
        metrics.record("sos_end", mapOf("reason" to reason.name))
        incident = null
    }

    private fun broadcastCancel(own: Incident, reason: SosEndReason) {
        relayRouter.cancel(own.sosId.toHex(), clock.elapsedMs())
        val frame = SosCancelFrame(own.sosId, reason, Primitives.ed25519Sign(own.signingSeed, Domains.SOS_CANCEL, SosCancelFrame.signedBytes(own.sosId, reason)))
        scope.launch {
            sessions().forEach { runCatching { it.sendFrame(frame) } }
            // Incident signing material is discarded once the incident ends (spec §43).
            own.signingSeed.fill(0)
        }
    }

    private fun publishOutgoingLocked(wave: Int = mutableOutgoing.value?.wave ?: 0, phase: OutgoingSosState.Phase? = null) {
        val own = incident ?: run {
            if (phase != null) mutableOutgoing.value = mutableOutgoing.value?.copy(phase = phase)
            return
        }
        val responder = own.responderSessionKey
        mutableOutgoing.value = OutgoingSosState(
            sosIdHex = own.sosId.toHex(),
            category = own.category,
            language = own.language,
            phase = phase ?: if (responder != null) OutgoingSosState.Phase.CONNECTED else OutgoingSosState.Phase.SEARCHING,
            wave = wave,
            offeredCount = own.offeredSessions.size,
            declinedCount = own.declinedSessions.size,
            responderSessionKey = responder,
            trust = own.trust,
            shortAuthString = responder?.let { sessionFor(it) }?.let { shortAuthString(it.handshakeHash, own.sosId) },
            chat = own.chat.toList(),
        )
    }

    // ---------------------------------------------------------------- responder

    private val incoming = LinkedHashMap<String, IncomingSos>()
    private val incidentKeys = HashMap<String, ByteArray>()
    private var responderSequence = 0L
    private val mutableIncoming = MutableStateFlow<List<IncomingSos>>(emptyList())
    val incomingOffers: StateFlow<List<IncomingSos>> = mutableIncoming

    suspend fun accept(sosIdHex: String) = respond(sosIdHex, accept = true)
    suspend fun decline(sosIdHex: String) = respond(sosIdHex, accept = false)

    private suspend fun respond(sosIdHex: String, accept: Boolean) {
        val (sessionKey, frame) = synchronized(lock) {
            val offer = incoming[sosIdHex]?.takeIf { it.phase == IncomingSos.Phase.OFFERED && it.sessionKey != null } ?: return
            incoming[sosIdHex] = offer.copy(phase = if (accept) IncomingSos.Phase.ACCEPTED_WAITING else IncomingSos.Phase.DECLINED)
            publishIncomingLocked()
            offer.sessionKey to SosSimpleFrame(if (accept) FrameType.SOS_ACCEPT else FrameType.SOS_DECLINE, hexToBytes(sosIdHex))
        }
        sessionFor(sessionKey)?.sendFrame(frame)
        metrics.record(if (accept) "sos_accept_tx" else "sos_decline_tx", emptyMap())
    }

    override suspend fun onFrame(handle: SessionHandle, frame: ProtocolFrame) {
        when (frame) {
            is SosOfferFrame -> onOffer(handle, frame)
            is SosSimpleFrame -> onSimple(handle, frame)
            is SosChatFrame -> onChat(handle, frame)
            is SosRequestFrame -> onRequest(handle, frame)
            is SosCancelFrame -> onCancel(handle, frame)
            else -> Unit
        }
    }

    private fun onOffer(handle: SessionHandle, frame: SosOfferFrame) {
        if (!offerLimiter.tryAcquire(handle.peerSessionKey, clock.elapsedMs())) return
        val signed = SosOfferFrame.signedBytes(frame.sosId, frame.category, frame.language, frame.incidentPublicKey)
        if (!Primitives.ed25519Verify(frame.incidentPublicKey, Domains.SOS_OFFER, signed, frame.signature)) {
            metrics.record("sos_offer_bad_signature", emptyMap())
            return
        }
        if (!availableToHelp) return
        synchronized(lock) {
            val id = frame.sosId.toHex()
            incidentKeys[id] = frame.incidentPublicKey
            if (incoming[id]?.phase in setOf(IncomingSos.Phase.ACTIVE, IncomingSos.Phase.DECLINED)) return
            incoming[id] = IncomingSos(
                sosIdHex = id,
                sessionKey = handle.peerSessionKey,
                category = frame.category,
                language = frame.language,
                ageMs = frame.ageMs,
                proximity = selector.bucket(rssiSamples(handle.peerShortIdHex)),
                connection = SosConnectionKind.NEARBY_LIVE,
                phase = IncomingSos.Phase.OFFERED,
                trust = SosTrustState.ENCRYPTED_UNVERIFIED,
                shortAuthString = shortAuthString(handle.handshakeHash, frame.sosId),
                chat = emptyList(),
            )
            publishIncomingLocked()
        }
        metrics.record("sos_offer_rx", mapOf("link" to handle.linkId))
    }

    private suspend fun onSimple(handle: SessionHandle, frame: SosSimpleFrame) {
        val id = frame.sosId.toHex()
        when (frame.type) {
            FrameType.SOS_ACCEPT -> {
                // First confirmed accept wins; later acceptors are told someone else is helping (audit C).
                val reply = synchronized(lock) {
                    val own = incident?.takeIf { it.sosId.toHex() == id && handle.peerSessionKey in it.offeredSessions }
                    when {
                        own == null -> null
                        own.responderSessionKey == null -> {
                            own.responderSessionKey = handle.peerSessionKey
                            own.trust = SosTrustState.ENCRYPTED_UNVERIFIED
                            publishOutgoingLocked()
                            FrameType.SOS_CONFIRM
                        }
                        own.responderSessionKey == handle.peerSessionKey -> FrameType.SOS_CONFIRM
                        else -> FrameType.SOS_BUSY
                    }
                } ?: return
                handle.sendFrame(SosSimpleFrame(reply, frame.sosId))
                metrics.record("sos_accept_rx", mapOf("reply" to reply.name))
                if (reply == FrameType.SOS_CONFIRM) {
                    // Stop other offers and relayed requests for this incident.
                    val own = synchronized(lock) { incident } ?: return
                    val others = sessions().filter { it.peerSessionKey != handle.peerSessionKey && it.peerSessionKey in own.offeredSessions }
                    others.forEach { it.sendFrame(SosSimpleFrame(FrameType.SOS_BUSY, frame.sosId)) }
                }
            }
            FrameType.SOS_DECLINE -> synchronized(lock) {
                incident?.takeIf { it.sosId.toHex() == id }?.let {
                    it.declinedSessions += handle.peerSessionKey
                    publishOutgoingLocked()
                }
            }
            FrameType.SOS_CONFIRM -> updateIncoming(id, handle) { it.copy(phase = IncomingSos.Phase.ACTIVE) }
            FrameType.SOS_BUSY -> updateIncoming(id, handle) {
                if (it.phase == IncomingSos.Phase.ACTIVE) it else it.copy(phase = IncomingSos.Phase.TAKEN_BY_OTHER)
            }
            FrameType.SOS_END -> {
                updateIncoming(id, handle) { it.copy(phase = IncomingSos.Phase.ENDED) }
                synchronized(lock) {
                    incident?.takeIf { it.sosId.toHex() == id && it.responderSessionKey == handle.peerSessionKey }?.let {
                        endIncidentLocked(it, SosEndReason.SESSION_ENDED)
                    }
                }
            }
            else -> Unit
        }
    }

    suspend fun endSession(sosIdHex: String) {
        val target = synchronized(lock) {
            val own = incident?.takeIf { it.sosId.toHex() == sosIdHex }
            if (own != null) {
                val responder = own.responderSessionKey
                endIncidentLocked(own, SosEndReason.SESSION_ENDED)
                broadcastCancel(own, SosEndReason.SESSION_ENDED)
                responder
            } else {
                val state = incoming[sosIdHex] ?: return
                incoming[sosIdHex] = state.copy(phase = IncomingSos.Phase.ENDED)
                publishIncomingLocked()
                state.sessionKey
            }
        }
        sessionFor(target)?.sendFrame(SosSimpleFrame(FrameType.SOS_END, hexToBytes(sosIdHex)))
    }

    private fun updateIncoming(id: String, handle: SessionHandle, change: (IncomingSos) -> IncomingSos) = synchronized(lock) {
        val state = incoming[id]?.takeIf { it.sessionKey == handle.peerSessionKey } ?: return@synchronized
        incoming[id] = change(state)
        publishIncomingLocked()
    }

    private fun onChat(handle: SessionHandle, frame: SosChatFrame) = synchronized(lock) {
        val id = frame.sosId.toHex()
        val line = SosChatLine(false, frame.language, frame.text, clock.wallMs())
        val own = incident
        if (own != null && own.sosId.toHex() == id && own.responderSessionKey == handle.peerSessionKey) {
            own.chat += line
            publishOutgoingLocked()
            return@synchronized
        }
        val state = incoming[id]?.takeIf { it.sessionKey == handle.peerSessionKey && it.phase == IncomingSos.Phase.ACTIVE } ?: return@synchronized
        incoming[id] = state.copy(chat = state.chat + line)
        publishIncomingLocked()
    }

    private suspend fun onRequest(handle: SessionHandle, frame: SosRequestFrame) {
        if (!offerLimiter.tryAcquire("req:" + handle.peerSessionKey, clock.elapsedMs())) return
        if (!Primitives.ed25519Verify(frame.incidentPublicKey, Domains.SOS_REQUEST, frame.signedBytes(), frame.signature)) {
            metrics.record("sos_request_bad_signature", emptyMap())
            return
        }
        val id = frame.sosId.toHex()
        val decision = relayRouter.decide(frame.requestFrameId.toHex(), id, frame.hopCount, frame.hopLimit, frame.remainingTtlMs, clock.elapsedMs())
        metrics.record("sos_request_rx", mapOf("decision" to decision.name, "hop" to frame.hopCount))
        if (decision == SosRelayRouter.Decision.DUPLICATE || decision == SosRelayRouter.Decision.CANCELLED ||
            decision == SosRelayRouter.Decision.EXPIRED
        ) return
        synchronized(lock) {
            incidentKeys[id] = frame.incidentPublicKey
            val ownIncident = incident?.sosId?.toHex() == id
            if (availableToHelp && !ownIncident && incoming[id] == null) {
                incoming[id] = IncomingSos(
                    sosIdHex = id, sessionKey = null, category = frame.category, language = frame.language,
                    ageMs = frame.ageMs, proximity = null, connection = SosConnectionKind.RELAYED,
                    phase = IncomingSos.Phase.RELAYED_NOTICE, trust = SosTrustState.ENCRYPTED_UNVERIFIED,
                    shortAuthString = null, chat = emptyList(),
                )
                publishIncomingLocked()
            }
        }
        if (decision != SosRelayRouter.Decision.FORWARD) return
        val jitter = relayRouter.jitterMs()
        scope.launch {
            delay(jitter)
            val forwarded = frame.forwarded(jitter)
            if (forwarded.remainingTtlMs <= 0) return@launch
            sessions().filter { it.peerSessionKey != handle.peerSessionKey }.forEach { runCatching { it.sendFrame(forwarded) } }
            metrics.record("sos_request_forward", mapOf("hop" to forwarded.hopCount, "jitter_ms" to jitter))
        }
    }

    private suspend fun onCancel(handle: SessionHandle, frame: SosCancelFrame) {
        val id = frame.sosId.toHex()
        val key = synchronized(lock) { incidentKeys[id] } ?: return
        if (!Primitives.ed25519Verify(key, Domains.SOS_CANCEL, SosCancelFrame.signedBytes(frame.sosId, frame.reason), frame.signature)) return
        if (!relayRouter.cancel(id, clock.elapsedMs())) return
        synchronized(lock) {
            incoming[id]?.let { if (it.phase != IncomingSos.Phase.ACTIVE) incoming[id] = it.copy(phase = IncomingSos.Phase.ENDED) }
            publishIncomingLocked()
        }
        sessions().filter { it.peerSessionKey != handle.peerSessionKey }.forEach { runCatching { it.sendFrame(frame) } }
    }

    override fun onSessionClosed(handle: SessionHandle) {
        synchronized(lock) {
            incoming.entries.filter { it.value.sessionKey == handle.peerSessionKey && it.value.phase != IncomingSos.Phase.ENDED }.forEach {
                incoming[it.key] = it.value.copy(phase = IncomingSos.Phase.ENDED)
            }
            publishIncomingLocked()
            incident?.let { own ->
                own.offeredSessions.remove(handle.peerSessionKey)
                if (own.responderSessionKey == handle.peerSessionKey) {
                    // Responder vanished: resume searching rather than claiming a live connection.
                    own.responderSessionKey = null
                    own.trust = null
                }
                publishOutgoingLocked()
            }
        }
    }

    private fun publishIncomingLocked() {
        mutableIncoming.value = incoming.values.toList().takeLast(16)
        while (incoming.size > 32) incoming.remove(incoming.keys.first())
    }

    private fun sessionFor(key: String?): SessionHandle? = key?.let { k -> sessions().firstOrNull { it.peerSessionKey == k } }

    companion object {
        /** 6-digit SAS from the Noise handshake hash, bound to the incident (audit #6, spec §37 State 2). */
        fun shortAuthString(handshakeHash: ByteArray, sosId: ByteArray): String {
            val digest = Primitives.sha256(Domains.SOS_SAS, handshakeHash, sosId)
            val value = ((digest[0].toLong() and 0xFF) shl 24) or ((digest[1].toLong() and 0xFF) shl 16) or
                ((digest[2].toLong() and 0xFF) shl 8) or (digest[3].toLong() and 0xFF)
            return "%06d".format(value % 1_000_000)
        }

        private fun hexToBytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
