package com.chmod777.itantra.identity

import com.chmod777.itantra.crypto.Domains
import com.chmod777.itantra.crypto.NodeIds
import com.chmod777.itantra.crypto.Primitives

/**
 * This installation's long-term identity (spec §10). Private material exists only
 * in memory here; persistence is the job of the Keystore-wrapped `KeyManager`.
 */
class LocalIdentity(
    private val signingSeed: ByteArray,
    private val encryptionPrivateKey: ByteArray,
    val keyEpoch: Long,
    val displayNameHint: String,
) {
    init {
        require(signingSeed.size == Primitives.KEY_BYTES && encryptionPrivateKey.size == Primitives.KEY_BYTES)
    }

    val signingPublicKey: ByteArray = Primitives.ed25519PublicKey(signingSeed)
    val encryptionPublicKey: ByteArray = Primitives.x25519PublicKey(encryptionPrivateKey)
    val nodeId: ByteArray = NodeIds.nodeId(signingPublicKey, encryptionPublicKey)
    val fingerprint: String = NodeIds.fingerprint(signingPublicKey, encryptionPublicKey)

    fun capsule(): IdentityCapsuleV1 =
        IdentityCapsuleV1(signingPublicKey, encryptionPublicKey, displayNameHint, keyEpoch)

    fun signedCapsule(): SignedCapsule {
        val bytes = capsule().encode()
        return SignedCapsule(bytes, sign(Domains.CAPSULE, bytes))
    }

    fun sign(domain: ByteArray, message: ByteArray): ByteArray =
        Primitives.ed25519Sign(signingSeed, domain, message)

    fun agree(peerEncryptionPublicKey: ByteArray): ByteArray =
        Primitives.x25519Agree(encryptionPrivateKey, peerEncryptionPublicKey)

    fun openSealed(ciphertext: ByteArray, info: ByteArray): ByteArray =
        Primitives.openSealed(encryptionPrivateKey, ciphertext, info)

    /** Only for the Keystore wrapper; never logged. */
    internal fun exportPrivateMaterial(): Pair<ByteArray, ByteArray> =
        signingSeed.copyOf() to encryptionPrivateKey.copyOf()

    companion object {
        fun generate(displayNameHint: String, keyEpoch: Long = 1): LocalIdentity = LocalIdentity(
            signingSeed = Primitives.randomBytes(Primitives.KEY_BYTES),
            encryptionPrivateKey = Primitives.x25519GeneratePrivate(),
            keyEpoch = keyEpoch,
            displayNameHint = displayNameHint,
        )
    }
}
