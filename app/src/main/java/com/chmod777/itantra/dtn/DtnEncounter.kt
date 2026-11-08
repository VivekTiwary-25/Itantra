package com.chmod777.itantra.dtn

import com.chmod777.itantra.metrics.MetricsSink
import com.chmod777.itantra.protocol.BundleAckFrame
import com.chmod777.itantra.protocol.BundleFrame
import com.chmod777.itantra.protocol.BundleSummary
import com.chmod777.itantra.protocol.InventoryEndFrame
import com.chmod777.itantra.protocol.InventoryFrame
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.protocol.ProtocolFrame
import com.chmod777.itantra.protocol.TombstoneFrame
import com.chmod777.itantra.protocol.WantFrame
import com.chmod777.itantra.session.FrameSink
import com.chmod777.itantra.transport.SlidingWindowLimiter
import java.security.SecureRandom

/**
 * Drives one encounter over one secure session (spec §24–§29):
 * both sides send a paginated inventory → each plans WANTs + tombstones →
 * holders serve bundles → receivers persist and hop-ACK.
 */
class DtnEncounter(
    private val node: DtnNode,
    private val sink: FrameSink,
    peerSessionKey: String,
    private val config: ProtocolConfig,
    private val clock: DtnClock,
    private val metrics: MetricsSink,
) {
    private val budget = SessionBudget(peerSessionKey)
    private val peerPages = LinkedHashMap<Long, MutableList<BundleSummary>>()
    private val inboundRounds = SlidingWindowLimiter(60_000, 12)
    private var lastRoundStartMs = Long.MIN_VALUE / 2
    private var malformed = 0

    /** Sends our inventory. Rate-limited so store churn cannot flood a peer. */
    suspend fun startRound(force: Boolean = false): Boolean {
        val now = clock.elapsedMs()
        if (!force && now - lastRoundStartMs < config.minInventoryRoundIntervalMs) return false
        lastRoundStartMs = now
        val roundId = random.nextInt().toLong() and 0xFFFF_FFFFL
        val pages = node.inventory().chunked(config.inventoryPageSize).take(config.maxInventoryPages)
        pages.forEachIndexed { index, page -> sink.sendFrame(InventoryFrame(roundId, index, page)) }
        sink.sendFrame(InventoryEndFrame(roundId, pages.size))
        metrics.record("dtn_inventory_tx", mapOf("round" to roundId, "summaries" to pages.sumOf { it.size }, "pages" to pages.size))
        return true
    }

    /** Returns false when the peer misbehaved badly enough to drop the session. */
    suspend fun handle(frame: ProtocolFrame): Boolean {
        when (frame) {
            is InventoryFrame -> {
                if (frame.pageIndex == 0 && !inboundRounds.tryAcquire("inv", clock.elapsedMs())) return strike("inventory rate")
                // Keep at most two in-flight rounds' pages.
                while (peerPages.size >= 2 && !peerPages.containsKey(frame.roundId)) peerPages.remove(peerPages.keys.first())
                val pages = peerPages.getOrPut(frame.roundId) { ArrayList() }
                if (pages.size + frame.summaries.size > config.inventoryPageSize * config.maxInventoryPages) return strike("inventory size")
                pages += frame.summaries
            }
            is InventoryEndFrame -> {
                val summaries = peerPages.remove(frame.roundId) ?: emptyList()
                val plan = node.planEncounter(summaries, budget)
                plan.tombstones.forEach { sink.sendFrame(TombstoneFrame(it.encode())) }
                if (plan.wants.isNotEmpty()) sink.sendFrame(WantFrame(frame.roundId, plan.wants))
                metrics.record(
                    "dtn_inventory_rx",
                    mapOf("round" to frame.roundId, "summaries" to summaries.size, "wants" to plan.wants.size, "tombstones_tx" to plan.tombstones.size),
                )
            }
            is WantFrame -> for (want in frame.wants) {
                val bundle = node.serveWant(want, budget) ?: continue
                val started = clock.monotonicNs()
                sink.sendFrame(bundle)
                metrics.record(
                    "dtn_bundle_tx",
                    mapOf("mode" to want.mode.name, "bytes" to bundle.immutableBytes.size, "tokens" to bundle.relayState.copyTokens, "send_ns" to clock.monotonicNs() - started),
                )
            }
            is BundleFrame -> {
                val result = node.ingest(frame, budget)
                sink.sendFrame(result.ack)
                result.tombstones.forEach { sink.sendFrame(TombstoneFrame(it.encode())) }
                metrics.record("dtn_bundle_rx", mapOf("mode" to frame.mode.name, "bytes" to frame.immutableBytes.size, "ack" to result.ack.status.name))
            }
            is BundleAckFrame -> node.onAck(frame, budget)
            is TombstoneFrame -> {
                val applied = node.onTombstone(frame.tombstoneBytes)
                metrics.record("dtn_tombstone_rx", mapOf("applied" to applied))
            }
            else -> return true
        }
        return true
    }

    private fun strike(reason: String): Boolean {
        metrics.record("dtn_peer_strike", mapOf("reason" to reason))
        return ++malformed < config.maxMalformedFramesPerSession
    }

    companion object {
        private val random = SecureRandom()
    }
}
