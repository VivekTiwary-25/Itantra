package com.chmod777.itantra.metrics

import com.chmod777.itantra.protocol.ProbeFrame
import com.chmod777.itantra.session.SessionHandle
import com.chmod777.itantra.transport.ble.FragmentCodec
import com.chmod777.itantra.transport.ble.GattProfile
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

data class BenchmarkParams(
    val trials: Int,
    val hops: Int,
    val payloadBytes: Int,
    val echoPayload: Boolean,
    val timeoutMs: Long,
    val gapMs: Long = 250,
)

/**
 * Benchmark summary. Every number states its measurement; failed trials are
 * counted, never excluded (execution spec §5.3–§5.4).
 */
data class BenchmarkSummary(
    val label: String,
    val attempts: Int,
    val successes: Int,
    val failures: Map<String, Int>,
    val rttMsMin: Double?,
    val rttMsMedian: Double?,
    val rttMsP90: Double?,
    val rttMsMax: Double?,
    val relayProcMsMedian: Double?,
    val goodputBytesPerSecMedian: Double?,
    val csvFile: File,
) {
    fun describe(): String = buildString {
        appendLine(label)
        appendLine("attempts=$attempts successes=$successes failures=$failures")
        appendLine("RTT ms (originator monotonic clock): min=${fmt(rttMsMin)} median=${fmt(rttMsMedian)} p90=${fmt(rttMsP90)} max=${fmt(rttMsMax)}")
        appendLine("relay processing ms (relay's own clock, decode→hand-off): median=${fmt(relayProcMsMedian)}")
        appendLine("payload goodput B/s = payload bytes / RTT (lower bound; successes only): median=${fmt(goodputBytesPerSecMedian)}")
        append("raw: ${csvFile.absolutePath}")
    }

    private fun fmt(v: Double?) = v?.let { String.format(Locale.US, "%.1f", it) } ?: "n/a"
}

/**
 * Runs repeated probes and writes one CSV row per trial. Transport labels come
 * from the live session, so RFCOMM is never reported as BLE GATT.
 */
class BenchmarkRunner(private val probes: ProbeService, private val directory: File, private val deviceLabel: String, private val runId: String) {

    suspend fun run(firstHop: SessionHandle, params: BenchmarkParams): BenchmarkSummary {
        directory.mkdirs()
        val csv = File(directory, "probes_${runId}_${System.currentTimeMillis()}.csv")
        val mtu = firstHop.negotiatedMtu
        val results = ArrayList<ProbeResult>()
        csv.bufferedWriter().use { out ->
            out.write(CSV_HEADER)
            out.newLine()
            repeat(params.trials) { attempt ->
                val result = probes.probe(firstHop, params.hops, params.payloadBytes, params.echoPayload, params.timeoutMs)
                results += result
                out.write(row(attempt, result, mtu))
                out.newLine()
                out.flush()
                delay(params.gapMs)
            }
        }
        val ok = results.filter { it.success }
        val rtts = ok.mapNotNull { it.rttNs?.div(1e6) }.sorted()
        val proc = ok.flatMap { it.relayProcessingNs }.map { it / 1e6 }.sorted()
        val goodput = ok.mapNotNull { r -> r.rttNs?.let { r.payloadBytes / (it / 1e9) } }.sorted()
        return BenchmarkSummary(
            label = "${firstHop.transportKind.label} probe, ${params.hops} hop(s), ${params.payloadBytes} B payload, echo_payload=${params.echoPayload}, device=$deviceLabel",
            attempts = results.size,
            successes = ok.size,
            failures = results.filterNot { it.success }.groupingBy { it.failureCause ?: "unknown" }.eachCount(),
            rttMsMin = rtts.firstOrNull(),
            rttMsMedian = percentile(rtts, 0.5),
            rttMsP90 = percentile(rtts, 0.9),
            rttMsMax = rtts.lastOrNull(),
            relayProcMsMedian = percentile(proc, 0.5),
            goodputBytesPerSecMedian = percentile(goodput, 0.5),
            csvFile = csv,
        )
    }

    private fun row(attempt: Int, r: ProbeResult, mtu: Int?): String {
        // First-hop overhead accounting at the negotiated MTU (BLE only).
        val frameBytes = ProbeFrame(ByteArray(ProbeFrame.PROBE_ID_BYTES), r.hopsRequested - 1, 0, r.echoPayload, ByteArray(r.payloadBytes)).encode().size
        val noiseBytes = frameBytes + NOISE_TAG_BYTES
        val linkFrameBytes = noiseBytes + 1
        val perFragment = mtu?.let { GattProfile.fragmentBudget(it) - FragmentCodec.HEADER_BYTES }
        val fragments = perFragment?.let { (linkFrameBytes + it - 1) / it }
        val fragmentBytes = fragments?.let { linkFrameBytes + it * FragmentCodec.HEADER_BYTES }
        return listOf(
            runId, deviceLabel, attempt, r.probeIdHex, r.firstHopTransport, r.firstHopLinkId, mtu ?: "", r.payloadBytes, frameBytes, noiseBytes,
            linkFrameBytes, fragments ?: "", fragmentBytes ?: "", r.hopsRequested, r.hopsTraversed, r.echoPayload,
            if (r.success) "success" else "failure", r.failureCause ?: "",
            r.rttNs?.let { String.format(Locale.US, "%.3f", it / 1e6) } ?: "",
            r.relayProcessingNs.joinToString(";") { String.format(Locale.US, "%.3f", it / 1e6) },
        ).joinToString(",")
    }

    private fun percentile(sorted: List<Double>, p: Double): Double? =
        if (sorted.isEmpty()) null else sorted[((sorted.size - 1) * p).toInt()]

    companion object {
        /** ChaCha20-Poly1305 tag added by each Noise transport message. */
        private const val NOISE_TAG_BYTES = 16
        const val CSV_HEADER = "run_id,device,attempt,probe_id,transport,first_hop_link,negotiated_mtu,payload_bytes,protocol_frame_bytes," +
            "noise_bytes,link_frame_bytes,first_hop_fragments,first_hop_fragment_bytes,hops_requested,hops_traversed,echo_payload," +
            "result,failure_cause,rtt_ms,relay_processing_ms"
    }
}
