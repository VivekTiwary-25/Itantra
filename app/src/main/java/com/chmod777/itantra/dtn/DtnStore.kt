package com.chmod777.itantra.dtn

/**
 * Durable DTN state (spec §20, §27, §28, §46): BundleStore + SeenStore +
 * tombstones + sender outbox + recipient inbox + receipts.
 *
 * Implementations must make each call durable before returning; the relay ACK
 * ("I persisted these bytes") is only sent after [putBundle] returns.
 */
interface DtnStore {
    // Bundles
    fun bundle(storageKey: String): StoredBundle?
    fun bundlesById(bundleId: ByteArray): List<StoredBundle>
    fun allBundles(): List<StoredBundle>
    fun putBundle(bundle: StoredBundle)
    fun deleteBundle(storageKey: String)
    fun totalBundleBytes(): Long
    fun bundleBytesFromPeerSession(peerSessionKey: String): Long

    // Seen (relay dedupe)
    fun seen(storageKey: String): SeenRecord?
    fun seenById(bundleId: ByteArray): List<SeenRecord>
    fun putSeen(record: SeenRecord)
    fun purgeSeen(nowWallMs: Long): Int

    // Tombstones
    fun tombstone(bundleId: ByteArray): TombstoneRecord?
    fun putTombstone(record: TombstoneRecord)
    fun purgeTombstones(nowWallMs: Long): Int

    // Sender outbox
    fun outgoing(bundleId: ByteArray): OutgoingMessage?
    fun allOutgoing(): List<OutgoingMessage>
    fun putOutgoing(message: OutgoingMessage)

    // Recipient inbox (recipient-level dedupe by inner_message_id, audit #4)
    fun delivered(innerMessageId: ByteArray): DeliveredMessage?
    fun allDelivered(): List<DeliveredMessage>
    fun putDelivered(message: DeliveredMessage)

    // Receipts
    fun putReceipt(record: ReceiptRecord)
    fun receipt(ackOfBundleId: ByteArray): ReceiptRecord?

    /** Runs [block] atomically where the backend supports it. */
    fun <T> transaction(block: () -> T): T
}
