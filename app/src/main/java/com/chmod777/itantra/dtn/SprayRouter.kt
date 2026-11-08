package com.chmod777.itantra.dtn

import com.chmod777.itantra.protocol.BundleSummary
import com.chmod777.itantra.protocol.Priority
import com.chmod777.itantra.protocol.ProtocolConfig

/**
 * Binary Spray-and-Wait decisions (spec §23, §48). Answers only SHOULD_TRANSFER?
 * and HOW_MANY_TOKENS?; it never touches sockets, crypto, storage or UI.
 */
class SprayRouter(private val config: ProtocolConfig) {

    /** Tokens not currently promised to an unacknowledged transfer. */
    fun availableTokens(stored: StoredBundle): Int = stored.copyTokens - (stored.pendingSplit?.tokens ?: 0)

    /**
     * Relay tokens to hand to a peer: floor(n/2) when n > 1; zero in the wait phase
     * (n == 1) or while an earlier split is still unconfirmed.
     */
    fun tokensToGive(stored: StoredBundle): Int {
        if (stored.pendingSplit != null) return 0
        val n = stored.copyTokens
        return if (n > 1) n / 2 else 0
    }

    /** Receiver side: request a relay copy only when the holder is still spraying. */
    fun shouldRequestRelay(summary: BundleSummary): Boolean = summary.copyTokens > 1

    /** Sender keeps ceil(n/2) once the receiver acknowledges persistence. */
    fun tokensAfterCommit(stored: StoredBundle): Int {
        val pending = stored.pendingSplit ?: return stored.copyTokens
        return (stored.copyTokens - pending.tokens).coerceAtLeast(1)
    }

    fun initialTokens(priority: Priority): Int = config.copyBudget(priority)
}
