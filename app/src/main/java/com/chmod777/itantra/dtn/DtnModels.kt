package com.chmod777.itantra.dtn

import com.chmod777.itantra.crypto.toHex
import com.chmod777.itantra.protocol.PrivateBundleV1

/** Clock abstraction so expiry, reboot and age logic are testable (spec §30, audit #8). */
interface DtnClock {
    /** Monotonic ms since boot (SystemClock.elapsedRealtime on Android). */
    fun elapsedMs(): Long

    /** Settings.Global.BOOT_COUNT on Android; changes on every reboot. */
    fun bootCount(): Int

    /** Wall clock ms, used only for display, seen-entry retention and hints. */
    fun wallMs(): Long

    fun monotonicNs(): Long = elapsedMs() * 1_000_000
}

/** Stable map key for binary IDs. */
fun key(id: ByteArray, hash: ByteArray? = null): String = if (hash == null) id.toHex() else id.toHex() + ":" + hash.toHex()

enum class BundleOrigin {
    /** Created on this phone (message or receipt). Never evicted for space (spec §31, §53). */
    LOCAL,

    /** A relay copy accepted from a peer. */
    RELAY,
}

/**
 * Age bookkeeping that survives reboots (audit #8): the effective age is
 * `ageBaseMs + (elapsed - ageBaseElapsedMs)` only while the boot count matches;
 * after a reboot it resumes from the last persisted `ageBaseMs`.
 */
data class AgeState(val ageBaseMs: Long, val ageBaseElapsedMs: Long, val ageBaseBootCount: Int) {
    fun effectiveAgeMs(clock: DtnClock): Long =
        if (clock.bootCount() == ageBaseBootCount) ageBaseMs + (clock.elapsedMs() - ageBaseElapsedMs).coerceAtLeast(0) else ageBaseMs

    fun rebased(clock: DtnClock): AgeState = AgeState(effectiveAgeMs(clock), clock.elapsedMs(), clock.bootCount())

    companion object {
        fun startingAt(ageMs: Long, clock: DtnClock) = AgeState(ageMs, clock.elapsedMs(), clock.bootCount())
    }
}

/**
 * Two-phase spray split (audit A): tokens promised to one session peer, not yet
 * acknowledged. Committed on hop ACK or later inventory evidence; rolled back after timeout.
 */
data class PendingSplit(val tokens: Int, val peerSessionKey: String, val startedElapsedMs: Long, val bootCount: Int)

/** A stored relay/origin bundle. Never contains plaintext (spec §46). */
data class StoredBundle(
    val bundle: PrivateBundleV1,
    val immutableBytes: ByteArray,
    val copyTokens: Int,
    val hopCount: Int,
    val age: AgeState,
    val origin: BundleOrigin,
    val sourcePeerSessionKey: String?,
    val createdWallMs: Long,
    val pendingSplit: PendingSplit? = null,
) {
    val storageKey: String get() = key(bundle.bundleId, bundle.ciphertextHash)
    val sizeBytes: Int get() = immutableBytes.size

    fun remainingLifetimeMs(clock: DtnClock): Long = (bundle.lifetimeMs - age.effectiveAgeMs(clock)).coerceAtLeast(0)
    fun isExpired(clock: DtnClock): Boolean = age.effectiveAgeMs(clock) >= bundle.lifetimeMs

    override fun equals(other: Any?): Boolean = other is StoredBundle && storageKey == other.storageKey &&
        copyTokens == other.copyTokens && hopCount == other.hopCount && pendingSplit == other.pendingSplit

    override fun hashCode(): Int = storageKey.hashCode()
}

/** Relay dedupe entry keyed by (bundle_id, ciphertext_hash) (audit #4). */
data class SeenRecord(val bundleId: ByteArray, val ciphertextHash: ByteArray, val deletionCommitment: ByteArray, val retainUntilWallMs: Long) {
    val storageKey: String get() = key(bundleId, ciphertextHash)
}

/** Verified tombstone marker (spec §28). */
data class TombstoneRecord(val bundleId: ByteArray, val deletionSecret: ByteArray, val expiresAtWallMs: Long)

/** Sender-visible delivery states (spec §21, §57). */
enum class DeliveryState { QUEUED, RELAYED, DELIVERED, EXPIRED, UNKNOWN }

/** The sender's own record of a message it created. Plaintext here is the user's own sent item. */
data class OutgoingMessage(
    val bundleId: ByteArray,
    val innerMessageId: ByteArray,
    val recipientNodeId: ByteArray,
    val text: String,
    val language: String,
    val deletionSecret: ByteArray,
    val createdWallMs: Long,
    val state: DeliveryState,
    val updatedWallMs: Long,
)

/** A message delivered to this phone as the authenticated end recipient. */
data class DeliveredMessage(
    val innerMessageId: ByteArray,
    val bundleId: ByteArray,
    val senderNodeId: ByteArray,
    val language: String,
    val text: String,
    val createdTimeHintMs: Long,
    val receivedWallMs: Long,
)

/** Verified end-to-end receipt (DeliveryReceiptEntity). */
data class ReceiptRecord(val ackOfBundleId: ByteArray, val recipientNodeId: ByteArray, val receiptTimeHintMs: Long, val verifiedWallMs: Long)
