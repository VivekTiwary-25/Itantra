package com.chmod777.itantra

import com.chmod777.itantra.transport.SendMessageResult

internal enum class MessageDeliveryState(val label: String) {
    SENT("Awaiting ACK"),
    DELIVERED("Delivered"),
}

internal fun SendMessageResult.failureMessage(): String? = when (this) {
    is SendMessageResult.Sent -> null
    SendMessageResult.NotConnected -> "Not connected. Connect to another phone and try again."
    is SendMessageResult.Error -> "Send failed: $message"
}

internal fun matchesAcknowledgement(transportMessageId: Long?, acknowledgedMessageId: Long): Boolean =
    transportMessageId == acknowledgedMessageId
