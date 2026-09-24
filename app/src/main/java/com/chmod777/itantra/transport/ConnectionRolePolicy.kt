package com.chmod777.itantra.transport

import com.chmod777.itantra.crypto.compareUnsigned
import com.chmod777.itantra.protocol.ProtocolConfig

/**
 * Deterministic simultaneous-discovery rule (spec §8, audit #7).
 *
 * The side with the smaller advertised short ID initiates. The larger side waits
 * [ProtocolConfig.roleWaitMs] for an inbound link and then may initiate itself
 * (bounded role swap). Attempts are rate-limited per peer.
 */
class ConnectionRolePolicy(private val config: ProtocolConfig) {
    enum class Decision { INITIATE, WAIT, SKIP_RATE_LIMITED, SKIP_ALREADY_LINKED, SKIP_LINK_LIMIT }

    fun decide(
        localShortId: ByteArray,
        peerShortId: ByteArray,
        firstSeenMs: Long,
        nowMs: Long,
        alreadyLinked: Boolean,
        activeLinks: Int,
        attemptsInLastMinute: Int,
    ): Decision {
        if (alreadyLinked) return Decision.SKIP_ALREADY_LINKED
        if (activeLinks >= config.maxLinks) return Decision.SKIP_LINK_LIMIT
        if (attemptsInLastMinute >= config.connectAttemptsPerPeerPerMinute) return Decision.SKIP_RATE_LIMITED
        val weInitiate = compareUnsigned(localShortId, peerShortId) < 0
        if (weInitiate) return Decision.INITIATE
        return if (nowMs - firstSeenMs >= config.roleWaitMs) Decision.INITIATE else Decision.WAIT
    }

    /**
     * When a collision produced two links to the same peer, keep the one whose
     * initiator holds the smaller short ID. Both sides reach the same answer.
     */
    fun preferredLocalRole(localShortId: ByteArray, peerShortId: ByteArray): LinkRole =
        if (compareUnsigned(localShortId, peerShortId) < 0) LinkRole.INITIATOR else LinkRole.RESPONDER
}

/**
 * Bounds BLE scan (re)starts (audit H): Android throttles apps that start scans
 * more than about 5 times in 30 s, silently returning no results.
 */
class ScanRestartLimiter(private val config: ProtocolConfig) {
    private val starts = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(nowMs: Long): Boolean {
        while (starts.isNotEmpty() && nowMs - starts.first() >= 30_000) starts.removeFirst()
        val last = starts.lastOrNull()
        if (last != null && nowMs - last < config.minScanStartIntervalMs) return false
        if (starts.size >= config.maxScanStartsPer30s) return false
        starts.addLast(nowMs)
        return true
    }
}

/** Sliding-window counter used for every per-peer rate limit (spec §52). */
class SlidingWindowLimiter(private val windowMs: Long, private val maxEvents: Int) {
    private val events = HashMap<String, ArrayDeque<Long>>()

    @Synchronized
    fun count(key: String, nowMs: Long): Int = prune(key, nowMs).size

    @Synchronized
    fun tryAcquire(key: String, nowMs: Long): Boolean {
        val queue = prune(key, nowMs)
        if (queue.size >= maxEvents) return false
        queue.addLast(nowMs)
        return true
    }

    private fun prune(key: String, nowMs: Long): ArrayDeque<Long> {
        val queue = events.getOrPut(key) { ArrayDeque() }
        while (queue.isNotEmpty() && nowMs - queue.first() >= windowMs) queue.removeFirst()
        if (events.size > 4_096) events.entries.removeIf { it.value.isEmpty() }
        return queue
    }
}
