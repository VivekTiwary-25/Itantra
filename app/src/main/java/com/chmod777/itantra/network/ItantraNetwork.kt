package com.chmod777.itantra.network

import android.content.Context
import android.os.SystemClock
import com.chmod777.itantra.crypto.shortHex
import com.chmod777.itantra.crypto.toHex
import com.chmod777.itantra.dtn.DeliveryState
import com.chmod777.itantra.dtn.DtnEvent
import com.chmod777.itantra.protocol.Priority
import com.chmod777.itantra.service.EmergencyModeService
import com.chmod777.itantra.service.EmergencyState
import com.chmod777.itantra.service.NetworkingRuntime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** A trusted contact the product may address. Selection is by node ID, never by name. */
data class RecipientOption(val nodeIdHex: String, val name: String, val fingerprint: String)

/** An authenticated end-recipient delivery waiting to be shown and spoken once. */
data class ProductDelivery(
    val bundleIdHex: String,
    val text: String,
    val languageCode: String,
    val senderName: String,
    /** This phone's elapsedRealtimeNanos when the DTN layer accepted the message. */
    val deliveredAtElapsedNs: Long,
)

/**
 * Product-level networking API (execution spec §8.2). The app shell talks only to
 * this object: no BluetoothGatt, sockets, MAC addresses, bundles or keys leak out.
 *
 * Speech stays outside: the app passes text + ISO language in and receives text +
 * language back, only for messages addressed to this phone.
 */
object ItantraNetwork {
    private val pending = MutableStateFlow<List<ProductDelivery>>(emptyList())
    private var collecting = false

    fun init(context: Context) {
        NetworkingRuntime.init(context)
        synchronized(this) {
            if (collecting) return
            collecting = true
        }
        // Process-scoped so deliveries that arrive while no screen is open are not lost.
        NetworkingRuntime.scope.launch {
            NetworkingRuntime.dtn.events.filterIsInstance<DtnEvent.MessageDelivered>().collect { event ->
                val delivery = ProductDelivery(
                    bundleIdHex = event.message.bundleId.toHex(),
                    text = event.message.text,
                    languageCode = event.message.language,
                    senderName = event.from.localName,
                    deliveredAtElapsedNs = SystemClock.elapsedRealtimeNanos(),
                )
                NetworkingRuntime.metrics.record(
                    "product_delivered",
                    mapOf("bundle" to event.message.bundleId.shortHex(), "lang" to event.message.language, "t_delivered_ns" to delivery.deliveredAtElapsedNs),
                )
                pending.value = pending.value + delivery
            }
        }
    }

    val emergencyState: StateFlow<EmergencyState> get() = NetworkingRuntime.emergencyState

    /** Number of live hop-secure sessions (0 when Emergency mode is off). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val nearbyLinkCount: Flow<Int>
        get() = NetworkingRuntime.session.flatMapLatest { session -> session?.core?.sessions?.map { it.size } ?: flowOf(0) }

    fun startEmergencyMode(context: Context) = EmergencyModeService.start(context)
    fun stopEmergencyMode(context: Context) = EmergencyModeService.stop(context)

    fun recipients(): List<RecipientOption> =
        NetworkingRuntime.database.all().map { RecipientOption(it.nodeId.toHex(), it.localName, it.fingerprint) }

    /**
     * Persists a signed, encrypted bundle for [recipientNodeIdHex] (spec §50). Returns
     * the bundle handle in state QUEUED, or a user-facing failure. Nothing is "sent" here.
     */
    fun queueTrustedMessage(recipientNodeIdHex: String, text: String, languageCode: String, urgent: Boolean = false): Result<String> = runCatching {
        val nodeId = ByteArray(recipientNodeIdHex.length / 2) { recipientNodeIdHex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val outgoing = NetworkingRuntime.dtn.createMessage(nodeId, text, languageCode, if (urgent) Priority.URGENT else Priority.NORMAL)
        NetworkingRuntime.metrics.record(
            "product_queued",
            mapOf("bundle" to outgoing.bundleId.shortHex(), "lang" to languageCode, "t_queued_ns" to SystemClock.elapsedRealtimeNanos()),
        )
        outgoing.bundleId.toHex()
    }

    /** Sender state changes: QUEUED → RELAYED → DELIVERED (verified receipt only) / EXPIRED / UNKNOWN. */
    val outgoingStateChanges: Flow<Pair<String, DeliveryState>>
        get() = NetworkingRuntime.dtn.events.filterIsInstance<DtnEvent.OutgoingStateChanged>().map { it.message.bundleId.toHex() to it.message.state }

    fun currentStates(): Map<String, DeliveryState> = NetworkingRuntime.dtn.outgoingMessages().associate { it.bundleId.toHex() to it.state }

    /** Deliveries not yet shown/spoken by the product UI. */
    val pendingDeliveries: StateFlow<List<ProductDelivery>> get() = pending

    /** The UI has shown (and queued TTS for) this delivery; it will not be handed out again. */
    fun acknowledgeDelivery(bundleIdHex: String) {
        pending.value = pending.value.filterNot { it.bundleIdHex == bundleIdHex }
    }

    /**
     * Timing hook for the later end-to-end benchmark (execution spec §9 Product test B).
     * Each value is this phone's monotonic clock only; cross-phone subtraction is invalid.
     */
    fun recordProductTiming(event: String, fields: Map<String, Any?> = emptyMap()) {
        NetworkingRuntime.metrics.record(event, fields + ("t_event_ns" to SystemClock.elapsedRealtimeNanos()))
    }
}
