package com.chmod777.itantra.identity

import com.chmod777.itantra.crypto.Domains
import com.chmod777.itantra.crypto.NodeIds
import com.chmod777.itantra.crypto.Primitives
import com.chmod777.itantra.protocol.cbor.CborCodec
import com.chmod777.itantra.protocol.cbor.CborLimits
import com.chmod777.itantra.protocol.cbor.MalformedInputException
import com.chmod777.itantra.protocol.cbor.asMap
import com.chmod777.itantra.protocol.cbor.bytes
import com.chmod777.itantra.protocol.cbor.bytesUpTo
import com.chmod777.itantra.protocol.cbor.cborMap
import com.chmod777.itantra.protocol.cbor.requireOnlyKeys
import com.chmod777.itantra.protocol.cbor.text
import com.chmod777.itantra.protocol.cbor.uint

/** Shareable identity object (spec §11). Field keys are the canonical CBOR map keys. */
data class IdentityCapsuleV1(
    val signingPublicKey: ByteArray,
    val encryptionPublicKey: ByteArray,
    val displayNameHint: String,
    val keyEpoch: Long,
) {
    init {
        require(signingPublicKey.size == Primitives.KEY_BYTES)
        require(encryptionPublicKey.size == Primitives.KEY_BYTES)
        require(displayNameHint.toByteArray().size <= MAX_DISPLAY_NAME_BYTES)
    }

    val nodeId: ByteArray get() = NodeIds.nodeId(signingPublicKey, encryptionPublicKey)
    val fingerprint: String get() = NodeIds.fingerprint(signingPublicKey, encryptionPublicKey)

    fun encode(): ByteArray = CborCodec.encode(
        cborMap {
            put(1, VERSION)
            put(2, signingPublicKey)
            put(3, encryptionPublicKey)
            put(4, displayNameHint)
            put(5, keyEpoch)
        },
    )

    override fun equals(other: Any?): Boolean = other is IdentityCapsuleV1 &&
        signingPublicKey.contentEquals(other.signingPublicKey) &&
        encryptionPublicKey.contentEquals(other.encryptionPublicKey) &&
        displayNameHint == other.displayNameHint && keyEpoch == other.keyEpoch

    override fun hashCode(): Int = signingPublicKey.contentHashCode() * 31 + encryptionPublicKey.contentHashCode()

    companion object {
        const val VERSION = 1
        const val MAX_DISPLAY_NAME_BYTES = 64
        private val LIMITS = CborLimits(maxInputBytes = 256, maxDepth = 1, maxContainerItems = 5)

        @Throws(MalformedInputException::class)
        fun decode(bytes: ByteArray): IdentityCapsuleV1 {
            val map = CborCodec.decode(bytes, LIMITS).asMap()
            map.requireOnlyKeys(1, 2, 3, 4, 5)
            map.uint(1, VERSION.toLong()..VERSION.toLong())
            return IdentityCapsuleV1(
                signingPublicKey = map.bytes(2, Primitives.KEY_BYTES),
                encryptionPublicKey = map.bytes(3, Primitives.KEY_BYTES),
                displayNameHint = map.text(4, MAX_DISPLAY_NAME_BYTES),
                keyEpoch = map.uint(5, 0..0xFFFF_FFFFL),
            )
        }
    }
}

/**
 * `{1: capsule_bytes, 2: signature}`, where the signature covers
 * `"itantra-v1/capsule" || capsule_bytes` (audit #5).
 */
class SignedCapsule(val capsuleBytes: ByteArray, val signature: ByteArray) {
    fun encode(): ByteArray = CborCodec.encode(
        cborMap {
            put(1, capsuleBytes)
            put(2, signature)
        },
    )

    /** Verifies the self-signature and returns the capsule, or throws. */
    @Throws(MalformedInputException::class)
    fun verify(): IdentityCapsuleV1 {
        val capsule = IdentityCapsuleV1.decode(capsuleBytes)
        if (!Primitives.ed25519Verify(capsule.signingPublicKey, Domains.CAPSULE, capsuleBytes, signature)) {
            throw MalformedInputException("capsule self-signature is invalid")
        }
        return capsule
    }

    companion object {
        const val MAX_ENCODED_BYTES = 400
        private val LIMITS = CborLimits(maxInputBytes = MAX_ENCODED_BYTES, maxDepth = 1, maxContainerItems = 2)

        @Throws(MalformedInputException::class)
        fun decode(bytes: ByteArray): SignedCapsule {
            val map = CborCodec.decode(bytes, LIMITS).asMap()
            map.requireOnlyKeys(1, 2)
            return SignedCapsule(map.bytesUpTo(1, 256), map.bytes(2, Primitives.SIGNATURE_BYTES))
        }
    }
}

/** QR / paste transport for a signed capsule (IMPLEMENTATION_NOTES §5). */
object ContactQrCodec {
    const val PREFIX = "ITANTRA1:"

    fun encode(signed: SignedCapsule): String =
        PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(signed.encode())

    /** Parses, bounds-checks and verifies. Throws [MalformedInputException] on anything invalid. */
    @Throws(MalformedInputException::class)
    fun decodeAndVerify(text: String): IdentityCapsuleV1 {
        val trimmed = text.trim()
        if (!trimmed.startsWith(PREFIX)) throw MalformedInputException("not an iTantra contact code")
        val body = trimmed.removePrefix(PREFIX)
        if (body.length > SignedCapsule.MAX_ENCODED_BYTES * 2) throw MalformedInputException("contact code too long")
        val bytes = try {
            java.util.Base64.getUrlDecoder().decode(body)
        } catch (_: IllegalArgumentException) {
            throw MalformedInputException("contact code is not base64url")
        }
        return SignedCapsule.decode(bytes).verify()
    }
}
