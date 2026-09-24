package com.chmod777.itantra.dtn.sqlite

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.chmod777.itantra.crypto.toHex
import com.chmod777.itantra.dtn.AgeState
import com.chmod777.itantra.dtn.BundleOrigin
import com.chmod777.itantra.dtn.DeliveredMessage
import com.chmod777.itantra.dtn.DeliveryState
import com.chmod777.itantra.dtn.DtnStore
import com.chmod777.itantra.dtn.OutgoingMessage
import com.chmod777.itantra.dtn.PendingSplit
import com.chmod777.itantra.dtn.ReceiptRecord
import com.chmod777.itantra.dtn.SeenRecord
import com.chmod777.itantra.dtn.StoredBundle
import com.chmod777.itantra.dtn.TombstoneRecord
import com.chmod777.itantra.identity.TrustMethod
import com.chmod777.itantra.identity.TrustedContact
import com.chmod777.itantra.identity.TrustedContactStore
import com.chmod777.itantra.protocol.PrivateBundleV1
import com.chmod777.itantra.protocol.ProtocolConfig

/**
 * Durable networking database (spec §20, §46; IMPLEMENTATION_NOTES §3.1: framework
 * SQLite instead of Room). WAL + synchronous=FULL so a relay ACK ("I persisted
 * these bytes") is only sent after the row is on disk. Excluded from backup.
 *
 * Relay bundle rows hold only the relay-visible envelope (ciphertext); plaintext
 * exists only in `outgoing` (the user's own sent items) and `delivered` (the
 * user's own inbox).
 */
class NetDatabase(context: Context, private val config: ProtocolConfig, name: String = NAME) :
    SQLiteOpenHelper(context, name, null, VERSION), DtnStore, TrustedContactStore {

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onOpen(db: SQLiteDatabase) {
        db.rawQuery("PRAGMA synchronous=FULL", null).use { it.moveToFirst() }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE contacts (node_id TEXT PRIMARY KEY, local_name TEXT NOT NULL, signing_pk BLOB NOT NULL,
               encryption_pk BLOB NOT NULL, fingerprint TEXT NOT NULL, key_epoch INTEGER NOT NULL, trust_method TEXT NOT NULL,
               created_at INTEGER NOT NULL)""",
        )
        db.execSQL(
            """CREATE TABLE bundles (storage_key TEXT PRIMARY KEY, bundle_id TEXT NOT NULL, immutable_blob BLOB NOT NULL,
               size INTEGER NOT NULL, priority INTEGER NOT NULL, copy_tokens INTEGER NOT NULL, hop_count INTEGER NOT NULL,
               age_base_ms INTEGER NOT NULL, age_base_elapsed_ms INTEGER NOT NULL, age_base_boot INTEGER NOT NULL,
               origin TEXT NOT NULL, source_peer TEXT, created_wall INTEGER NOT NULL, last_updated INTEGER NOT NULL,
               pending_tokens INTEGER, pending_peer TEXT, pending_elapsed INTEGER, pending_boot INTEGER)""",
        )
        db.execSQL("CREATE INDEX bundles_by_id ON bundles(bundle_id)")
        db.execSQL(
            """CREATE TABLE seen (storage_key TEXT PRIMARY KEY, bundle_id TEXT NOT NULL, ciphertext_hash BLOB NOT NULL,
               deletion_commitment BLOB NOT NULL, retain_until INTEGER NOT NULL)""",
        )
        db.execSQL("CREATE INDEX seen_by_id ON seen(bundle_id)")
        db.execSQL("CREATE TABLE tombstones (bundle_id TEXT PRIMARY KEY, deletion_secret BLOB NOT NULL, expires_at INTEGER NOT NULL)")
        db.execSQL(
            """CREATE TABLE outgoing (bundle_id TEXT PRIMARY KEY, inner_message_id BLOB NOT NULL, recipient_node_id BLOB NOT NULL,
               text TEXT NOT NULL, language TEXT NOT NULL, deletion_secret BLOB NOT NULL, created_wall INTEGER NOT NULL,
               state TEXT NOT NULL, updated_wall INTEGER NOT NULL)""",
        )
        db.execSQL(
            """CREATE TABLE delivered (inner_message_id TEXT PRIMARY KEY, bundle_id BLOB NOT NULL, sender_node_id BLOB NOT NULL,
               language TEXT NOT NULL, text TEXT NOT NULL, created_hint INTEGER NOT NULL, received_wall INTEGER NOT NULL)""",
        )
        db.execSQL(
            """CREATE TABLE receipts (ack_of_bundle_id TEXT PRIMARY KEY, recipient_node_id BLOB NOT NULL, receipt_hint INTEGER NOT NULL,
               verified_wall INTEGER NOT NULL)""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private val db: SQLiteDatabase get() = writableDatabase

    // ------------------------------------------------------------ contacts

    override fun all(): List<TrustedContact> = db.query("contacts", null, null, null, null, null, "created_at").use { c ->
        generateSequence { if (c.moveToNext()) contact(c) else null }.toList()
    }

    override fun byNodeId(nodeId: ByteArray): TrustedContact? =
        db.query("contacts", null, "node_id=?", arrayOf(nodeId.toHex()), null, null, null).use { if (it.moveToFirst()) contact(it) else null }

    override fun upsert(contact: TrustedContact): Boolean {
        db.insertWithOnConflict(
            "contacts", null,
            ContentValues().apply {
                put("node_id", contact.nodeId.toHex())
                put("local_name", contact.localName)
                put("signing_pk", contact.signingPublicKey)
                put("encryption_pk", contact.encryptionPublicKey)
                put("fingerprint", contact.fingerprint)
                put("key_epoch", contact.keyEpoch)
                put("trust_method", contact.trustMethod.name)
                put("created_at", contact.createdAtWallMs)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        return true
    }

    override fun rename(nodeId: ByteArray, localName: String): Boolean =
        db.update("contacts", ContentValues().apply { put("local_name", localName) }, "node_id=?", arrayOf(nodeId.toHex())) > 0

    override fun remove(nodeId: ByteArray): Boolean = db.delete("contacts", "node_id=?", arrayOf(nodeId.toHex())) > 0

    private fun contact(c: Cursor) = TrustedContact(
        nodeId = hexBytes(c.str("node_id")),
        localName = c.str("local_name"),
        signingPublicKey = c.blob("signing_pk"),
        encryptionPublicKey = c.blob("encryption_pk"),
        fingerprint = c.str("fingerprint"),
        keyEpoch = c.long("key_epoch"),
        trustMethod = TrustMethod.valueOf(c.str("trust_method")),
        createdAtWallMs = c.long("created_at"),
    )

    // ------------------------------------------------------------- bundles

    override fun bundle(storageKey: String): StoredBundle? = queryBundles("storage_key=?", arrayOf(storageKey)).firstOrNull()
    override fun bundlesById(bundleId: ByteArray): List<StoredBundle> = queryBundles("bundle_id=?", arrayOf(bundleId.toHex()))
    override fun allBundles(): List<StoredBundle> = queryBundles(null, null)

    override fun putBundle(bundle: StoredBundle) {
        db.insertWithOnConflict(
            "bundles", null,
            ContentValues().apply {
                put("storage_key", bundle.storageKey)
                put("bundle_id", bundle.bundle.bundleId.toHex())
                put("immutable_blob", bundle.immutableBytes)
                put("size", bundle.sizeBytes)
                put("priority", bundle.bundle.priority.wire)
                put("copy_tokens", bundle.copyTokens)
                put("hop_count", bundle.hopCount)
                put("age_base_ms", bundle.age.ageBaseMs)
                put("age_base_elapsed_ms", bundle.age.ageBaseElapsedMs)
                put("age_base_boot", bundle.age.ageBaseBootCount)
                put("origin", bundle.origin.name)
                put("source_peer", bundle.sourcePeerSessionKey)
                put("created_wall", bundle.createdWallMs)
                put("last_updated", System.currentTimeMillis())
                val pending = bundle.pendingSplit
                if (pending == null) {
                    putNull("pending_tokens"); putNull("pending_peer"); putNull("pending_elapsed"); putNull("pending_boot")
                } else {
                    put("pending_tokens", pending.tokens)
                    put("pending_peer", pending.peerSessionKey)
                    put("pending_elapsed", pending.startedElapsedMs)
                    put("pending_boot", pending.bootCount)
                }
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun deleteBundle(storageKey: String) {
        db.delete("bundles", "storage_key=?", arrayOf(storageKey))
    }

    override fun totalBundleBytes(): Long = db.rawQuery("SELECT COALESCE(SUM(size),0) FROM bundles", null).use { it.moveToFirst(); it.getLong(0) }

    override fun bundleBytesFromPeerSession(peerSessionKey: String): Long =
        db.rawQuery("SELECT COALESCE(SUM(size),0) FROM bundles WHERE source_peer=?", arrayOf(peerSessionKey)).use { it.moveToFirst(); it.getLong(0) }

    private fun queryBundles(where: String?, args: Array<String>?): List<StoredBundle> =
        db.query("bundles", null, where, args, null, null, null).use { c ->
            generateSequence { if (c.moveToNext()) storedBundle(c) else null }.filterNotNull().toList()
        }

    private fun storedBundle(c: Cursor): StoredBundle? {
        val bytes = c.blob("immutable_blob")
        // Re-validate on load: a corrupted row is dropped rather than routed.
        val bundle = runCatching { PrivateBundleV1.decodeImmutable(bytes, config) }.getOrNull() ?: return null
        val pendingTokens = if (c.isNull(c.getColumnIndexOrThrow("pending_tokens"))) null else c.int("pending_tokens")
        return StoredBundle(
            bundle = bundle,
            immutableBytes = bytes,
            copyTokens = c.int("copy_tokens"),
            hopCount = c.int("hop_count"),
            age = AgeState(c.long("age_base_ms"), c.long("age_base_elapsed_ms"), c.int("age_base_boot")),
            origin = BundleOrigin.valueOf(c.str("origin")),
            sourcePeerSessionKey = c.strOrNull("source_peer"),
            createdWallMs = c.long("created_wall"),
            pendingSplit = pendingTokens?.let { PendingSplit(it, c.str("pending_peer"), c.long("pending_elapsed"), c.int("pending_boot")) },
        )
    }

    // ---------------------------------------------------------------- seen

    override fun seen(storageKey: String): SeenRecord? =
        db.query("seen", null, "storage_key=?", arrayOf(storageKey), null, null, null).use { if (it.moveToFirst()) seenRecord(it) else null }

    override fun seenById(bundleId: ByteArray): List<SeenRecord> =
        db.query("seen", null, "bundle_id=?", arrayOf(bundleId.toHex()), null, null, null).use { c ->
            generateSequence { if (c.moveToNext()) seenRecord(c) else null }.toList()
        }

    override fun putSeen(record: SeenRecord) {
        db.insertWithOnConflict(
            "seen", null,
            ContentValues().apply {
                put("storage_key", record.storageKey)
                put("bundle_id", record.bundleId.toHex())
                put("ciphertext_hash", record.ciphertextHash)
                put("deletion_commitment", record.deletionCommitment)
                put("retain_until", record.retainUntilWallMs)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun purgeSeen(nowWallMs: Long): Int = db.delete("seen", "retain_until<?", arrayOf(nowWallMs.toString()))

    private fun seenRecord(c: Cursor) = SeenRecord(hexBytes(c.str("bundle_id")), c.blob("ciphertext_hash"), c.blob("deletion_commitment"), c.long("retain_until"))

    // ---------------------------------------------------------- tombstones

    override fun tombstone(bundleId: ByteArray): TombstoneRecord? =
        db.query("tombstones", null, "bundle_id=?", arrayOf(bundleId.toHex()), null, null, null).use {
            if (it.moveToFirst()) TombstoneRecord(bundleId, it.blob("deletion_secret"), it.long("expires_at")) else null
        }

    override fun putTombstone(record: TombstoneRecord) {
        db.insertWithOnConflict(
            "tombstones", null,
            ContentValues().apply {
                put("bundle_id", record.bundleId.toHex())
                put("deletion_secret", record.deletionSecret)
                put("expires_at", record.expiresAtWallMs)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun purgeTombstones(nowWallMs: Long): Int = db.delete("tombstones", "expires_at<?", arrayOf(nowWallMs.toString()))

    // ------------------------------------------------------------ outgoing

    override fun outgoing(bundleId: ByteArray): OutgoingMessage? =
        db.query("outgoing", null, "bundle_id=?", arrayOf(bundleId.toHex()), null, null, null).use { if (it.moveToFirst()) outgoing(it) else null }

    override fun allOutgoing(): List<OutgoingMessage> = db.query("outgoing", null, null, null, null, null, "created_wall").use { c ->
        generateSequence { if (c.moveToNext()) outgoing(c) else null }.toList()
    }

    override fun putOutgoing(message: OutgoingMessage) {
        db.insertWithOnConflict(
            "outgoing", null,
            ContentValues().apply {
                put("bundle_id", message.bundleId.toHex())
                put("inner_message_id", message.innerMessageId)
                put("recipient_node_id", message.recipientNodeId)
                put("text", message.text)
                put("language", message.language)
                put("deletion_secret", message.deletionSecret)
                put("created_wall", message.createdWallMs)
                put("state", message.state.name)
                put("updated_wall", message.updatedWallMs)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun outgoing(c: Cursor) = OutgoingMessage(
        bundleId = hexBytes(c.str("bundle_id")),
        innerMessageId = c.blob("inner_message_id"),
        recipientNodeId = c.blob("recipient_node_id"),
        text = c.str("text"),
        language = c.str("language"),
        deletionSecret = c.blob("deletion_secret"),
        createdWallMs = c.long("created_wall"),
        state = DeliveryState.valueOf(c.str("state")),
        updatedWallMs = c.long("updated_wall"),
    )

    // ----------------------------------------------------------- delivered

    override fun delivered(innerMessageId: ByteArray): DeliveredMessage? =
        db.query("delivered", null, "inner_message_id=?", arrayOf(innerMessageId.toHex()), null, null, null).use { if (it.moveToFirst()) delivered(it) else null }

    override fun allDelivered(): List<DeliveredMessage> = db.query("delivered", null, null, null, null, null, "received_wall").use { c ->
        generateSequence { if (c.moveToNext()) delivered(c) else null }.toList()
    }

    override fun putDelivered(message: DeliveredMessage) {
        db.insertWithOnConflict(
            "delivered", null,
            ContentValues().apply {
                put("inner_message_id", message.innerMessageId.toHex())
                put("bundle_id", message.bundleId)
                put("sender_node_id", message.senderNodeId)
                put("language", message.language)
                put("text", message.text)
                put("created_hint", message.createdTimeHintMs)
                put("received_wall", message.receivedWallMs)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    private fun delivered(c: Cursor) = DeliveredMessage(
        innerMessageId = hexBytes(c.str("inner_message_id")),
        bundleId = c.blob("bundle_id"),
        senderNodeId = c.blob("sender_node_id"),
        language = c.str("language"),
        text = c.str("text"),
        createdTimeHintMs = c.long("created_hint"),
        receivedWallMs = c.long("received_wall"),
    )

    // ------------------------------------------------------------ receipts

    override fun putReceipt(record: ReceiptRecord) {
        db.insertWithOnConflict(
            "receipts", null,
            ContentValues().apply {
                put("ack_of_bundle_id", record.ackOfBundleId.toHex())
                put("recipient_node_id", record.recipientNodeId)
                put("receipt_hint", record.receiptTimeHintMs)
                put("verified_wall", record.verifiedWallMs)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun receipt(ackOfBundleId: ByteArray): ReceiptRecord? =
        db.query("receipts", null, "ack_of_bundle_id=?", arrayOf(ackOfBundleId.toHex()), null, null, null).use {
            if (it.moveToFirst()) ReceiptRecord(ackOfBundleId, it.blob("recipient_node_id"), it.long("receipt_hint"), it.long("verified_wall")) else null
        }

    override fun <T> transaction(block: () -> T): T {
        val database = db
        database.beginTransaction()
        try {
            val result = block()
            database.setTransactionSuccessful()
            return result
        } finally {
            database.endTransaction()
        }
    }

    companion object {
        const val NAME = "itantra_net.db"
        const val VERSION = 1

        private fun hexBytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        private fun Cursor.str(column: String): String = getString(getColumnIndexOrThrow(column))
        private fun Cursor.strOrNull(column: String): String? = getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }
        private fun Cursor.blob(column: String): ByteArray = getBlob(getColumnIndexOrThrow(column))
        private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
        private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
    }
}
