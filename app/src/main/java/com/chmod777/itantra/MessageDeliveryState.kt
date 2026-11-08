package com.chmod777.itantra

import com.chmod777.itantra.dtn.DeliveryState
import com.chmod777.itantra.transport.SendMessageResult

/**
 * Sender-visible states (spec §21, §57). DELIVERED is reachable only from a verified
 * end-to-end receipt on the v1 stack. The legacy RFCOMM demo can at most report that
 * the next phone received the frame, because a relay also ACKs.
 */
internal enum class MessageDeliveryState(val label: String) {
    QUEUED("Queued"),
    RELAYED("Relayed"),
    DELIVERED("Delivered"),
    EXPIRED("Expired"),
    UNKNOWN("Unknown"),
    LEGACY_SENT("Sent (legacy RFCOMM)"),
    LEGACY_NEXT_HOP("Next phone received (legacy)"),
}

internal fun DeliveryState.toMessageDeliveryState(): MessageDeliveryState = when (this) {
    DeliveryState.QUEUED -> MessageDeliveryState.QUEUED
    DeliveryState.RELAYED -> MessageDeliveryState.RELAYED
    DeliveryState.DELIVERED -> MessageDeliveryState.DELIVERED
    DeliveryState.EXPIRED -> MessageDeliveryState.EXPIRED
    DeliveryState.UNKNOWN -> MessageDeliveryState.UNKNOWN
}

/** A legacy adjacent-hop ACK: never evidence that the intended person received it. */
internal fun legacyAcknowledgedState(): MessageDeliveryState = MessageDeliveryState.LEGACY_NEXT_HOP

internal fun SendMessageResult.failureMessage(): String? = when (this) {
    is SendMessageResult.Sent -> null
    SendMessageResult.NotConnected -> "Not connected. Connect to another phone and try again."
    is SendMessageResult.Error -> "Send failed: $message"
}

internal fun matchesAcknowledgement(transportMessageId: Long?, acknowledgedMessageId: Long): Boolean =
    transportMessageId == acknowledgedMessageId
