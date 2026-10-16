package com.chmod777.itantra.ui.app

import com.chmod777.itantra.MessageDeliveryState

/**
 * The view types the demo's screens are written against, kept and made real.
 *
 * `DemoMessage` carried a fake `audioUri` for the prop player and nothing about delivery. This
 * carries a delivery state instead, because that is what the engine actually knows.
 */
enum class MessageMode {
    NORMAL, URGENT, SOS, SOS_REPLY;

    val isSos: Boolean get() = this == SOS || this == SOS_REPLY
}

enum class Direction { INCOMING, OUTGOING }

/** Only ever NONE until the SOS screens land at step 4; the row and detail layouts already read it. */
enum class SosResponse { NONE, ACCEPTED, DECLINED }

/**
 * One row of Logs, built from the DTN database rather than held in memory.
 *
 * [id] is the bundle id for a message this phone sent and the inner message id for one it
 * received — both already unique, both stable across a restart, which is what lets read/unread
 * be remembered separately from the engine's own tables.
 */
internal data class UiMessage(
    val id: String,
    val direction: Direction,
    /** Incoming: who sent it. Outgoing: who it went to. Null when the node id matches no contact. */
    val peer: String?,
    val body: String,
    val mode: MessageMode,
    /** Wall clock, milliseconds: created for a sent message, received for an arriving one. */
    val timestamp: Long,
    val unread: Boolean,
    val languageCode: String,
    /** Null on a received message: there is no delivery state for something already here. */
    val delivery: MessageDeliveryState?,
    val sosResponse: SosResponse = SosResponse.NONE,
)
