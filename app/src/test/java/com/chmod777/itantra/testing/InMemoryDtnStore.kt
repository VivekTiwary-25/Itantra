package com.chmod777.itantra.testing

import com.chmod777.itantra.crypto.toHex
import com.chmod777.itantra.dtn.DeliveredMessage
import com.chmod777.itantra.dtn.DtnClock
import com.chmod777.itantra.dtn.DtnStore
import com.chmod777.itantra.dtn.OutgoingMessage
import com.chmod777.itantra.dtn.ReceiptRecord
import com.chmod777.itantra.dtn.SeenRecord
import com.chmod777.itantra.dtn.StoredBundle
import com.chmod777.itantra.dtn.TombstoneRecord

/** TEST DOUBLE: in-memory [DtnStore]. Reusing one instance across DtnNode instances simulates a process restart. */
class InMemoryDtnStore : DtnStore {
    private val bundles = LinkedHashMap<String, StoredBundle>()
    private val seen = LinkedHashMap<String, SeenRecord>()
    private val tombstones = LinkedHashMap<String, TombstoneRecord>()
    private val outgoing = LinkedHashMap<String, OutgoingMessage>()
    private val delivered = LinkedHashMap<String, DeliveredMessage>()
    private val receipts = LinkedHashMap<String, ReceiptRecord>()

    @Synchronized override fun bundle(storageKey: String) = bundles[storageKey]
    @Synchronized override fun bundlesById(bundleId: ByteArray) = bundles.values.filter { it.bundle.bundleId.contentEquals(bundleId) }
    @Synchronized override fun allBundles() = bundles.values.toList()
    @Synchronized override fun putBundle(bundle: StoredBundle) { bundles[bundle.storageKey] = bundle }
    @Synchronized override fun deleteBundle(storageKey: String) { bundles.remove(storageKey) }
    @Synchronized override fun totalBundleBytes() = bundles.values.sumOf { it.sizeBytes.toLong() }
    @Synchronized override fun bundleBytesFromPeerSession(peerSessionKey: String) =
        bundles.values.filter { it.sourcePeerSessionKey == peerSessionKey }.sumOf { it.sizeBytes.toLong() }

    @Synchronized override fun seen(storageKey: String) = seen[storageKey]
    @Synchronized override fun seenById(bundleId: ByteArray) = seen.values.filter { it.bundleId.contentEquals(bundleId) }
    @Synchronized override fun putSeen(record: SeenRecord) { seen[record.storageKey] = record }
    @Synchronized override fun purgeSeen(nowWallMs: Long): Int {
        val old = seen.filterValues { it.retainUntilWallMs < nowWallMs }.keys
        old.forEach { seen.remove(it) }
        return old.size
    }

    @Synchronized override fun tombstone(bundleId: ByteArray) = tombstones[bundleId.toHex()]
    @Synchronized override fun putTombstone(record: TombstoneRecord) { tombstones[record.bundleId.toHex()] = record }
    @Synchronized override fun purgeTombstones(nowWallMs: Long): Int {
        val old = tombstones.filterValues { it.expiresAtWallMs < nowWallMs }.keys
        old.forEach { tombstones.remove(it) }
        return old.size
    }

    @Synchronized override fun outgoing(bundleId: ByteArray) = outgoing[bundleId.toHex()]
    @Synchronized override fun allOutgoing() = outgoing.values.toList()
    @Synchronized override fun putOutgoing(message: OutgoingMessage) { outgoing[message.bundleId.toHex()] = message }

    @Synchronized override fun delivered(innerMessageId: ByteArray) = delivered[innerMessageId.toHex()]
    @Synchronized override fun allDelivered() = delivered.values.toList()
    @Synchronized override fun putDelivered(message: DeliveredMessage) { delivered[message.innerMessageId.toHex()] = message }

    @Synchronized override fun putReceipt(record: ReceiptRecord) { receipts[record.ackOfBundleId.toHex()] = record }
    @Synchronized override fun receipt(ackOfBundleId: ByteArray) = receipts[ackOfBundleId.toHex()]

    @Synchronized override fun <T> transaction(block: () -> T): T = block()
}

/** TEST DOUBLE: manually advanced clock with a controllable boot counter. */
class FakeClock(var elapsed: Long = 1_000, var boot: Int = 1, var wall: Long = 1_700_000_000_000) : DtnClock {
    override fun elapsedMs() = elapsed
    override fun bootCount() = boot
    override fun wallMs() = wall
    override fun monotonicNs() = System.nanoTime()

    fun advance(ms: Long) {
        elapsed += ms
        wall += ms
    }

    /** Simulates a reboot: elapsed resets, boot count increments, wall keeps going. */
    fun reboot(offForMs: Long) {
        elapsed = 5_000
        boot++
        wall += offForMs
    }
}
