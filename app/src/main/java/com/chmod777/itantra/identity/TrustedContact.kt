package com.chmod777.itantra.identity

/** A QR-verified contact (spec §12). `localName` is editable and never identity. */
class TrustedContact(
    val nodeId: ByteArray,
    val localName: String,
    val signingPublicKey: ByteArray,
    val encryptionPublicKey: ByteArray,
    val fingerprint: String,
    val keyEpoch: Long,
    val trustMethod: TrustMethod,
    val createdAtWallMs: Long,
) {
    override fun equals(other: Any?): Boolean = other is TrustedContact && nodeId.contentEquals(other.nodeId)
    override fun hashCode(): Int = nodeId.contentHashCode()

    companion object {
        fun fromVerifiedCapsule(capsule: IdentityCapsuleV1, localName: String, nowWallMs: Long) = TrustedContact(
            nodeId = capsule.nodeId,
            localName = localName.trim().ifEmpty { capsule.displayNameHint.ifEmpty { "Contact" } },
            signingPublicKey = capsule.signingPublicKey,
            encryptionPublicKey = capsule.encryptionPublicKey,
            fingerprint = capsule.fingerprint,
            keyEpoch = capsule.keyEpoch,
            trustMethod = TrustMethod.QR_VERIFIED,
            createdAtWallMs = nowWallMs,
        )
    }
}

enum class TrustMethod { QR_VERIFIED }

/**
 * Trusted-contact storage (spec §12, §13). Resolution is by node_id only; a
 * display name, device name, MAC or BLE peer ID never selects a recipient.
 */
interface TrustedContactStore {
    fun all(): List<TrustedContact>
    fun byNodeId(nodeId: ByteArray): TrustedContact?

    /** Inserts or replaces keys for the same node_id; returns false when refused. */
    fun upsert(contact: TrustedContact): Boolean
    fun rename(nodeId: ByteArray, localName: String): Boolean
    fun remove(nodeId: ByteArray): Boolean
}
