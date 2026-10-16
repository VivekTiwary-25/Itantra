package com.chmod777.itantra.ui.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.chmod777.itantra.crypto.shortHex
import com.chmod777.itantra.crypto.toHex
import com.chmod777.itantra.dtn.DtnEvent
import com.chmod777.itantra.network.ItantraNetwork
import com.chmod777.itantra.service.NetworkingRuntime
import com.chmod777.itantra.toMessageDeliveryState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Logs, read straight from the DTN database.
 *
 * The database already holds both sides of every message, so nothing here is a copy that could
 * drift or be lost on a force-stop. The only things kept outside it are the two facts the
 * database has no column for: which received messages have been opened, and which sent ones
 * were Urgent.
 */
internal class MessageStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Newest first. */
    var messages: List<UiMessage> by mutableStateOf(emptyList())
        private set

    val unreadCount: Int get() = messages.count { it.unread }

    init {
        refresh()
    }

    fun markRead(id: String) {
        val read = readSet(KEY_READ)
        if (id in read) return
        writeSet(KEY_READ, read + id)
        messages = messages.map { if (it.id == id) it.copy(unread = false) else it }
    }

    /**
     * The `outgoing` table has no priority column, so once the process dies a sent Urgent
     * message would read back as Normal. Remembering it here is the only way to keep the tag
     * after a restart. A received message's urgency is not recorded anywhere either, so incoming
     * rows are always Normal; that is a gap in the engine, not something this store can fix.
     */
    fun markUrgent(bundleIdHex: String) {
        val urgent = readSet(KEY_URGENT)
        if (bundleIdHex in urgent) return
        writeSet(KEY_URGENT, urgent + bundleIdHex)
        messages = messages.map { if (it.id == bundleIdHex) it.copy(mode = MessageMode.URGENT) else it }
    }

    /** Re-reads the database. */
    fun refresh() {
        val dtn = NetworkingRuntime.dtn
        val names = NetworkingRuntime.database.all().associate { it.nodeId.toHex() to it.localName }
        fun nameOf(nodeId: ByteArray): String = names[nodeId.toHex()] ?: nodeId.shortHex()

        val read = readSet(KEY_READ)
        val urgent = readSet(KEY_URGENT)

        val sent = dtn.outgoingMessages().map { out ->
            val id = out.bundleId.toHex()
            UiMessage(
                id = id,
                direction = Direction.OUTGOING,
                peer = nameOf(out.recipientNodeId),
                peerNodeIdHex = out.recipientNodeId.toHex(),
                body = out.text,
                mode = if (id in urgent) MessageMode.URGENT else MessageMode.NORMAL,
                timestamp = out.createdWallMs,
                unread = false,
                languageCode = out.language,
                delivery = out.state.toMessageDeliveryState(),
            )
        }
        val received = dtn.deliveredMessages().map { inc ->
            val id = inc.innerMessageId.toHex()
            UiMessage(
                id = id,
                direction = Direction.INCOMING,
                peer = nameOf(inc.senderNodeId),
                peerNodeIdHex = inc.senderNodeId.toHex(),
                body = inc.text,
                mode = MessageMode.NORMAL,
                timestamp = inc.receivedWallMs,
                unread = id !in read,
                languageCode = inc.language,
                delivery = null,
            )
        }
        val merged = (sent + received).sortedByDescending { it.timestamp }
        if (merged != messages) messages = merged
    }

    // A missing or wrongly typed key is normal on a first run; treat both as empty.
    private fun readSet(key: String): Set<String> =
        runCatching { prefs.getStringSet(key, null)?.toSet() }.getOrNull() ?: emptySet()

    private fun writeSet(key: String, value: Set<String>) {
        runCatching { prefs.edit().putStringSet(key, value).apply() }
    }

    private companion object {
        const val PREFS = "ui_messages"
        const val KEY_READ = "read_ids"
        const val KEY_URGENT = "urgent_bundle_ids"
    }
}

@Composable
internal fun rememberMessageStore(): MessageStore {
    val context = LocalContext.current
    val store = remember { MessageStore(context) }
    LaunchedEffect(store) {
        // The state-change flow covers every send and every relay hop. The events flow adds an
        // arrival and the store being swept; OutgoingStateChanged is left out of it because the
        // first flow already carries it.
        merge(
            ItantraNetwork.outgoingStateChanges.map { },
            NetworkingRuntime.dtn.events
                .filter { it is DtnEvent.MessageDelivered || it is DtnEvent.StoreChanged }
                .map { },
        ).collect { store.refresh() }
    }
    return store
}
