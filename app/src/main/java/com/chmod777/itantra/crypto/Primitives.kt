package com.chmod777.itantra.crypto

import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.hybrid.HpkePrivateKey
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.internal.HpkeDecrypt
import com.google.crypto.tink.hybrid.internal.HpkeEncrypt
import com.google.crypto.tink.subtle.Ed25519Sign
import com.google.crypto.tink.subtle.Ed25519Verify
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import com.google.crypto.tink.util.Bytes
import com.google.crypto.tink.util.SecretBytes
import com.google.crypto.tink.InsecureSecretKeyAccess
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Domain-separation prefixes (spec §11, §15, §17, §19, audit #2/#5). */
object Domains {
    val NODE_ID = ascii("itantra-v1/node-id")
    val FINGERPRINT = ascii("itantra-v1/fingerprint")
    val CAPSULE = ascii("itantra-v1/capsule")
    val PAIR_SECRET = ascii("itantra-v1/pair-secret")
    val DESTINATION_TAG = ascii("itantra-v1/destination-tag")
    val PRIVATE_MESSAGE = ascii("itantra-v1/private-message")
    val RECEIPT = ascii("itantra-v1/receipt")
    val DELETE = ascii("itantra-v1/delete")
    val SEALED_MESSAGE = ascii("itantra-v1/sealed-message")
    val NOISE_PROLOGUE = ascii("itantra-v1/noise-hop")
    val SOS_REQUEST = ascii("itantra-v1/sos-request")
    val SOS_OFFER = ascii("itantra-v1/sos-offer")
    val SOS_CANCEL = ascii("itantra-v1/sos-cancel")
    val SOS_SAS = ascii("itantra-v1/sos-sas")

    private fun ascii(value: String) = value.toByteArray(Charsets.US_ASCII)
}

class CryptoFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thin, typed facade over Tink and the JCA. No primitive is implemented here;
 * this only fixes parameters and turns library exceptions into [CryptoFailure].
 */
object Primitives {
    const val KEY_BYTES = 32
    const val SIGNATURE_BYTES = 64

    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    fun sha256(vararg parts: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            parts.forEach { update(it) }
            digest()
        }

    fun hmacSha256(key: ByteArray, vararg parts: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            parts.forEach { update(it) }
            doFinal()
        }

    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, size: Int): ByteArray = try {
        Hkdf.computeHkdf("HMACSHA256", ikm, salt, info, size)
    } catch (e: GeneralSecurityException) {
        throw CryptoFailure("HKDF failed", e)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    // --- Ed25519 (identity signing) ---

    /** Returns the 32-byte public key for a 32-byte Ed25519 seed. */
    fun ed25519PublicKey(seed: ByteArray): ByteArray = try {
        Ed25519Sign.KeyPair.newKeyPairFromSeed(seed).publicKey
    } catch (e: GeneralSecurityException) {
        throw CryptoFailure("invalid Ed25519 seed", e)
    }

    fun ed25519Sign(seed: ByteArray, domain: ByteArray, message: ByteArray): ByteArray = try {
        Ed25519Sign(seed).sign(domain + message)
    } catch (e: GeneralSecurityException) {
        throw CryptoFailure("Ed25519 signing failed", e)
    }

    fun ed25519Verify(publicKey: ByteArray, domain: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != KEY_BYTES || signature.size != SIGNATURE_BYTES) return false
        return try {
            Ed25519Verify(publicKey).verify(signature, domain + message)
            true
        } catch (_: GeneralSecurityException) {
            false
        }
    }

    // --- X25519 (pairwise secrets) ---

    fun x25519GeneratePrivate(): ByteArray = X25519.generatePrivateKey()

    fun x25519PublicKey(privateKey: ByteArray): ByteArray = try {
        X25519.publicFromPrivate(privateKey)
    } catch (e: GeneralSecurityException) {
        throw CryptoFailure("invalid X25519 private key", e)
    }

    /** Raw X25519. Rejects low-order points and an all-zero result (audit #2). */
    fun x25519Agree(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        if (peerPublicKey.size != KEY_BYTES) throw CryptoFailure("X25519 public key must be 32 B")
        val shared = try {
            X25519.computeSharedSecret(privateKey, peerPublicKey)
        } catch (e: GeneralSecurityException) {
            throw CryptoFailure("X25519 rejected the peer key", e)
        }
        if (shared.all { it == 0.toByte() }) throw CryptoFailure("X25519 produced an all-zero secret")
        return shared
    }

    // --- HPKE sealed box (spec §18, IMPLEMENTATION_NOTES §3.2) ---

    private val hpkeParameters: HpkeParameters by lazy {
        HpkeParameters.builder()
            .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
            .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
            .setAeadId(HpkeParameters.AeadId.CHACHA20_POLY1305)
            .setVariant(HpkeParameters.Variant.NO_PREFIX)
            .build()
    }

    /** Seals to a recipient X25519 public key. Output is `enc(32) || ciphertext`. */
    fun sealTo(recipientX25519Public: ByteArray, plaintext: ByteArray, info: ByteArray): ByteArray = try {
        val publicKey = HpkePublicKey.create(hpkeParameters, Bytes.copyFrom(recipientX25519Public), null)
        HpkeEncrypt.create(publicKey).encrypt(plaintext, info)
    } catch (e: GeneralSecurityException) {
        throw CryptoFailure("HPKE seal failed", e)
    }

    /** Opens a sealed box, or throws [CryptoFailure] on any authentication failure. */
    fun openSealed(recipientX25519Private: ByteArray, ciphertext: ByteArray, info: ByteArray): ByteArray = try {
        val publicKey = HpkePublicKey.create(
            hpkeParameters,
            Bytes.copyFrom(x25519PublicKey(recipientX25519Private)),
            null,
        )
        val privateKey = HpkePrivateKey.create(
            publicKey,
            SecretBytes.copyFrom(recipientX25519Private, InsecureSecretKeyAccess.get()),
        )
        HpkeDecrypt.create(privateKey).decrypt(ciphertext, info)
    } catch (e: GeneralSecurityException) {
        throw CryptoFailure("HPKE open failed", e)
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** Truncated hex for debug metrics only; never a stable identity (spec §54). */
fun ByteArray.shortHex(bytes: Int = 4): String = copyOf(minOf(bytes, size)).toHex()

/** Unsigned lexicographic comparison, used for key ordering and collision rules. */
fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
    for (i in 0 until minOf(a.size, b.size)) {
        val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
        if (diff != 0) return diff
    }
    return a.size - b.size
}
