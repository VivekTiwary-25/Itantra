package com.chmod777.itantra.transport

/**
 * Pure relay decision logic shared by the Bluetooth transport and unit tests.
 * A message ID is remembered when first seen, so it cannot circulate forever.
 */
class TransportRelayPolicy(private val maxRememberedIds: Int = DEFAULT_MAX_REMEMBERED_IDS) {
    private val seenMessageIds = linkedSetOf<Long>()

    @Synchronized
    fun rememberOutgoing(messageId: Long) {
        remember(messageId)
    }

    @Synchronized
    fun decideForIncoming(message: TransportMessage): RelayDecision {
        if (!remember(message.messageId)) return RelayDecision.Duplicate
        if (message.ttl == 0) return RelayDecision.TtlExpired
        return RelayDecision.Forward(message.copy(ttl = message.ttl - 1))
    }

    private fun remember(messageId: Long): Boolean {
        if (!seenMessageIds.add(messageId)) return false
        if (seenMessageIds.size > maxRememberedIds) {
            // Keep the newest ID after bounding memory use.
            seenMessageIds.clear()
            seenMessageIds.add(messageId)
        }
        return true
    }

    companion object {
        const val DEFAULT_MAX_REMEMBERED_IDS = 2_048
    }
}

sealed interface RelayDecision {
    data class Forward(val message: TransportMessage) : RelayDecision
    data object Duplicate : RelayDecision
    data object TtlExpired : RelayDecision
}
