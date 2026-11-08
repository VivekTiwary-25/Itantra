package com.chmod777.itantra.crypto

import com.chmod777.itantra.identity.LocalIdentity
import com.chmod777.itantra.identity.TrustedContact
import com.chmod777.itantra.protocol.DeliveryReceiptV1
import com.chmod777.itantra.protocol.ID_BYTES
import com.chmod777.itantra.protocol.Priority
import com.chmod777.itantra.protocol.PrivateBundleV1
import com.chmod777.itantra.protocol.PrivateMessageV1
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.protocol.SECRET_BYTES
import com.chmod777.itantra.protocol.SignedInner
import com.chmod777.itantra.protocol.cbor.CborCodec
import com.chmod777.itantra.protocol.cbor.CborLimits
import com.chmod777.itantra.protocol.cbor.MalformedInputException
import com.chmod777.itantra.protocol.cbor.asMap
import com.chmod777.itantra.protocol.cbor.bytes
import com.chmod777.itantra.protocol.cbor.bytesUpTo
import com.chmod777.itantra.protocol.cbor.cborMap
import com.chmod777.itantra.protocol.cbor.int
import com.chmod777.itantra.protocol.cbor.requireOnlyKeys

/** Pairwise destination secret (spec §14 + audit #2). */
object PairSecretDeriver {
    fun pairSecret(own: LocalIdentity, contactEncryptionPublicKey: ByteArray): ByteArray {
        val shared = own.agree(contactEncryptionPublicKey)
        val (low, high) = if (compareUnsigned(own.encryptionPublicKey, contactEncryptionPublicKey) <= 0) {
            own.encryptionPublicKey to contactEncryptionPublicKey
        } else {
            contactEncryptionPublicKey to own.encryptionPublicKey
        }
        return Primitives.hkdfSha256(
            ikm = shared,
            salt = ByteArray(0),
            info = Domains.PAIR_SECRET + low + high,
            size = 32,
        )
    }
}

/** Direction-bound opaque destination tag (spec §15 + audit #2). */
object DestinationTag {
    fun compute(pairSecret: ByteArray, recipientNodeId: ByteArray, bundleId: ByteArray): ByteArray =
        Primitives.hmacSha256(pairSecret, Domains.DESTINATION_TAG, recipientNodeId, bundleId).copyOf(ID_BYTES)
}

/** A bundle this node created, plus the private facts only the originator keeps. */
class CreatedBundle(
    val bundle: PrivateBundleV1,
    val innerMessageId: ByteArray?,
    val deletionSecret: ByteArray,
    val addresseeNodeId: ByteArray,
)

/** What a recipient learns after every check passes. */
sealed interface OpenedPayload {
    val from: TrustedContact
    val deletionSecret: ByteArray

    class Message(override val from: TrustedContact, val message: PrivateMessageV1) : OpenedPayload {
        override val deletionSecret get() = message.deletionSecret
    }

    class Receipt(override val from: TrustedContact, val receipt: DeliveryReceiptV1) : OpenedPayload {
        override val deletionSecret get() = receipt.deletionSecret
    }
}

/** Why a bundle was dropped. Never includes plaintext. */
class OpenFailure(val reason: String) : Exception(reason)

/**
 * End-to-end protection of private bundles (spec §15–§19, §26, §29). This is the
 * only class that ever sees private-message plaintext.
 */
class PrivateMessageCrypto(private val config: ProtocolConfig = ProtocolConfig.DEFAULT) {

    private enum class InnerKind(val wire: Int) { MESSAGE(1), RECEIPT(2) }

    fun createMessage(
        sender: LocalIdentity,
        recipient: TrustedContact,
        text: String,
        language: String,
        priority: Priority,
        nowWallMs: Long,
    ): CreatedBundle {
        val textBytes = text.toByteArray(Charsets.UTF_8).size
        require(textBytes in 1..config.privateTextMaxBytes) { "text must be 1..${config.privateTextMaxBytes} UTF-8 bytes" }
        require(language.toByteArray().size in 1..PrivateMessageV1.MAX_LANGUAGE_BYTES) { "invalid language code" }
        val bundleId = Primitives.randomBytes(ID_BYTES)
        val innerMessageId = Primitives.randomBytes(ID_BYTES)
        val deletionSecret = Primitives.randomBytes(SECRET_BYTES)
        val lifetime = config.lifetimeMs(priority)
        val message = PrivateMessageV1(
            innerMessageId = innerMessageId,
            senderNodeId = sender.nodeId,
            recipientNodeId = recipient.nodeId,
            senderSigningPublicKey = sender.signingPublicKey,
            language = language,
            text = text,
            createdTimeHintMs = nowWallMs,
            deletionSecret = deletionSecret,
            bundleId = bundleId,
            lifetimeMs = lifetime,
            priority = priority,
        )
        return CreatedBundle(seal(sender, recipient, message), innerMessageId, deletionSecret, recipient.nodeId)
    }

    /** Receipts are addressed to the original sender and never trigger receipts (audit B). */
    fun createReceipt(
        recipientIdentity: LocalIdentity,
        originalSender: TrustedContact,
        delivered: PrivateMessageV1,
        nowWallMs: Long,
    ): CreatedBundle {
        val deletionSecret = Primitives.randomBytes(SECRET_BYTES)
        val receipt = DeliveryReceiptV1(
            ackOfBundleId = delivered.bundleId,
            ackOfInnerMessageId = delivered.innerMessageId,
            recipientNodeId = recipientIdentity.nodeId,
            originalSenderNodeId = originalSender.nodeId,
            receiptTimeHintMs = nowWallMs,
            deletionSecret = deletionSecret,
            bundleId = Primitives.randomBytes(ID_BYTES),
            lifetimeMs = config.lifetimeMs(delivered.priority),
            priority = delivered.priority,
        )
        return CreatedBundle(seal(recipientIdentity, originalSender, receipt), null, deletionSecret, originalSender.nodeId)
    }

    /** Signs and seals any inner object. Internal so tests can build adversarial inputs. */
    internal fun seal(signer: LocalIdentity, addressee: TrustedContact, inner: SignedInner): PrivateBundleV1 {
        val (kind, domain) = when (inner) {
            is PrivateMessageV1 -> InnerKind.MESSAGE to Domains.PRIVATE_MESSAGE
            is DeliveryReceiptV1 -> InnerKind.RECEIPT to Domains.RECEIPT
        }
        val signedBytes = inner.encode()
        val signature = signer.sign(domain, signedBytes)
        val plaintext = padToBucket(kind, signedBytes, signature)
        val ciphertext = Primitives.sealTo(addressee.encryptionPublicKey, plaintext, Domains.SEALED_MESSAGE)
        val pairSecret = PairSecretDeriver.pairSecret(signer, addressee.encryptionPublicKey)
        return PrivateBundleV1(
            bundleId = inner.bundleId,
            destinationTag = DestinationTag.compute(pairSecret, addressee.nodeId, inner.bundleId),
            lifetimeMs = inner.lifetimeMs,
            priority = inner.priority,
            deletionCommitment = PrivateBundleV1.deletionCommitment(inner.deletionSecret),
            ciphertextHash = Primitives.sha256(ciphertext),
            ciphertext = ciphertext,
        )
    }

    /** `{1: kind, 2: signed_bytes, 3: signature, 4: padding}` padded to an exact bucket size (audit E). */
    private fun padToBucket(kind: InnerKind, signedBytes: ByteArray, signature: ByteArray): ByteArray {
        fun encode(padding: Int) = CborCodec.encode(
            cborMap {
                put(1, kind.wire)
                put(2, signedBytes)
                put(3, signature)
                put(4, ByteArray(padding))
            },
        )
        val unpadded = encode(0).size
        for (bucket in config.paddingBucketsBytes) {
            if (bucket < unpadded) continue
            // Growing the pad from 0 also grows its CBOR length head; solve for the exact size.
            for (headBytes in 1..3) {
                val padding = bucket - unpadded - (headBytes - 1)
                if (padding >= 0 && headLength(padding) == headBytes) return encode(padding).also { check(it.size == bucket) }
            }
        }
        throw IllegalArgumentException("payload of $unpadded B exceeds the largest padding bucket")
    }

    private fun headLength(length: Int) = when {
        length < 24 -> 1
        length <= 0xFF -> 2
        else -> 3
    }

    /** Cheap relay-safe check: which trusted contact, if any, addressed this bundle to me. */
    fun matchDestination(own: LocalIdentity, contacts: List<TrustedContact>, bundle: PrivateBundleV1): TrustedContact? =
        matchDestinationTag(own, contacts, bundle.bundleId, bundle.destinationTag)

    fun matchDestinationTag(
        own: LocalIdentity,
        contacts: List<TrustedContact>,
        bundleId: ByteArray,
        destinationTag: ByteArray,
    ): TrustedContact? = contacts.firstOrNull { contact ->
        val pairSecret = try {
            PairSecretDeriver.pairSecret(own, contact.encryptionPublicKey)
        } catch (_: CryptoFailure) {
            return@firstOrNull false
        }
        Primitives.constantTimeEquals(DestinationTag.compute(pairSecret, own.nodeId, bundleId), destinationTag)
    }

    /**
     * Recipient processing (spec §26 with audit #1 and #3 corrections):
     * decrypt → strict parse → signer must be the tag-matched contact → verify with
     * that contact's STORED key → addressee must be me → authenticated copies must
     * equal the outer envelope. Throws [OpenFailure] on any failure.
     */
    fun open(own: LocalIdentity, matchedContact: TrustedContact, bundle: PrivateBundleV1): OpenedPayload {
        val plaintext = try {
            own.openSealed(bundle.ciphertext, Domains.SEALED_MESSAGE)
        } catch (_: CryptoFailure) {
            throw OpenFailure("decryption failed")
        }
        try {
            val map = CborCodec.decode(
                plaintext,
                CborLimits(maxInputBytes = config.paddingBucketsBytes.max(), maxDepth = 1, maxContainerItems = 4),
            ).asMap()
            map.requireOnlyKeys(1, 2, 3, 4)
            val kind = map.int(1, 1..2)
            val signedBytes = map.bytesUpTo(2, config.privateTextMaxBytes + 512, minSize = 1)
            val signature = map.bytes(3, Primitives.SIGNATURE_BYTES)
            map.bytesUpTo(4, config.paddingBucketsBytes.max())
            val inner: SignedInner
            val domain: ByteArray
            if (kind == InnerKind.MESSAGE.wire) {
                inner = PrivateMessageV1.decode(signedBytes, config)
                domain = Domains.PRIVATE_MESSAGE
                // The message-supplied key is informational only; it must equal the stored key.
                if (!inner.senderSigningPublicKey.contentEquals(matchedContact.signingPublicKey)) {
                    throw OpenFailure("sender key does not match the trusted contact")
                }
            } else {
                inner = DeliveryReceiptV1.decode(signedBytes)
                domain = Domains.RECEIPT
            }
            if (!inner.signerNodeId.contentEquals(matchedContact.nodeId)) {
                throw OpenFailure("signer is not the contact whose pair secret matched")
            }
            if (!Primitives.ed25519Verify(matchedContact.signingPublicKey, domain, signedBytes, signature)) {
                throw OpenFailure("signature invalid")
            }
            if (!inner.addresseeNodeId.contentEquals(own.nodeId)) throw OpenFailure("not addressed to this node")
            if (!inner.bundleId.contentEquals(bundle.bundleId) ||
                inner.lifetimeMs != bundle.lifetimeMs ||
                inner.priority != bundle.priority ||
                !Primitives.constantTimeEquals(PrivateBundleV1.deletionCommitment(inner.deletionSecret), bundle.deletionCommitment)
            ) {
                throw OpenFailure("outer envelope does not match authenticated copies")
            }
            return when (inner) {
                is PrivateMessageV1 -> OpenedPayload.Message(matchedContact, inner)
                is DeliveryReceiptV1 -> OpenedPayload.Receipt(matchedContact, inner)
            }
        } catch (e: MalformedInputException) {
            throw OpenFailure("malformed inner payload: ${e.message}")
        }
    }
}
