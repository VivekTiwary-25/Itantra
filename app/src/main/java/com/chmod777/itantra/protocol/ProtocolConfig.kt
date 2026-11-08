package com.chmod777.itantra.protocol

/**
 * Every tunable networking parameter in one place (spec §22, §39, §43, §52).
 *
 * These are starting engineering values, not universal truths. Tests construct
 * modified copies instead of editing constants in place.
 */
data class ProtocolConfig(
    // --- Protocol identity ---
    val protocolMajor: Int = 1,

    // --- Link / GATT (spec §7, audit #7, §5 of IMPLEMENTATION_NOTES) ---
    val maxLinkFrameBytes: Int = 32 * 1024,
    val maxFragmentsPerFrame: Int = 4096,
    val maxReassemblyBytesPerPeer: Int = 64 * 1024,
    val maxConcurrentReassembliesPerPeer: Int = 2,
    val reassemblyTimeoutMs: Long = 10_000,
    val requestedAttMtu: Int = 517,
    val gattOperationTimeoutMs: Long = 5_000,
    val gattConnectTimeoutMs: Long = 12_000,
    val linkSetupTimeoutMs: Long = 20_000,
    val gattRetryBackoffMs: List<Long> = listOf(500, 1_500, 4_000),
    val maxLinks: Int = 6,
    val roleWaitMs: Long = 6_000,
    /** Two links to one peer that became READY further apart than this are not a collision: the older is stale. */
    val duplicateLinkWindowMs: Long = 10_000,
    val connectAttemptsPerPeerPerMinute: Int = 4,
    val advertisedIdRotationMs: Long = 15 * 60_000,
    val peerStaleMs: Long = 30_000,
    val minScanStartIntervalMs: Long = 6_000,
    val maxScanStartsPer30s: Int = 4,

    // --- Noise hop security (spec §9, audit #6) ---
    val noiseHandshakeTimeoutMs: Long = 10_000,
    val noiseHandshakesPerPeerPerMinute: Int = 6,
    val maxMalformedFramesPerSession: Int = 3,

    // --- Private messages (spec §22, §51, audit E) ---
    val privateTextMaxBytes: Int = 16 * 1024,
    val normalPrivateCopyBudget: Int = 4,
    val urgentPrivateCopyBudget: Int = 8,
    val normalPrivateLifetimeMs: Long = 24 * 60 * 60_000L,
    val urgentPrivateLifetimeMs: Long = 6 * 60 * 60_000L,
    val privateHopGuard: Int = 16,
    val paddingBucketsBytes: List<Int> = listOf(256, 1024, 4096, 16 * 1024, 24 * 1024),
    val maxCiphertextBytes: Int = 24 * 1024 + 64,

    // --- DTN store / inventory (spec §24, §27, §31, §52) ---
    val inventoryPageSize: Int = 32,
    val maxInventoryPages: Int = 16,
    val maxWantsPerEncounterRound: Int = 64,
    /** Per session, per sliding minute (not lifetime caps). */
    val maxDestinationClaimsPerSession: Int = 16,
    val maxBytesAcceptedPerSession: Long = 2L * 1024 * 1024,
    val maxBundlesAcceptedPerSession: Int = 64,
    val maxStoredBundles: Int = 512,
    val maxStoredBytes: Long = 8L * 1024 * 1024,
    val maxStoredBytesPerPeerSession: Long = 1L * 1024 * 1024,
    val seenGraceMs: Long = 60 * 60_000L,
    val agePersistIntervalMs: Long = 60_000,
    val expirySweepIntervalMs: Long = 30_000,
    val pendingSplitTimeoutMs: Long = 10 * 60_000L,
    val minInventoryRoundIntervalMs: Long = 5_000,
    /** Anti-entropy: each live session re-runs inventory at least this often, so refused or missed transfers retry. */
    val periodicInventoryRoundMs: Long = 30_000,

    // --- SOS (spec §35, §39, §43, audit C/G) ---
    val sosRssiSampleWindowMs: Long = 2_500,
    val sosRssiStrongDbm: Int = -65,
    val sosRssiMediumDbm: Int = -80,
    val sosWave0Candidates: Int = 3,
    val sosWave1Candidates: Int = 5,
    val sosWave1StartMs: Long = 10_000,
    val sosWave2StartMs: Long = 25_000,
    val sosWave3StartMs: Long = 60_000,
    val sosWave2HopLimit: Int = 2,
    val sosWave3HopLimit: Int = 3,
    val sosDiscoveryTtlMs: Long = 2 * 60_000L,
    val activeSosIdleTimeoutMs: Long = 30 * 60_000L,
    val sosRelayJitterMinMs: Long = 50,
    val sosRelayJitterMaxMs: Long = 500,
    val maxSosRequestsPerPeerPerMinute: Int = 6,
    val maxSosRelayFramesPerMinute: Int = 30,
    val maxSosSeenFrames: Int = 1024,
    val sosChatTextMaxBytes: Int = 2 * 1024,

    // --- Benchmark ---
    val probeTimeoutMs: Long = 15_000,
    val maxProbePayloadBytes: Int = 16 * 1024,
) {
    fun copyBudget(priority: Priority): Int = when (priority) {
        Priority.NORMAL -> normalPrivateCopyBudget
        Priority.URGENT -> urgentPrivateCopyBudget
    }

    fun lifetimeMs(priority: Priority): Long = when (priority) {
        Priority.NORMAL -> normalPrivateLifetimeMs
        Priority.URGENT -> urgentPrivateLifetimeMs
    }

    companion object {
        val DEFAULT = ProtocolConfig()
    }
}

enum class Priority(val wire: Int) {
    NORMAL(0),
    URGENT(1);

    companion object {
        fun fromWire(value: Long): Priority? = entries.firstOrNull { it.wire.toLong() == value }
    }
}
