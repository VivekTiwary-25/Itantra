package com.chmod777.itantra.dtn

import com.chmod777.itantra.crypto.OpenFailure
import com.chmod777.itantra.crypto.OpenedPayload
import com.chmod777.itantra.crypto.PrivateMessageCrypto
import com.chmod777.itantra.identity.LocalIdentity
import com.chmod777.itantra.identity.TrustedContact
import com.chmod777.itantra.identity.TrustedContactStore
import com.chmod777.itantra.protocol.AckStatus
import com.chmod777.itantra.protocol.BundleAckFrame
import com.chmod777.itantra.protocol.BundleFrame
import com.chmod777.itantra.protocol.BundleSummary
import com.chmod777.itantra.protocol.Priority
import com.chmod777.itantra.protocol.PrivateBundleV1
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.protocol.RelayState
import com.chmod777.itantra.protocol.TombstoneV1
import com.chmod777.itantra.protocol.WantEntry
import com.chmod777.itantra.protocol.WantMode
import com.chmod777.itantra.protocol.cbor.MalformedInputException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Observable DTN events for the product UI, TTS boundary and metrics. Never carries relay plaintext. */
sealed interface DtnEvent {
    /** This phone is the authenticated end recipient. The only path that may trigger TTS. */
    data class MessageDelivered(val message: DeliveredMessage, val from: TrustedContact) : DtnEvent
    data class OutgoingStateChanged(val message: OutgoingMessage) : DtnEvent
    data class BundleStored(val bundleKeyShort: String, val origin: BundleOrigin, val tokens: Int, val hopCount: Int) : DtnEvent
    data class BundleRemoved(val bundleKeyShort: String, val reason: String) : DtnEvent
    data class Rejected(val reason: String) : DtnEvent
    data object StoreChanged : DtnEvent
}

/**
 * Per-session abuse budget (spec §52). One instance per Noise session; a new
 * session identity starts fresh but is still bounded.
 */
class SessionBudget(val peerSessionKey: String) {
    var destinationClaims = 0
    var bytesAccepted = 0L
    var bundlesAccepted = 0
    val outstandingWants = HashMap<String, WantMode>()
}

/** Result of processing one inbound bundle: the hop ACK plus any tombstones to push back. */
class IngestResult(val ack: BundleAckFrame, val tombstones: List<TombstoneV1>)

/**
 * The store-carry-forward node (spec §20–§31, §48). Pure Kotlin: storage,
 * crypto and time are injected, and all state changes are serialised on [lock].
 */
class DtnNode(
    private val identity: LocalIdentity,
    private val contacts: TrustedContactStore,
    private val store: DtnStore,
    private val clock: DtnClock,
    private val config: ProtocolConfig = ProtocolConfig.DEFAULT,
    private val crypto: PrivateMessageCrypto = PrivateMessageCrypto(config),
) {
    private val lock = Any()
    private val router = SprayRouter(config)
    private val mutableEvents = MutableSharedFlow<DtnEvent>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<DtnEvent> = mutableEvents

    val localNodeId: ByteArray get() = identity.nodeId

    private fun emit(event: DtnEvent) {
        mutableEvents.tryEmit(event)
    }

    // ------------------------------------------------------------------ origin

    /**
     * Outgoing trusted-message pipeline (spec §50): resolve contact by node_id →
     * sign → seal → tag → bundle → persist BEFORE any transmission.
     */
    fun createMessage(recipientNodeId: ByteArray, text: String, language: String, priority: Priority = Priority.NORMAL): OutgoingMessage {
        val recipient = contacts.byNodeId(recipientNodeId)
            ?: throw IllegalArgumentException("Cannot securely identify this recipient.")
        val created = crypto.createMessage(identity, recipient, text, language, priority, clock.wallMs())
        val now = clock.wallMs()
        val outgoing = OutgoingMessage(
            bundleId = created.bundle.bundleId,
            innerMessageId = created.innerMessageId!!,
            recipientNodeId = recipient.nodeId,
            text = text,
            language = language,
            deletionSecret = created.deletionSecret,
            createdWallMs = now,
            state = DeliveryState.QUEUED,
            updatedWallMs = now,
        )
        synchronized(lock) {
            store.transaction {
                store.putBundle(newLocalBundle(created.bundle, priority))
                store.putOutgoing(outgoing)
                markSeen(created.bundle)
            }
        }
        emit(DtnEvent.OutgoingStateChanged(outgoing))
        emit(DtnEvent.StoreChanged)
        return outgoing
    }

    private fun newLocalBundle(bundle: PrivateBundleV1, priority: Priority) = StoredBundle(
        bundle = bundle,
        immutableBytes = bundle.encodeImmutable(),
        copyTokens = router.initialTokens(priority),
        hopCount = 0,
        age = AgeState.startingAt(0, clock),
        origin = BundleOrigin.LOCAL,
        sourcePeerSessionKey = null,
        createdWallMs = clock.wallMs(),
    )

    private fun markSeen(bundle: PrivateBundleV1) {
        val retainUntil = clock.wallMs() + bundle.lifetimeMs + config.seenGraceMs
        store.putSeen(SeenRecord(bundle.bundleId, bundle.ciphertextHash, bundle.deletionCommitment, retainUntil))
    }

    // --------------------------------------------------------------- inventory

    /** Bounded, non-expired, plaintext-free inventory (spec §24). */
    fun inventory(): List<BundleSummary> = synchronized(lock) {
        store.allBundles()
            .filter { !it.isExpired(clock) && store.tombstone(it.bundle.bundleId) == null }
            .sortedWith(compareByDescending<StoredBundle> { it.bundle.priority.wire }.thenByDescending { it.remainingLifetimeMs(clock) })
            .take(config.inventoryPageSize * config.maxInventoryPages)
            .map {
                BundleSummary(
                    bundleId = it.bundle.bundleId,
                    ciphertextHash = it.bundle.ciphertextHash,
                    destinationTag = it.bundle.destinationTag,
                    kind = it.bundle.kind,
                    priority = it.bundle.priority,
                    remainingLifetimeMs = it.remainingLifetimeMs(clock),
                    payloadSize = it.bundle.ciphertext.size,
                    copyTokens = router.availableTokens(it),
                )
            }
    }

    /** What to push and pull after receiving a peer's complete inventory. */
    class EncounterPlan(val wants: List<WantEntry>, val tombstones: List<TombstoneV1>)

    fun planEncounter(peerSummaries: List<BundleSummary>, budget: SessionBudget): EncounterPlan = synchronized(lock) {
        val wants = ArrayList<WantEntry>()
        val tombstones = ArrayList<TombstoneV1>()
        val contactList = contacts.all()
        for (summary in peerSummaries) {
            val storageKey = key(summary.bundleId, summary.ciphertextHash)
            // Reconcile an unacknowledged split: this same session peer now evidently holds the bundle (audit A).
            store.bundle(storageKey)?.let { ours ->
                val pending = ours.pendingSplit
                if (pending != null && pending.peerSessionKey == budget.peerSessionKey) commitSplit(ours)
            }
            val tombstone = store.tombstone(summary.bundleId)
            if (tombstone != null) {
                tombstones += TombstoneV1(tombstone.bundleId, tombstone.deletionSecret, remainingTombstoneMs(tombstone))
                continue
            }
            if (store.bundle(storageKey) != null || store.seen(storageKey) != null) continue
            if (wants.size >= config.maxWantsPerEncounterRound) continue
            val isForMe = crypto.matchDestinationTag(identity, contactList, summary.bundleId, summary.destinationTag) != null
            val mode = when {
                isForMe -> WantMode.DESTINATION
                router.shouldRequestRelay(summary) && hasRoomFor(summary.payloadSize.toLong(), budget.peerSessionKey) -> WantMode.RELAY
                else -> null
            } ?: continue
            wants += WantEntry(summary.bundleId, summary.ciphertextHash, mode)
            budget.outstandingWants[storageKey] = mode
        }
        EncounterPlan(wants, tombstones.take(config.maxWantsPerEncounterRound))
    }

    private fun remainingTombstoneMs(record: TombstoneRecord) = (record.expiresAtWallMs - clock.wallMs()).coerceAtLeast(0)

    // ----------------------------------------------------------------- serving

    /**
     * Serves one WANT (spec §23, §25). A destination claim gets a delivery copy but
     * never deletes our copy; a relay request gets floor(n/2) tokens, recorded as a
     * pending split until the hop ACK arrives.
     */
    fun serveWant(want: WantEntry, budget: SessionBudget): BundleFrame? = synchronized(lock) {
        val storageKey = key(want.bundleId, want.ciphertextHash)
        val stored = store.bundle(storageKey) ?: return null
        if (stored.isExpired(clock) || store.tombstone(want.bundleId) != null) return null
        val ageMs = stored.age.effectiveAgeMs(clock)
        when (want.mode) {
            WantMode.DESTINATION -> {
                if (budget.destinationClaims >= config.maxDestinationClaimsPerSession) {
                    emit(DtnEvent.Rejected("destination-claim rate limit"))
                    return null
                }
                budget.destinationClaims++
                BundleFrame(WantMode.DESTINATION, stored.immutableBytes, RelayState(0, stored.hopCount, ageMs))
            }
            WantMode.RELAY -> {
                val give = router.tokensToGive(stored)
                if (give < 1) return null
                store.putBundle(
                    stored.copy(pendingSplit = PendingSplit(give, budget.peerSessionKey, clock.elapsedMs(), clock.bootCount())),
                )
                BundleFrame(WantMode.RELAY, stored.immutableBytes, RelayState(give, stored.hopCount, ageMs))
            }
        }
    }

    // ------------------------------------------------------------------ ingest

    fun ingest(frame: BundleFrame, budget: SessionBudget): IngestResult = synchronized(lock) {
        val bundle = try {
            PrivateBundleV1.decodeImmutable(frame.immutableBytes, config)
        } catch (e: MalformedInputException) {
            emit(DtnEvent.Rejected("malformed bundle: ${e.message}"))
            return IngestResult(BundleAckFrame(ByteArray(16), ByteArray(32), AckStatus.REJECTED), emptyList())
        }
        val storageKey = key(bundle.bundleId, bundle.ciphertextHash)
        fun ack(status: AckStatus, tombstones: List<TombstoneV1> = emptyList()) =
            IngestResult(BundleAckFrame(bundle.bundleId, bundle.ciphertextHash, status), tombstones)

        // Only bundles this node explicitly asked this peer for are accepted.
        if (budget.outstandingWants.remove(storageKey) != frame.mode) {
            emit(DtnEvent.Rejected("unsolicited bundle"))
            return ack(AckStatus.REJECTED)
        }
        store.tombstone(bundle.bundleId)?.let { record ->
            return ack(AckStatus.TOMBSTONED, listOf(TombstoneV1(record.bundleId, record.deletionSecret, remainingTombstoneMs(record))))
        }
        if (frame.relayState.accumulatedAgeMs >= bundle.lifetimeMs) return ack(AckStatus.REJECTED)
        if (budget.bytesAccepted + frame.immutableBytes.size > config.maxBytesAcceptedPerSession ||
            budget.bundlesAccepted >= config.maxBundlesAcceptedPerSession
        ) {
            emit(DtnEvent.Rejected("per-session acceptance cap"))
            return ack(AckStatus.REJECTED)
        }
        budget.bytesAccepted += frame.immutableBytes.size
        budget.bundlesAccepted++

        return when (frame.mode) {
            WantMode.DESTINATION -> ingestAsDestination(bundle, ::ack)
            WantMode.RELAY -> ingestAsRelay(bundle, frame, budget, storageKey, ::ack)
        }
    }

    private fun ingestAsRelay(
        bundle: PrivateBundleV1,
        frame: BundleFrame,
        budget: SessionBudget,
        storageKey: String,
        ack: (AckStatus, List<TombstoneV1>) -> IngestResult,
    ): IngestResult {
        if (store.bundle(storageKey) != null || store.seen(storageKey) != null) return ack(AckStatus.ALREADY_HAVE, emptyList())
        val newHopCount = frame.relayState.hopCount + 1
        if (frame.relayState.copyTokens < 1 || newHopCount > config.privateHopGuard) return ack(AckStatus.REJECTED, emptyList())
        if (!makeRoomFor(frame.immutableBytes.size.toLong(), budget.peerSessionKey)) {
            emit(DtnEvent.Rejected("storage full"))
            return ack(AckStatus.REJECTED, emptyList())
        }
        val stored = StoredBundle(
            bundle = bundle,
            immutableBytes = frame.immutableBytes,
            copyTokens = frame.relayState.copyTokens,
            hopCount = newHopCount,
            age = AgeState.startingAt(frame.relayState.accumulatedAgeMs, clock),
            origin = BundleOrigin.RELAY,
            sourcePeerSessionKey = budget.peerSessionKey,
            createdWallMs = clock.wallMs(),
        )
        // Persist before acknowledging: the ACK means "I persisted these bytes" (spec §20).
        store.transaction {
            store.putBundle(stored)
            markSeen(bundle)
        }
        emit(DtnEvent.BundleStored(stored.storageKey.take(8), BundleOrigin.RELAY, stored.copyTokens, stored.hopCount))
        emit(DtnEvent.StoreChanged)
        return ack(AckStatus.PERSISTED, emptyList())
    }

    /**
     * Recipient processing (spec §26 + audit #1/#3/#4). Seen/delivered state is
     * written only after every cryptographic and identity check succeeds.
     */
    private fun ingestAsDestination(bundle: PrivateBundleV1, ack: (AckStatus, List<TombstoneV1>) -> IngestResult): IngestResult {
        val sender = crypto.matchDestination(identity, contacts.all(), bundle) ?: run {
            emit(DtnEvent.Rejected("destination tag does not match any trusted contact"))
            return ack(AckStatus.REJECTED, emptyList())
        }
        val opened = try {
            crypto.open(identity, sender, bundle)
        } catch (e: OpenFailure) {
            emit(DtnEvent.Rejected("destination open failed: ${e.reason}"))
            return ack(AckStatus.REJECTED, emptyList())
        }
        val tombstone = TombstoneV1(bundle.bundleId, opened.deletionSecret, bundle.lifetimeMs)
        store.transaction {
            store.putTombstone(TombstoneRecord(bundle.bundleId, opened.deletionSecret, clock.wallMs() + bundle.lifetimeMs))
            markSeen(bundle)
            // Any relay copy we may hold is now obsolete.
            store.bundlesById(bundle.bundleId).forEach { store.deleteBundle(it.storageKey) }
            when (opened) {
                is OpenedPayload.Message -> acceptMessage(opened, bundle)
                is OpenedPayload.Receipt -> acceptReceipt(opened)
            }
        }
        emit(DtnEvent.StoreChanged)
        return ack(AckStatus.ACCEPTED_AS_DESTINATION_ATTEMPT, listOf(tombstone))
    }

    private fun acceptMessage(opened: OpenedPayload.Message, bundle: PrivateBundleV1) {
        val message = opened.message
        if (store.delivered(message.innerMessageId) != null) return // Show / speak once (spec §26 step 6–8).
        val delivered = DeliveredMessage(
            innerMessageId = message.innerMessageId,
            bundleId = bundle.bundleId,
            senderNodeId = message.senderNodeId,
            language = message.language,
            text = message.text,
            createdTimeHintMs = message.createdTimeHintMs,
            receivedWallMs = clock.wallMs(),
        )
        store.putDelivered(delivered)
        // Signed end-to-end receipt, routed back as its own private bundle. Receipts never get receipts.
        val receipt = crypto.createReceipt(identity, opened.from, message, clock.wallMs())
        store.putBundle(newLocalBundle(receipt.bundle, receipt.bundle.priority))
        markSeen(receipt.bundle)
        emit(DtnEvent.MessageDelivered(delivered, opened.from))
    }

    private fun acceptReceipt(opened: OpenedPayload.Receipt) {
        val receipt = opened.receipt
        val outgoing = store.outgoing(receipt.ackOfBundleId) ?: return
        // The receipt must come from the contact we addressed, for the exact inner message.
        if (!outgoing.recipientNodeId.contentEquals(opened.from.nodeId) ||
            !outgoing.innerMessageId.contentEquals(receipt.ackOfInnerMessageId)
        ) {
            emit(DtnEvent.Rejected("receipt does not match the outgoing message"))
            return
        }
        store.putReceipt(ReceiptRecord(receipt.ackOfBundleId, receipt.recipientNodeId, receipt.receiptTimeHintMs, clock.wallMs()))
        // We know our own deletion secret: stop carrying the original and tombstone it.
        store.bundlesById(outgoing.bundleId).forEach { store.deleteBundle(it.storageKey) }
        store.putTombstone(TombstoneRecord(outgoing.bundleId, outgoing.deletionSecret, clock.wallMs() + config.normalPrivateLifetimeMs))
        if (outgoing.state != DeliveryState.DELIVERED) {
            val updated = outgoing.copy(state = DeliveryState.DELIVERED, updatedWallMs = clock.wallMs())
            store.putOutgoing(updated)
            emit(DtnEvent.OutgoingStateChanged(updated))
        }
    }

    // -------------------------------------------------------------------- acks

    /** Hop ACK handling. Only moves QUEUED → RELAYED; DELIVERED needs a verified receipt. */
    fun onAck(ack: BundleAckFrame, budget: SessionBudget) = synchronized(lock) {
        val stored = store.bundle(key(ack.bundleId, ack.ciphertextHash))
        if (stored != null && stored.pendingSplit?.peerSessionKey == budget.peerSessionKey) {
            if (ack.status == AckStatus.PERSISTED) commitSplit(stored) else store.putBundle(stored.copy(pendingSplit = null))
        }
        if (ack.status == AckStatus.PERSISTED || ack.status == AckStatus.ACCEPTED_AS_DESTINATION_ATTEMPT) {
            store.outgoing(ack.bundleId)?.let { outgoing ->
                if (outgoing.state == DeliveryState.QUEUED) {
                    val updated = outgoing.copy(state = DeliveryState.RELAYED, updatedWallMs = clock.wallMs())
                    store.putOutgoing(updated)
                    emit(DtnEvent.OutgoingStateChanged(updated))
                }
            }
        }
    }

    private fun commitSplit(stored: StoredBundle) {
        store.putBundle(stored.copy(copyTokens = router.tokensAfterCommit(stored), pendingSplit = null))
    }

    // -------------------------------------------------------------- tombstones

    /**
     * Applies a tombstone only with local evidence (spec §28): a stored bundle or
     * seen entry whose deletion commitment it opens. Returns true when applied.
     */
    fun onTombstone(bytes: ByteArray): Boolean = synchronized(lock) {
        val tombstone = try {
            TombstoneV1.decode(bytes, config)
        } catch (_: MalformedInputException) {
            emit(DtnEvent.Rejected("malformed tombstone"))
            return false
        }
        if (store.tombstone(tombstone.ackOfBundleId) != null) return true // Replay: idempotent.
        val bundles = store.bundlesById(tombstone.ackOfBundleId)
        val seen = store.seenById(tombstone.ackOfBundleId)
        val matchingBundles = bundles.filter { tombstone.matches(it.bundle.deletionCommitment) }
        val evidence = matchingBundles.isNotEmpty() || seen.any { tombstone.matches(it.deletionCommitment) }
        if (!evidence) return false
        val ourLifetime = (bundles.map { it.remainingLifetimeMs(clock) } + seen.map { it.retainUntilWallMs - clock.wallMs() })
            .maxOrNull() ?: 0
        val retainMs = minOf(tombstone.remainingLifetimeMs, ourLifetime.coerceAtLeast(0), config.normalPrivateLifetimeMs)
        store.transaction {
            matchingBundles.forEach {
                store.deleteBundle(it.storageKey)
                emit(DtnEvent.BundleRemoved(it.storageKey.take(8), "tombstone"))
            }
            store.putTombstone(TombstoneRecord(tombstone.ackOfBundleId, tombstone.deletionSecret, clock.wallMs() + retainMs + config.seenGraceMs))
        }
        emit(DtnEvent.StoreChanged)
        true
    }

    // ------------------------------------------------------------- expiry/space

    /**
     * Expiry sweep (spec §30, audit #8): deletes expired bundles, persists re-based
     * ages, rolls back stale pending splits, purges old seen/tombstone rows.
     */
    fun sweep(): Int = synchronized(lock) {
        var removed = 0
        store.transaction {
            for (stored in store.allBundles()) {
                if (stored.isExpired(clock)) {
                    store.deleteBundle(stored.storageKey)
                    removed++
                    emit(DtnEvent.BundleRemoved(stored.storageKey.take(8), "expired"))
                    if (stored.origin == BundleOrigin.LOCAL) setOutgoingState(stored.bundle.bundleId, DeliveryState.EXPIRED)
                    continue
                }
                var updated = stored.copy(age = stored.age.rebased(clock))
                val pending = stored.pendingSplit
                if (pending != null && (pending.bootCount != clock.bootCount() ||
                        clock.elapsedMs() - pending.startedElapsedMs > config.pendingSplitTimeoutMs)
                ) {
                    // Unconfirmed: keep the tokens. This can overcount the budget (audit A).
                    updated = updated.copy(pendingSplit = null)
                }
                store.putBundle(updated)
            }
            // A sender record whose own copy vanished without expiry, tombstone or receipt has lost continuity.
            for (outgoing in store.allOutgoing()) {
                if (outgoing.state == DeliveryState.DELIVERED || outgoing.state == DeliveryState.EXPIRED ||
                    outgoing.state == DeliveryState.UNKNOWN
                ) continue
                if (store.bundlesById(outgoing.bundleId).isEmpty() && store.tombstone(outgoing.bundleId) == null) {
                    setOutgoingState(outgoing.bundleId, DeliveryState.UNKNOWN)
                }
            }
            store.purgeSeen(clock.wallMs())
            store.purgeTombstones(clock.wallMs())
        }
        if (removed > 0) emit(DtnEvent.StoreChanged)
        removed
    }

    private fun setOutgoingState(bundleId: ByteArray, state: DeliveryState) {
        val outgoing = store.outgoing(bundleId) ?: return
        if (outgoing.state == state || outgoing.state == DeliveryState.DELIVERED) return
        val updated = outgoing.copy(state = state, updatedWallMs = clock.wallMs())
        store.putOutgoing(updated)
        emit(DtnEvent.OutgoingStateChanged(updated))
    }

    private fun hasRoomFor(bytes: Long, peerSessionKey: String): Boolean =
        store.bundleBytesFromPeerSession(peerSessionKey) + bytes <= config.maxStoredBytesPerPeerSession &&
            (store.totalBundleBytes() + bytes <= config.maxStoredBytes || evictionCandidates().isNotEmpty())

    /**
     * Storage pressure (spec §31): expired → low-priority normal relay copies,
     * oldest first → urgent relay copies. LOCAL-origin bundles are never evicted.
     */
    private fun makeRoomFor(bytes: Long, peerSessionKey: String): Boolean {
        if (store.bundleBytesFromPeerSession(peerSessionKey) + bytes > config.maxStoredBytesPerPeerSession) return false
        val candidates = evictionCandidates().iterator()
        while (store.totalBundleBytes() + bytes > config.maxStoredBytes || store.allBundles().size >= config.maxStoredBundles) {
            if (!candidates.hasNext()) return false
            val victim = candidates.next()
            store.deleteBundle(victim.storageKey)
            emit(DtnEvent.BundleRemoved(victim.storageKey.take(8), "evicted"))
        }
        return true
    }

    private fun evictionCandidates(): List<StoredBundle> = store.allBundles()
        .filter { it.origin == BundleOrigin.RELAY }
        .sortedWith(
            compareByDescending<StoredBundle> { it.isExpired(clock) }
                .thenBy { it.bundle.priority.wire }
                .thenByDescending { it.age.effectiveAgeMs(clock) },
        )

    // ------------------------------------------------------------------ queries

    fun outgoingMessages(): List<OutgoingMessage> = synchronized(lock) { store.allOutgoing().sortedByDescending { it.createdWallMs } }
    fun deliveredMessages(): List<DeliveredMessage> = synchronized(lock) { store.allDelivered().sortedByDescending { it.receivedWallMs } }
    fun storedBundles(): List<StoredBundle> = synchronized(lock) { store.allBundles() }
}
