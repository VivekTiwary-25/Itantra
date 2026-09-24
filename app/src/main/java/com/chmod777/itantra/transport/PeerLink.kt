package com.chmod777.itantra.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * One-hop byte transport to one peer (spec §6). Nothing above this interface may
 * touch BluetoothGatt, BluetoothSocket, MAC addresses or other transport IDs.
 *
 * A "frame" is one complete link frame (see `protocol/LinkFrame`). Implementations
 * fragment and reassemble internally and only ever deliver complete frames.
 */
interface PeerLink {
    /** The peer's rotating advertised short ID for BLE; a random per-link ID otherwise. Not identity. */
    val peerSessionId: ByteArray
    val state: State get() = stateFlow.value
    val stateFlow: StateFlow<State>

    /** INITIATOR = this side opened the link (GATT client); it also initiates Noise. */
    val role: LinkRole
    val transportKind: TransportKind

    /** Local-only label for logs and metrics. */
    val linkId: String

    /** Largest single frame this link accepts. */
    val maxFrameBytes: Int

    /** Negotiated ATT MTU for BLE GATT links; null for stream transports. For metrics only. */
    val negotiatedMtu: Int? get() = null

    suspend fun connect()

    /** Suspends until every fragment of [frame] is accepted by the radio stack, or throws [LinkClosedException]. */
    suspend fun send(frame: ByteArray)

    /** Complete inbound frames. Single collector. Completes when the link closes. */
    fun incomingFrames(): Flow<ByteArray>

    suspend fun close()

    enum class State { IDLE, CONNECTING, READY, CLOSING, CLOSED, FAILED }
}

enum class LinkRole { INITIATOR, RESPONDER }

enum class TransportKind(val label: String) {
    BLE_GATT("BLE_GATT"),
    RFCOMM("RFCOMM"),
    IN_MEMORY_TEST("IN_MEMORY_TEST"),
}

open class LinkClosedException(message: String, cause: Throwable? = null) : Exception(message, cause)
