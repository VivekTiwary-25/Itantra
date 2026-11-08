package com.chmod777.itantra.protocol

import com.chmod777.itantra.crypto.Domains
import com.chmod777.itantra.crypto.Primitives
import com.chmod777.itantra.protocol.cbor.CborCodec
import com.chmod777.itantra.protocol.cbor.CborLimits
import com.chmod777.itantra.protocol.cbor.MalformedInputException
import com.chmod777.itantra.protocol.cbor.asMap
import com.chmod777.itantra.protocol.cbor.bytes
import com.chmod777.itantra.protocol.cbor.bytesUpTo
import com.chmod777.itantra.protocol.cbor.cborMap
import com.chmod777.itantra.protocol.cbor.int
import com.chmod777.itantra.protocol.cbor.requireOnlyKeys
import com.chmod777.itantra.protocol.cbor.text
import com.chmod777.itantra.protocol.cbor.uint

const val ID_BYTES = 16
const val HASH_BYTES = 32
const val SECRET_BYTES = 32

/** v1 only has private bundles; message vs receipt is hidden inside the ciphertext. */
enum class BundleKind(val wire: Int) { PRIVATE(1) }

/**
 * Relay-visible immutable envelope of a private bundle (spec §16).
 * The relay-mutable state travels separately as [RelayState].
 */
class PrivateBundleV1(
    val bundleId: ByteArray,
    val destinationTag: ByteArray,
    val lifetimeMs: Long,
    val priority: Priority,
    val deletionCommitment: ByteArray,
    val ciphertextHash: ByteArray,
    val ciphertext: ByteArray,
    val kind: BundleKind = BundleKind.PRIVATE,
) {
    init {
        require(bundleId.size == ID_BYTES && destinationTag.size == ID_BYTES)
        require(deletionCommitment.size == HASH_BYTES && ciphertextHash.size == HASH_BYTES)
    }

    fun encodeImmutable(): ByteArray = CborCodec.encode(
        cborMap {
            put(1, PROTOCOL_VERSION)
            put(2, bundleId)
            put(3, destinationTag)
            put(4, lifetimeMs)
            put(5, priority.wire)
            put(6, deletionCommitment)
            put(7, ciphertextHash)
            put(8, ciphertext)
            put(9, kind.wire)
        },
    )

    companion object {
        const val PROTOCOL_VERSION = 1

        fun deletionCommitment(deletionSecret: ByteArray): ByteArray =
            Primitives.sha256(Domains.DELETE, deletionSecret)

        /** Hard validation used by relays: bounds, version, and ciphertext hash. */
        @Throws(MalformedInputException::class)
        fun decodeImmutable(bytes: ByteArray, config: ProtocolConfig): PrivateBundleV1 {
            val map = CborCodec.decode(
                bytes,
                CborLimits(maxInputBytes = config.maxCiphertextBytes + 256, maxDepth = 1, maxContainerItems = 9),
            ).asMap()
            map.requireOnlyKeys(1, 2, 3, 4, 5, 6, 7, 8, 9)
            map.uint(1, PROTOCOL_VERSION.toLong()..PROTOCOL_VERSION.toLong())
            val lifetime = map.uint(4, 1..config.normalPrivateLifetimeMs.coerceAtLeast(config.urgentPrivateLifetimeMs))
            val priority = Priority.fromWire(map.uint(5)) ?: throw MalformedInputException("unknown priority")
            map.int(9, BundleKind.PRIVATE.wire..BundleKind.PRIVATE.wire)
            val ciphertext = map.bytesUpTo(8, config.maxCiphertextBytes, minSize = 1)
            val claimedHash = map.bytes(7, HASH_BYTES)
            if (!Primitives.constantTimeEquals(claimedHash, Primitives.sha256(ciphertext))) {
                throw MalformedInputException("ciphertext_hash does not match ciphertext")
            }
            return PrivateBundleV1(
                bundleId = map.bytes(2, ID_BYTES),
                destinationTag = map.bytes(3, ID_BYTES),
                lifetimeMs = lifetime,
                priority = priority,
                deletionCommitment = map.bytes(6, HASH_BYTES),
                ciphertextHash = claimedHash,
                ciphertext = ciphertext,
            )
        }
    }
}

/** Relay-mutable state (spec §16). Unauthenticated by design; relays may lie about it. */
data class RelayState(val copyTokens: Int, val hopCount: Int, val accumulatedAgeMs: Long)

/** Relay cleanup object (spec §28). */
class TombstoneV1(val ackOfBundleId: ByteArray, val deletionSecret: ByteArray, val remainingLifetimeMs: Long) {
    init {
        require(ackOfBundleId.size == ID_BYTES && deletionSecret.size == SECRET_BYTES)
    }

    fun encode(): ByteArray = CborCodec.encode(
        cborMap {
            put(1, PrivateBundleV1.PROTOCOL_VERSION)
            put(2, ackOfBundleId)
            put(3, deletionSecret)
            put(4, remainingLifetimeMs)
        },
    )

    fun matches(deletionCommitment: ByteArray): Boolean =
        Primitives.constantTimeEquals(PrivateBundleV1.deletionCommitment(deletionSecret), deletionCommitment)

    companion object {
        @Throws(MalformedInputException::class)
        fun decode(bytes: ByteArray, config: ProtocolConfig): TombstoneV1 {
            val map = CborCodec.decode(bytes, CborLimits(maxInputBytes = 128, maxDepth = 1, maxContainerItems = 4)).asMap()
            map.requireOnlyKeys(1, 2, 3, 4)
            map.uint(1, 1L..1L)
            return TombstoneV1(
                ackOfBundleId = map.bytes(2, ID_BYTES),
                deletionSecret = map.bytes(3, SECRET_BYTES),
                remainingLifetimeMs = map.uint(4, 0..config.normalPrivateLifetimeMs),
            )
        }
    }
}

/**
 * Fields shared by both signed inner objects. `bundleId`, `lifetimeMs`,
 * `priority` and the deletion secret are authenticated copies of the outer
 * envelope (audit #3).
 */
sealed interface SignedInner {
    val signerNodeId: ByteArray
    val addresseeNodeId: ByteArray
    val bundleId: ByteArray
    val deletionSecret: ByteArray
    val lifetimeMs: Long
    val priority: Priority
    fun encode(): ByteArray
}

/** Private message payload (spec §17 + audit #3). */
class PrivateMessageV1(
    val innerMessageId: ByteArray,
    val senderNodeId: ByteArray,
    val recipientNodeId: ByteArray,
    val senderSigningPublicKey: ByteArray,
    val language: String,
    val text: String,
    val createdTimeHintMs: Long,
    override val deletionSecret: ByteArray,
    override val bundleId: ByteArray,
    override val lifetimeMs: Long,
    override val priority: Priority,
) : SignedInner {
    override val signerNodeId get() = senderNodeId
    override val addresseeNodeId get() = recipientNodeId

    override fun encode(): ByteArray = CborCodec.encode(
        cborMap {
            put(1, PrivateBundleV1.PROTOCOL_VERSION)
            put(2, innerMessageId)
            put(3, senderNodeId)
            put(4, recipientNodeId)
            put(5, senderSigningPublicKey)
            put(6, language)
            put(7, text)
            put(8, createdTimeHintMs)
            put(9, deletionSecret)
            put(10, bundleId)
            put(11, lifetimeMs)
            put(12, priority.wire)
        },
    )

    companion object {
        const val MAX_LANGUAGE_BYTES = 16

        @Throws(MalformedInputException::class)
        fun decode(bytes: ByteArray, config: ProtocolConfig): PrivateMessageV1 {
            val map = CborCodec.decode(
                bytes,
                CborLimits(maxInputBytes = config.privateTextMaxBytes + 512, maxDepth = 1, maxContainerItems = 12),
            ).asMap()
            map.requireOnlyKeys(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
            map.uint(1, 1L..1L)
            return PrivateMessageV1(
                innerMessageId = map.bytes(2, ID_BYTES),
                senderNodeId = map.bytes(3, ID_BYTES),
                recipientNodeId = map.bytes(4, ID_BYTES),
                senderSigningPublicKey = map.bytes(5, Primitives.KEY_BYTES),
                language = map.text(6, MAX_LANGUAGE_BYTES, minBytes = 1),
                text = map.text(7, config.privateTextMaxBytes, minBytes = 1),
                createdTimeHintMs = map.uint(8),
                deletionSecret = map.bytes(9, SECRET_BYTES),
                bundleId = map.bytes(10, ID_BYTES),
                lifetimeMs = map.uint(11),
                priority = Priority.fromWire(map.uint(12)) ?: throw MalformedInputException("unknown priority"),
            )
        }
    }
}

/** Signed end-to-end delivery receipt (spec §29 + audit B/#3/#5). */
class DeliveryReceiptV1(
    val ackOfBundleId: ByteArray,
    val ackOfInnerMessageId: ByteArray,
    val recipientNodeId: ByteArray,
    val originalSenderNodeId: ByteArray,
    val receiptTimeHintMs: Long,
    override val deletionSecret: ByteArray,
    override val bundleId: ByteArray,
    override val lifetimeMs: Long,
    override val priority: Priority,
) : SignedInner {
    override val signerNodeId get() = recipientNodeId
    override val addresseeNodeId get() = originalSenderNodeId

    override fun encode(): ByteArray = CborCodec.encode(
        cborMap {
            put(1, PrivateBundleV1.PROTOCOL_VERSION)
            put(2, ackOfBundleId)
            put(3, ackOfInnerMessageId)
            put(4, recipientNodeId)
            put(5, STATUS_DELIVERED)
            put(6, receiptTimeHintMs)
            put(7, originalSenderNodeId)
            put(8, deletionSecret)
            put(9, bundleId)
            put(10, lifetimeMs)
            put(11, priority.wire)
        },
    )

    companion object {
        const val STATUS_DELIVERED = 1

        @Throws(MalformedInputException::class)
        fun decode(bytes: ByteArray): DeliveryReceiptV1 {
            val map = CborCodec.decode(bytes, CborLimits(maxInputBytes = 512, maxDepth = 1, maxContainerItems = 11)).asMap()
            map.requireOnlyKeys(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
            map.uint(1, 1L..1L)
            map.uint(5, STATUS_DELIVERED.toLong()..STATUS_DELIVERED.toLong())
            return DeliveryReceiptV1(
                ackOfBundleId = map.bytes(2, ID_BYTES),
                ackOfInnerMessageId = map.bytes(3, ID_BYTES),
                recipientNodeId = map.bytes(4, ID_BYTES),
                originalSenderNodeId = map.bytes(7, ID_BYTES),
                receiptTimeHintMs = map.uint(6),
                deletionSecret = map.bytes(8, SECRET_BYTES),
                bundleId = map.bytes(9, ID_BYTES),
                lifetimeMs = map.uint(10),
                priority = Priority.fromWire(map.uint(11)) ?: throw MalformedInputException("unknown priority"),
            )
        }
    }
}
