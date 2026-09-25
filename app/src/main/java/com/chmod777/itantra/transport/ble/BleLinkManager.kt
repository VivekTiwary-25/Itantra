package com.chmod777.itantra.transport.ble

import android.content.Context
import com.chmod777.itantra.crypto.Primitives
import com.chmod777.itantra.crypto.toHex
import com.chmod777.itantra.dtn.DtnClock
import com.chmod777.itantra.metrics.MetricsSink
import com.chmod777.itantra.protocol.LinkFrame
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.transport.ConnectionRolePolicy
import com.chmod777.itantra.transport.LinkClosedException
import com.chmod777.itantra.transport.PeerLink
import com.chmod777.itantra.transport.ScanRestartLimiter
import com.chmod777.itantra.transport.SlidingWindowLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** UI/metrics-safe view of a nearby phone: rotating short ID and signal only, never a MAC. */
data class ObservedPeer(
    val shortIdHex: String,
    val rssi: Int,
    val sosActive: Boolean,
    val availableToHelp: Boolean,
    val lastSeenAgoMs: Long,
    val linked: Boolean,
)

data class DiscoveryStatus(
    val advertising: String,
    val scanning: Boolean,
    val scanError: String?,
    val gattServer: Boolean,
    val localShortIdHex: String,
    val links: Int,
)

/**
 * BLE PeerLink manager (spec §4, §6, §8): advertise + scan + GATT server +
 * deterministic client connections. Hands READY links to [onLinkReady]; nothing
 * above this class sees Android Bluetooth objects or addresses.
 */
class BleLinkManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val config: ProtocolConfig,
    private val clock: DtnClock,
    private val metrics: MetricsSink,
    private val onLinkReady: (PeerLink) -> Unit,
) {
    private class Observation(
        @Volatile var hit: ScanHit,
        val firstSeenMs: Long,
        @Volatile var lastSeenMs: Long,
        val samples: ArrayDeque<Int> = ArrayDeque(),
    )

    private val policy = ConnectionRolePolicy(config)
    private val scanLimiter = ScanRestartLimiter(config)
    private val attemptLimiter = SlidingWindowLimiter(60_000, config.connectAttemptsPerPeerPerMinute)
    private val observations = ConcurrentHashMap<String, Observation>()
    private val links = ConcurrentHashMap<String, BleGattLinkCore>()
    private val linkedAddresses = ConcurrentHashMap<BleGattLinkCore, String>()
    private val connecting = ConcurrentHashMap.newKeySet<String>()
    private val prioritized = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var localShortId = Primitives.randomBytes(LinkFrame.SHORT_ID_BYTES)
    @Volatile private var lastRotationMs = clock.elapsedMs()
    @Volatile private var flags = 0

    private val advertiser = BleAdvertiser(context, metrics)
    private val scanner = BleScanner(context, metrics)
    private val server = BleGattServer(context, scope, config, clock, metrics, { localShortId }) { link ->
        register(link, link.deviceAddress)
    }
    private var tickJob: Job? = null
    @Volatile private var serverUp = false

    private val mutablePeers = MutableStateFlow<List<ObservedPeer>>(emptyList())
    val peers: StateFlow<List<ObservedPeer>> = mutablePeers
    private val mutableStatus = MutableStateFlow(DiscoveryStatus("stopped", false, null, false, "", 0))
    val status: StateFlow<DiscoveryStatus> = mutableStatus

    suspend fun start() {
        serverUp = server.start()
        if (!serverUp) metrics.record("gatt_server_failed", emptyMap())
        startAdvertising()
        startScan()
        tickJob = scope.launch {
            while (isActive) {
                tick()
                delay(1_000)
            }
        }
    }

    fun stop() {
        tickJob?.cancel()
        scanner.stop()
        advertiser.stop()
        links.values.forEach { link -> scope.launch { link.close() } }
        links.clear()
        linkedAddresses.clear()
        server.stop()
        BleGattClientLink.closeAllLingering()
        publishStatus()
    }

    /** Sets SOS-active / available-to-help capability flags and re-advertises. */
    fun setFlags(sosActive: Boolean, availableToHelp: Boolean) {
        val newFlags = (if (sosActive) AdvertisementPayload.FLAG_SOS_ACTIVE else 0) or
            (if (availableToHelp) AdvertisementPayload.FLAG_AVAILABLE_TO_HELP else 0)
        if (newFlags == flags) return
        flags = newFlags
        startAdvertising()
    }

    fun prioritize(shortIdHexes: List<String>) {
        prioritized.clear()
        prioritized.addAll(shortIdHexes)
    }

    fun rssiSamples(shortIdHex: String): List<Int> = observations[shortIdHex]?.let { synchronized(it.samples) { it.samples.toList() } } ?: emptyList()

    fun helpersInRange(): List<String> {
        val now = clock.elapsedMs()
        return observations.filter { (_, o) -> o.hit.payload.availableToHelp && now - o.lastSeenMs < config.peerStaleMs }.keys.toList()
    }

    private fun startAdvertising() {
        advertiser.start(AdvertisementPayload(AdvertisementPayload.VERSION, flags, localShortId)) { publishStatus() }
    }

    private fun startScan() {
        if (!scanLimiter.tryAcquire(clock.elapsedMs())) {
            metrics.record("scan_start_deferred", emptyMap())
            return
        }
        scanner.start(onHit = ::onHit, onFailure = { publishStatus() })
        publishStatus()
    }

    private fun onHit(hit: ScanHit) {
        val id = hit.payload.shortId.toHex()
        if (hit.payload.shortId.contentEquals(localShortId)) return
        val now = clock.elapsedMs()
        val observation = observations.compute(id) { _, existing ->
            val o = existing ?: Observation(hit, now, now).also {
                metrics.record("peer_discovered", mapOf("peer" to id.take(8), "rssi" to hit.rssi))
            }
            o.hit = hit
            o.lastSeenMs = now
            o
        }!!
        synchronized(observation.samples) {
            observation.samples.addLast(hit.rssi)
            while (observation.samples.size > 8) observation.samples.removeFirst()
        }
    }

    private fun tick() {
        val now = clock.elapsedMs()
        observations.entries.removeIf { now - it.value.lastSeenMs > config.peerStaleMs * 4 }
        server.sweep()
        links.values.forEach { it.sweepReassembly() }
        if (!scanner.scanning) startScan()
        maybeRotateShortId(now)
        val candidates = observations.entries
            .filter { now - it.value.lastSeenMs < config.peerStaleMs }
            .sortedByDescending { it.key in prioritized }
        for ((id, observation) in candidates) {
            if (id in connecting) continue
            val address = observation.hit.device.address
            val alreadyLinked = links.containsKey(id) || linkedAddresses.containsValue(address)
            val decision = policy.decide(
                localShortId = localShortId,
                peerShortId = observation.hit.payload.shortId,
                firstSeenMs = observation.firstSeenMs,
                nowMs = now,
                alreadyLinked = alreadyLinked,
                activeLinks = links.size + connecting.size,
                attemptsInLastMinute = attemptLimiter.count(id, now),
            )
            if (decision == ConnectionRolePolicy.Decision.INITIATE) connect(id, observation)
        }
        publishPeers(now)
        publishStatus()
    }

    private fun connect(id: String, observation: Observation) {
        if (!attemptLimiter.tryAcquire(id, clock.elapsedMs())) return
        connecting += id
        val link = BleGattClientLink(context, observation.hit.device, observation.hit.payload.shortId, localShortId, config, clock, metrics)
        metrics.record("connect_attempt", mapOf("peer" to id.take(8), "link" to link.linkId))
        scope.launch {
            try {
                link.connect()
                register(link, observation.hit.device.address)
            } catch (e: LinkClosedException) {
                metrics.record("connect_failed", mapOf("peer" to id.take(8), "cause" to e.message))
            } finally {
                connecting -= id
            }
        }
    }

    /**
     * Collision handling (spec §8): if both phones connected to each other, both keep
     * the link whose initiator has the smaller short ID and close the other.
     */
    private fun register(link: BleGattLinkCore, address: String?) {
        val peerId = link.peerSessionId.toHex()
        val existing = links[peerId]
        if (existing != null && existing.state == PeerLink.State.READY) {
            val apartMs = (link.readyAtNs - existing.readyAtNs) / 1_000_000
            val keepNewer = policy.keepNewerDuplicate(localShortId, link.peerSessionId, existing.role, existing.readyAtNs, link.role, link.readyAtNs)
            val (keep, drop) = if (keepNewer) link to existing else existing to link
            metrics.record("link_duplicate_resolved", mapOf("kept" to keep.linkId, "dropped" to drop.linkId, "apart_ms" to apartMs))
            drop.closeKeepingConnection()
            if (keep === existing) return
        }
        links[peerId] = link
        address?.let { linkedAddresses[link] = it }
        onLinkReady(link)
        scope.launch {
            link.stateFlow.first { it == PeerLink.State.CLOSED || it == PeerLink.State.FAILED }
            links.remove(peerId, link)
            linkedAddresses.remove(link)
            publishStatus()
        }
        publishStatus()
    }

    /** Rotates the advertised ID (audit #7), only when no link is being set up or held. */
    private fun maybeRotateShortId(now: Long) {
        if (now - lastRotationMs < config.advertisedIdRotationMs) return
        if (connecting.isNotEmpty() || links.isNotEmpty() || server.activeLinkCount > 0) return
        localShortId = Primitives.randomBytes(LinkFrame.SHORT_ID_BYTES)
        lastRotationMs = now
        metrics.record("short_id_rotated", emptyMap())
        startAdvertising()
    }

    private fun publishPeers(now: Long) {
        mutablePeers.value = observations.map { (id, o) ->
            ObservedPeer(
                shortIdHex = id,
                rssi = o.hit.rssi,
                sosActive = o.hit.payload.sosActive,
                availableToHelp = o.hit.payload.availableToHelp,
                lastSeenAgoMs = now - o.lastSeenMs,
                linked = links.containsKey(id),
            )
        }.sortedByDescending { it.rssi }
    }

    private fun publishStatus() {
        mutableStatus.value = DiscoveryStatus(
            advertising = when (val s = advertiser.status) {
                BleAdvertiser.Status.Advertising -> "advertising"
                BleAdvertiser.Status.Starting -> "starting"
                BleAdvertiser.Status.Stopped -> "stopped"
                is BleAdvertiser.Status.Failed -> "failed: ${s.reason}"
            },
            scanning = scanner.scanning,
            scanError = scanner.lastError,
            gattServer = serverUp,
            localShortIdHex = localShortId.toHex(),
            links = links.size,
        )
    }
}
