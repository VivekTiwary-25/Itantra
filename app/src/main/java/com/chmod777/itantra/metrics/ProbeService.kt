package com.chmod777.itantra.metrics

import com.chmod777.itantra.crypto.Primitives
import com.chmod777.itantra.crypto.toHex
import com.chmod777.itantra.dtn.DtnClock
import com.chmod777.itantra.protocol.FrameType
import com.chmod777.itantra.protocol.ProbeEchoFrame
import com.chmod777.itantra.protocol.ProbeFrame
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.protocol.ProtocolFrame
import com.chmod777.itantra.session.SessionFeature
import com.chmod777.itantra.session.SessionHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * One benchmark probe outcome. A probe is successful only when its echo returns
 * through the requested number of hops before the timeout (execution spec §5.3).
 */
data class ProbeResult(
    val probeIdHex: String,
    val success: Boolean,
    val failureCause: String?,
    val hopsRequested: Int,
    val hopsTraversed: Int,
    val payloadBytes: Int,
    val echoPayload: Boolean,
    /** Originator clock only: send start → echo decoded. */
    val rttNs: Long?,
    /** Each relay's own clock: probe decoded → forwarded frame handed to its next link. */
    val relayProcessingNs: List<Long>,
    val firstHopLinkId: String,
    val firstHopTransport: String,
)

/**
 * PROBE / PROBE_ECHO handling (IMPLEMENTATION_NOTES benchmark section). Relays
 * forward to one other live session; the last hop echoes along the reverse path.
 * Diagnostic only: probes never touch the DTN store, STT or TTS.
 */
class ProbeService(
    private val clock: DtnClock,
    private val metrics: MetricsSink,
    private val config: ProtocolConfig,
    private val sessions: () -> List<SessionHandle>,
) : SessionFeature {
    override val frameTypes = setOf(FrameType.PROBE, FrameType.PROBE_ECHO)

    private class Pending(val deferred: CompletableDeferred<ProbeEchoFrame>)
    private class Route(val sourceSessionKey: String, val forwardProcessingNs: Long, val createdNs: Long)

    private val pending = ConcurrentHashMap<String, Pending>()
    private val routes = ConcurrentHashMap<String, Route>()

    suspend fun probe(
        firstHop: SessionHandle,
        hops: Int,
        payloadBytes: Int,
        echoPayload: Boolean,
        timeoutMs: Long = config.probeTimeoutMs,
    ): ProbeResult {
        require(hops in 1..ProbeFrame.MAX_HOPS && payloadBytes in 0..config.maxProbePayloadBytes)
        val probeId = Primitives.randomBytes(ProbeFrame.PROBE_ID_BYTES)
        val idHex = probeId.toHex()
        val entry = Pending(CompletableDeferred())
        pending[idHex] = entry
        val frame = ProbeFrame(probeId, hopsRemaining = hops - 1, hopsTraversed = 0, echoPayload = echoPayload, payload = Primitives.randomBytes(payloadBytes))
        val encodedBytes = frame.encode().size
        val start = clock.monotonicNs()
        fun result(success: Boolean, cause: String?, rtt: Long?, echo: ProbeEchoFrame?) = ProbeResult(
            idHex, success, cause, hops, echo?.hopsTraversed ?: 0, payloadBytes, echoPayload, rtt,
            echo?.relayProcessingNs ?: emptyList(), firstHop.linkId, firstHop.transportKind.label,
        )
        val outcome = try {
            firstHop.sendFrame(frame)
            val sentNs = clock.monotonicNs()
            metrics.record("probe_tx", mapOf("probe" to idHex, "hops" to hops, "payload_bytes" to payloadBytes, "frame_bytes" to encodedBytes, "send_ns" to sentNs - start, "link" to firstHop.linkId))
            val echo = withTimeout(timeoutMs) { entry.deferred.await() }
            val rtt = clock.monotonicNs() - start
            if (echo.hopsTraversed == hops) result(true, null, rtt, echo) else result(false, "route_short:${echo.hopsTraversed}/$hops", rtt, echo)
        } catch (_: TimeoutCancellationException) {
            result(false, "timeout", null, null)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            result(false, "send_failed:${e.javaClass.simpleName}", null, null)
        } finally {
            pending.remove(idHex)
        }
        metrics.record(
            "probe_result",
            mapOf(
                "probe" to idHex, "success" to outcome.success, "cause" to outcome.failureCause, "rtt_ns" to outcome.rttNs,
                "hops" to hops, "hops_traversed" to outcome.hopsTraversed, "relay_proc_ns" to outcome.relayProcessingNs.joinToString(";"),
                "payload_bytes" to payloadBytes,
            ),
        )
        return outcome
    }

    override suspend fun onFrame(handle: SessionHandle, frame: ProtocolFrame) {
        val receivedNs = clock.monotonicNs()
        when (frame) {
            is ProbeFrame -> handleProbe(handle, frame, receivedNs)
            is ProbeEchoFrame -> handleEcho(frame)
            else -> Unit
        }
    }

    private suspend fun handleProbe(handle: SessionHandle, frame: ProbeFrame, receivedNs: Long) {
        val idHex = frame.probeId.toHex()
        val traversed = frame.hopsTraversed + 1
        if (frame.hopsRemaining == 0) {
            handle.sendFrame(ProbeEchoFrame(frame.probeId, traversed, emptyList(), if (frame.echoPayload) frame.payload else ByteArray(0)))
            metrics.record("probe_echo_tx", mapOf("probe" to idHex, "hops_traversed" to traversed))
            return
        }
        val next = sessions().firstOrNull { it.peerSessionKey != handle.peerSessionKey }
        if (next == null) {
            // No onward peer: echo early so the originator records a short route instead of a silent timeout.
            handle.sendFrame(ProbeEchoFrame(frame.probeId, traversed, emptyList(), ByteArray(0)))
            metrics.record("probe_no_route", mapOf("probe" to idHex))
            return
        }
        pruneRoutes(receivedNs)
        val forwarded = ProbeFrame(frame.probeId, frame.hopsRemaining - 1, traversed, frame.echoPayload, frame.payload)
        val handoffNs = clock.monotonicNs()
        routes[idHex] = Route(handle.peerSessionKey, handoffNs - receivedNs, receivedNs)
        next.sendFrame(forwarded)
        metrics.record(
            "probe_relay_forward",
            mapOf("probe" to idHex, "proc_ns" to handoffNs - receivedNs, "send_done_ns" to clock.monotonicNs() - receivedNs, "in_link" to handle.linkId, "out_link" to next.linkId),
        )
    }

    private suspend fun handleEcho(frame: ProbeEchoFrame) {
        val idHex = frame.probeId.toHex()
        pending[idHex]?.let {
            it.deferred.complete(frame)
            return
        }
        val route = routes.remove(idHex) ?: return
        val back = sessions().firstOrNull { it.peerSessionKey == route.sourceSessionKey } ?: return
        back.sendFrame(frame.withRelayProcessing(route.forwardProcessingNs))
        metrics.record("probe_echo_relay", mapOf("probe" to idHex))
    }

    private fun pruneRoutes(nowNs: Long) {
        val maxAgeNs = config.probeTimeoutMs * 1_000_000
        routes.entries.removeIf { nowNs - it.value.createdNs > maxAgeNs }
        if (routes.size > 1_024) routes.clear()
    }
}
