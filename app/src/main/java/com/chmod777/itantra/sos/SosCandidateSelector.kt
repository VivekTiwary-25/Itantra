package com.chmod777.itantra.sos

import com.chmod777.itantra.protocol.ProtocolConfig
import kotlin.random.Random

/** Coarse radio-contact quality. Never converted to metres or "closest person" (spec §4.1, §35). */
enum class RssiBucket { STRONG, MEDIUM, WEAK }

data class SosCandidate(val shortIdHex: String, val rssiSamples: List<Int>, val availableToHelp: Boolean)

/**
 * Candidate ordering (spec §35): only phones advertising "available to help",
 * bucketed by the median of several RSSI samples, randomised within a bucket.
 */
class SosCandidateSelector(private val config: ProtocolConfig, private val random: Random = Random.Default) {
    fun bucket(samples: List<Int>): RssiBucket? {
        if (samples.isEmpty()) return null
        val median = samples.sorted()[samples.size / 2]
        return when {
            median >= config.sosRssiStrongDbm -> RssiBucket.STRONG
            median >= config.sosRssiMediumDbm -> RssiBucket.MEDIUM
            else -> RssiBucket.WEAK
        }
    }

    fun order(candidates: List<SosCandidate>): List<SosCandidate> = candidates
        .filter { it.availableToHelp && bucket(it.rssiSamples) != null }
        .groupBy { bucket(it.rssiSamples)!! }
        .toSortedMap()
        .values
        .flatMap { it.shuffled(random) }
}

/** Wave policy (spec §36, §39). All thresholds come from [ProtocolConfig]. */
class SosWavePlanner(private val config: ProtocolConfig) {
    data class Wave(val index: Int, val directCandidateLimit: Int, val relayHopLimit: Int?)

    fun waveAt(elapsedMs: Long): Wave = when {
        elapsedMs >= config.sosWave3StartMs -> Wave(3, config.sosWave0Candidates + config.sosWave1Candidates, config.sosWave3HopLimit)
        elapsedMs >= config.sosWave2StartMs -> Wave(2, config.sosWave0Candidates + config.sosWave1Candidates, config.sosWave2HopLimit)
        elapsedMs >= config.sosWave1StartMs -> Wave(1, config.sosWave0Candidates + config.sosWave1Candidates, null)
        else -> Wave(0, config.sosWave0Candidates, null)
    }
}

/**
 * Controlled SOS request flooding (spec §40): each request_frame_id is forwarded
 * at most once, within hop limit, TTL and rate limits, with random jitter.
 */
class SosRelayRouter(private val config: ProtocolConfig, private val random: Random = Random.Default) {
    private val seen = LinkedHashMap<String, Long>()
    private val cancelled = LinkedHashMap<String, Long>()
    private val relayTimes = ArrayDeque<Long>()

    enum class Decision { FORWARD, DELIVER_ONLY, DUPLICATE, CANCELLED, EXPIRED, RATE_LIMITED }

    @Synchronized
    fun decide(requestFrameIdHex: String, sosIdHex: String, hopCount: Int, hopLimit: Int, remainingTtlMs: Long, nowMs: Long): Decision {
        prune(nowMs)
        if (cancelled.containsKey(sosIdHex)) return Decision.CANCELLED
        if (seen.containsKey(requestFrameIdHex)) return Decision.DUPLICATE
        seen[requestFrameIdHex] = nowMs
        if (remainingTtlMs <= 0) return Decision.EXPIRED
        if (hopCount + 1 > hopLimit) return Decision.DELIVER_ONLY
        while (relayTimes.isNotEmpty() && nowMs - relayTimes.first() >= 60_000) relayTimes.removeFirst()
        if (relayTimes.size >= config.maxSosRelayFramesPerMinute) return Decision.RATE_LIMITED
        relayTimes.addLast(nowMs)
        return Decision.FORWARD
    }

    /** Returns true the first time a cancellation for [sosIdHex] is seen (so it is propagated once). */
    @Synchronized
    fun cancel(sosIdHex: String, nowMs: Long): Boolean {
        prune(nowMs)
        return cancelled.put(sosIdHex, nowMs) == null
    }

    fun jitterMs(): Long = random.nextLong(config.sosRelayJitterMinMs, config.sosRelayJitterMaxMs + 1)

    private fun prune(nowMs: Long) {
        seen.entries.removeIf { nowMs - it.value > config.sosDiscoveryTtlMs }
        cancelled.entries.removeIf { nowMs - it.value > config.sosDiscoveryTtlMs * 2 }
        while (seen.size > config.maxSosSeenFrames) seen.remove(seen.keys.first())
        while (cancelled.size > config.maxSosSeenFrames) cancelled.remove(cancelled.keys.first())
    }
}
