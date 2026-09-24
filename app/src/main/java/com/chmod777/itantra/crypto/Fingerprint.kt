package com.chmod777.itantra.crypto

/** Key-derived identifiers (spec §11, IMPLEMENTATION_NOTES §5). Display names are never identity. */
object NodeIds {
    const val NODE_ID_BYTES = 16

    /** node_id = Trunc128(SHA256("itantra-v1/node-id" || spk || epk)). */
    fun nodeId(signingPublicKey: ByteArray, encryptionPublicKey: ByteArray): ByteArray =
        Primitives.sha256(Domains.NODE_ID, signingPublicKey, encryptionPublicKey).copyOf(NODE_ID_BYTES)

    /** Human-comparable fingerprint: 8 groups of 4 hex digits from a separate domain. */
    fun fingerprint(signingPublicKey: ByteArray, encryptionPublicKey: ByteArray): String =
        Primitives.sha256(Domains.FINGERPRINT, signingPublicKey, encryptionPublicKey)
            .copyOf(16)
            .toHex()
            .chunked(4)
            .joinToString(" ")
}
