package com.chmod777.itantra.crypto

import com.chmod777.itantra.identity.ContactQrCodec
import com.chmod777.itantra.identity.LocalIdentity
import com.chmod777.itantra.identity.SignedCapsule
import com.chmod777.itantra.identity.TrustedContact
import com.chmod777.itantra.protocol.Priority
import com.chmod777.itantra.protocol.PrivateBundleV1
import com.chmod777.itantra.protocol.PrivateMessageV1
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.protocol.TombstoneV1
import com.chmod777.itantra.protocol.cbor.MalformedInputException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateMessageCryptoTest {
    private val config = ProtocolConfig.DEFAULT
    private val crypto = PrivateMessageCrypto(config)
    private val vivek = LocalIdentity.generate("Vivek")
    private val rahul = LocalIdentity.generate("Rahul")
    private val mallory = LocalIdentity.generate("Mallory")

    private fun trust(identity: LocalIdentity, name: String = identity.displayNameHint): TrustedContact =
        TrustedContact.fromVerifiedCapsule(ContactQrCodec.decodeAndVerify(ContactQrCodec.encode(identity.signedCapsule())), name, 0)

    @Test
    fun capsuleQrRoundTripVerifiesSelfSignature() {
        val capsule = ContactQrCodec.decodeAndVerify(ContactQrCodec.encode(rahul.signedCapsule()))
        assertArrayEquals(rahul.nodeId, capsule.nodeId)
        assertEquals(rahul.fingerprint, capsule.fingerprint)
    }

    @Test
    fun capsuleWithForeignOrUndomainedSignatureIsRejected() {
        val bytes = rahul.capsule().encode()
        val signedByMallory = SignedCapsule(bytes, mallory.sign(Domains.CAPSULE, bytes))
        assertThrows(MalformedInputException::class.java) { ContactQrCodec.decodeAndVerify(ContactQrCodec.encode(signedByMallory)) }
        // A valid signature under a different domain must not verify as a capsule (audit #5).
        val wrongDomain = SignedCapsule(bytes, rahul.sign(Domains.RECEIPT, bytes))
        assertThrows(MalformedInputException::class.java) { ContactQrCodec.decodeAndVerify(ContactQrCodec.encode(wrongDomain)) }
        assertThrows(MalformedInputException::class.java) { ContactQrCodec.decodeAndVerify("ITANTRA1:!!!") }
        assertThrows(MalformedInputException::class.java) { ContactQrCodec.decodeAndVerify("hello") }
    }

    @Test
    fun trustedRecipientDecryptsAndAuthenticates() {
        val created = crypto.createMessage(vivek, trust(rahul), "I am safe. Meet me at the shelter.", "en", Priority.NORMAL, 1_000)
        val rahulContacts = listOf(trust(mallory), trust(vivek))
        val match = crypto.matchDestination(rahul, rahulContacts, created.bundle)
        assertNotNull(match)
        val opened = crypto.open(rahul, match!!, created.bundle) as OpenedPayload.Message
        assertEquals("I am safe. Meet me at the shelter.", opened.message.text)
        assertArrayEquals(vivek.nodeId, opened.from.nodeId)
        assertArrayEquals(created.innerMessageId, opened.message.innerMessageId)
        // The relay-visible envelope never contains the plaintext.
        assertFalse(String(created.bundle.encodeImmutable(), Charsets.ISO_8859_1).contains("shelter"))
    }

    @Test
    fun destinationTagIsDirectionBound() {
        val created = crypto.createMessage(vivek, trust(rahul), "hi", "en", Priority.NORMAL, 0)
        // The sender must not recognise its own outgoing bundle as addressed to itself (audit #2).
        assertNull(crypto.matchDestination(vivek, listOf(trust(rahul)), created.bundle))
        // An unrelated node recognises nothing.
        assertNull(crypto.matchDestination(mallory, listOf(trust(vivek), trust(rahul)), created.bundle))
    }

    @Test
    fun relayAndThirdPartiesCannotDecrypt() {
        val created = crypto.createMessage(vivek, trust(rahul), "secret", "en", Priority.NORMAL, 0)
        assertThrows(OpenFailure::class.java) { crypto.open(mallory, trust(vivek), created.bundle) }
    }

    @Test
    fun anotherTrustedContactCannotImpersonateTheSender() {
        // Mallory is trusted by Rahul, as is Vivek. Mallory forges sender_node_id = Vivek,
        // includes Vivek's public signing key, but can only sign with Mallory's key (audit #1).
        val forgedAsVivek = PrivateMessageV1(
            innerMessageId = Primitives.randomBytes(16),
            senderNodeId = vivek.nodeId,
            recipientNodeId = rahul.nodeId,
            senderSigningPublicKey = vivek.signingPublicKey,
            language = "en",
            text = "pretend to be Vivek",
            createdTimeHintMs = 0,
            deletionSecret = Primitives.randomBytes(32),
            bundleId = Primitives.randomBytes(16),
            lifetimeMs = config.normalPrivateLifetimeMs,
            priority = Priority.NORMAL,
        )
        val bundle = crypto.seal(mallory, trust(rahul), forgedAsVivek)
        val rahulContacts = listOf(trust(vivek), trust(mallory))
        // The tag can only have been produced with Mallory's pair secret.
        val matched = crypto.matchDestination(rahul, rahulContacts, bundle)!!
        assertArrayEquals(mallory.nodeId, matched.nodeId)
        assertThrows(OpenFailure::class.java) { crypto.open(rahul, matched, bundle) }
        // Even if an implementation tried Vivek's stored entry, the signature cannot verify.
        assertThrows(OpenFailure::class.java) { crypto.open(rahul, trust(vivek), bundle) }
    }

    @Test
    fun wrongAddresseeIsRejected() {
        // Vivek seals to Rahul's key but names Mallory as recipient_node_id.
        val misaddressed = PrivateMessageV1(
            Primitives.randomBytes(16), vivek.nodeId, mallory.nodeId, vivek.signingPublicKey, "en", "x", 0,
            Primitives.randomBytes(32), Primitives.randomBytes(16), config.normalPrivateLifetimeMs, Priority.NORMAL,
        )
        val bundle = crypto.seal(vivek, trust(rahul), misaddressed)
        assertThrows(OpenFailure::class.java) { crypto.open(rahul, trust(vivek), bundle) }
    }

    @Test
    fun tamperedEnvelopeFieldsAreRejected() {
        val created = crypto.createMessage(vivek, trust(rahul), "hello", "en", Priority.NORMAL, 0).bundle
        val vivekContact = trust(vivek)
        fun copy(
            lifetime: Long = created.lifetimeMs,
            priority: Priority = created.priority,
            commitment: ByteArray = created.deletionCommitment,
            bundleId: ByteArray = created.bundleId,
            ciphertext: ByteArray = created.ciphertext,
        ) = PrivateBundleV1(bundleId, created.destinationTag, lifetime, priority, commitment, Primitives.sha256(ciphertext), ciphertext)

        assertThrows(OpenFailure::class.java) { crypto.open(rahul, vivekContact, copy(lifetime = created.lifetimeMs + 1)) }
        assertThrows(OpenFailure::class.java) { crypto.open(rahul, vivekContact, copy(priority = Priority.URGENT)) }
        assertThrows(OpenFailure::class.java) { crypto.open(rahul, vivekContact, copy(commitment = ByteArray(32))) }
        assertThrows(OpenFailure::class.java) { crypto.open(rahul, vivekContact, copy(bundleId = ByteArray(16))) }
        val corrupted = created.ciphertext.copyOf().also { it[40] = (it[40].toInt() xor 1).toByte() }
        assertThrows(OpenFailure::class.java) { crypto.open(rahul, vivekContact, copy(ciphertext = corrupted)) }
    }

    @Test
    fun outerEnvelopeValidationRejectsHashMismatch() {
        val created = crypto.createMessage(vivek, trust(rahul), "hello", "en", Priority.NORMAL, 0).bundle
        val forged = PrivateBundleV1(
            created.bundleId, created.destinationTag, created.lifetimeMs, created.priority,
            created.deletionCommitment, ByteArray(32), created.ciphertext,
        )
        assertThrows(MalformedInputException::class.java) { PrivateBundleV1.decodeImmutable(forged.encodeImmutable(), config) }
        val parsed = PrivateBundleV1.decodeImmutable(created.encodeImmutable(), config)
        assertArrayEquals(created.ciphertextHash, parsed.ciphertextHash)
    }

    @Test
    fun ciphertextSizeIsBucketed() {
        // HPKE adds enc (32 B) + Poly1305 tag (16 B) to the padded plaintext.
        val allowedSizes = config.paddingBucketsBytes.map { it + 48 }.toSet()
        val sizes = listOf(1, 2, 20, 60, 700, 3_000, 9_000).map { length ->
            crypto.createMessage(vivek, trust(rahul), "a".repeat(length), "en", Priority.NORMAL, 0).bundle.ciphertext.size
        }
        assertTrue(sizes.toString(), sizes.all { it in allowedSizes })
        // Messages of different lengths inside one bucket are indistinguishable by size.
        assertEquals(sizes[0], sizes[1])
        assertEquals(sizes[3], sizes[4])
        val max = crypto.createMessage(vivek, trust(rahul), "a".repeat(config.privateTextMaxBytes), "en", Priority.NORMAL, 0)
        assertTrue(max.bundle.ciphertext.size <= config.maxCiphertextBytes)
        assertThrows(IllegalArgumentException::class.java) {
            crypto.createMessage(vivek, trust(rahul), "a".repeat(config.privateTextMaxBytes + 1), "en", Priority.NORMAL, 0)
        }
    }

    @Test
    fun receiptRoundTripsToOriginalSenderAndIsDistinguishable() {
        val created = crypto.createMessage(vivek, trust(rahul), "hello", "hi", Priority.URGENT, 0)
        val opened = crypto.open(rahul, trust(vivek), created.bundle) as OpenedPayload.Message
        val receipt = crypto.createReceipt(rahul, trust(vivek), opened.message, 5)
        val match = crypto.matchDestination(vivek, listOf(trust(rahul)), receipt.bundle)!!
        val openedReceipt = crypto.open(vivek, match, receipt.bundle) as OpenedPayload.Receipt
        assertArrayEquals(created.bundle.bundleId, openedReceipt.receipt.ackOfBundleId)
        assertArrayEquals(created.innerMessageId, openedReceipt.receipt.ackOfInnerMessageId)
        // Rahul does not recognise his own receipt as addressed to himself.
        assertNull(crypto.matchDestination(rahul, listOf(trust(vivek)), receipt.bundle))
    }

    @Test
    fun tombstoneVerifiesOnlyWithTheRealDeletionSecret() {
        val created = crypto.createMessage(vivek, trust(rahul), "hello", "en", Priority.NORMAL, 0)
        val real = TombstoneV1(created.bundle.bundleId, created.deletionSecret, 1_000)
        assertTrue(real.matches(created.bundle.deletionCommitment))
        val forged = TombstoneV1(created.bundle.bundleId, Primitives.randomBytes(32), 1_000)
        assertFalse(forged.matches(created.bundle.deletionCommitment))
        val decoded = TombstoneV1.decode(real.encode(), config)
        assertTrue(decoded.matches(created.bundle.deletionCommitment))
    }

    @Test
    fun x25519RejectsLowOrderPeerKey() {
        assertThrows(CryptoFailure::class.java) { Primitives.x25519Agree(Primitives.x25519GeneratePrivate(), ByteArray(32)) }
    }
}
