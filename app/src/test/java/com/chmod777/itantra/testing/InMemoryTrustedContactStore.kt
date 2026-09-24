package com.chmod777.itantra.testing

import com.chmod777.itantra.identity.TrustedContact
import com.chmod777.itantra.identity.TrustedContactStore

/** TEST DOUBLE: in-memory contact store for JVM tests only. */
class InMemoryTrustedContactStore : TrustedContactStore {
    private val contacts = LinkedHashMap<String, TrustedContact>()

    private fun key(nodeId: ByteArray) = nodeId.joinToString("") { "%02x".format(it) }

    @Synchronized override fun all(): List<TrustedContact> = contacts.values.toList()
    @Synchronized override fun byNodeId(nodeId: ByteArray): TrustedContact? = contacts[key(nodeId)]

    @Synchronized override fun upsert(contact: TrustedContact): Boolean {
        contacts[key(contact.nodeId)] = contact
        return true
    }

    @Synchronized override fun rename(nodeId: ByteArray, localName: String): Boolean {
        val existing = contacts[key(nodeId)] ?: return false
        contacts[key(nodeId)] = TrustedContact(
            existing.nodeId, localName, existing.signingPublicKey, existing.encryptionPublicKey,
            existing.fingerprint, existing.keyEpoch, existing.trustMethod, existing.createdAtWallMs,
        )
        return true
    }

    @Synchronized override fun remove(nodeId: ByteArray): Boolean = contacts.remove(key(nodeId)) != null
}
