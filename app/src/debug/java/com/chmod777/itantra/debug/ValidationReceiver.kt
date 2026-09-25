package com.chmod777.itantra.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.chmod777.itantra.crypto.Primitives
import com.chmod777.itantra.crypto.toHex
import com.chmod777.itantra.dtn.DtnEvent
import com.chmod777.itantra.identity.ContactQrCodec
import com.chmod777.itantra.identity.TrustedContact
import com.chmod777.itantra.metrics.BenchmarkParams
import com.chmod777.itantra.network.ItantraNetwork
import com.chmod777.itantra.protocol.SosCategory
import com.chmod777.itantra.service.EmergencyModeService
import com.chmod777.itantra.service.NetworkingRuntime
import com.chmod777.itantra.session.SessionFaultInjection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * DEBUG BUILDS ONLY. adb-driven control plane for physical two-phone validation.
 *
 * The laptop only issues commands here and reads results back. Application data
 * always travels phone ↔ phone over BLE; this receiver never forwards payloads.
 * Output: `files/validation/val.jsonl`, read with `run-as ... cat`.
 *
 * `adb shell am broadcast -n com.chmod777.itantra/.debug.ValidationReceiver --es cmd <cmd> [...]`
 */
class ValidationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ItantraNetwork.init(context)
        val cmd = intent.getStringExtra("cmd") ?: "state"
        // Work continues on the process scope (kept alive by the Emergency mode service),
        // so long benchmarks never hold the broadcast past its deadline.
        NetworkingRuntime.scope.launch {
            try {
                run(context.applicationContext, cmd, intent)
            } catch (e: Exception) {
                write(context, "error", mapOf("cmd" to cmd, "cause" to "${e.javaClass.simpleName}: ${e.message}"))
            }
        }
    }

    private suspend fun run(context: Context, cmd: String, intent: Intent) {
        when (cmd) {
            "arm" -> arm(context)
            "capsule" -> {
                val code = ContactQrCodec.encode(NetworkingRuntime.identity.value!!.signedCapsule())
                File(dir(context), "capsule.txt").writeText(code)
                write(context, "capsule", mapOf("fingerprint" to NetworkingRuntime.identity.value!!.fingerprint))
            }
            "import" -> {
                val capsule = ContactQrCodec.decodeAndVerify(intent.getStringExtra("code")!!)
                val name = intent.getStringExtra("name") ?: capsule.displayNameHint
                NetworkingRuntime.database.upsert(TrustedContact.fromVerifiedCapsule(capsule, name, System.currentTimeMillis()))
                write(context, "imported", mapOf("name" to name, "fingerprint" to capsule.fingerprint))
            }
            "send" -> send(context, intent)
            "bench" -> bench(context, intent)
            "state" -> state(context)
            "sweep" -> write(context, "sweep", mapOf("removed" to NetworkingRuntime.dtn.sweep()))
            "stop" -> {
                EmergencyModeService.stop(context)
                write(context, "stop_requested", emptyMap())
            }
            "drop_links" -> {
                val handles = NetworkingRuntime.session.value?.core?.sessionHandles().orEmpty()
                handles.forEach { it.close() }
                write(context, "links_dropped", mapOf("count" to handles.size))
            }
            "corrupt" -> {
                SessionFaultInjection.armCorruptNextFrame()
                write(context, "fault_armed", mapOf("kind" to "corrupt"))
            }
            "replay" -> {
                SessionFaultInjection.armReplayNextFrame()
                write(context, "fault_armed", mapOf("kind" to "replay"))
            }
            "sos_available" -> {
                NetworkingRuntime.setAvailableToHelp(intent.getBooleanExtra("on", true))
                write(context, "sos_available", mapOf("on" to NetworkingRuntime.availableToHelp.value))
            }
            "sos_start" -> {
                val sos = NetworkingRuntime.session.value!!.sos
                sos.startSos(SosCategory.valueOf(intent.getStringExtra("category") ?: "MEDICAL"), intent.getStringExtra("lang") ?: "en")
                write(context, "sos_started", mapOf("t_ns" to SystemClock.elapsedRealtimeNanos()))
            }
            "sos_accept" -> {
                val sos = NetworkingRuntime.session.value!!.sos
                val offer = sos.incomingOffers.value.lastOrNull() ?: error("no SOS offer")
                if (intent.getBooleanExtra("decline", false)) sos.decline(offer.sosIdHex) else sos.accept(offer.sosIdHex)
                write(context, "sos_answered", mapOf("decline" to intent.getBooleanExtra("decline", false)))
            }
            "sos_chat" -> {
                val sos = NetworkingRuntime.session.value!!.sos
                val id = sos.outgoing.value?.sosIdHex ?: sos.incomingOffers.value.lastOrNull()?.sosIdHex ?: error("no SOS")
                sos.sendChat(id, intent.getStringExtra("text") ?: "ping", "en")
                write(context, "sos_chat_sent", emptyMap())
            }
            "sos_cancel" -> {
                NetworkingRuntime.session.value?.sos?.cancelSos()
                write(context, "sos_cancelled", emptyMap())
            }
            else -> write(context, "unknown_cmd", mapOf("cmd" to cmd))
        }
    }

    /** Subscribes to DTN events so deliveries and state changes are timestamped on this phone's clock. */
    private fun arm(context: Context) {
        synchronized(Companion) {
            if (armed) {
                write(context, "armed", mapOf("already" to true))
                return
            }
            armed = true
        }
        NetworkingRuntime.scope.launch {
            NetworkingRuntime.dtn.events.collect { event ->
                val now = SystemClock.elapsedRealtimeNanos()
                when (event) {
                    is DtnEvent.MessageDelivered -> write(
                        context, "delivered",
                        mapOf(
                            "bundle" to event.message.bundleId.toHex(), "from" to event.from.localName,
                            "text_sha" to sha(event.message.text), "bytes" to event.message.text.toByteArray().size,
                            "tag" to event.message.text.substringBefore('|', "").take(24), "t_ns" to now,
                        ),
                    )
                    is DtnEvent.OutgoingStateChanged -> write(
                        context, "state_change", mapOf("bundle" to event.message.bundleId.toHex(), "state" to event.message.state.name, "t_ns" to now),
                    )
                    is DtnEvent.Rejected -> write(context, "dtn_rejected", mapOf("reason" to event.reason, "t_ns" to now))
                    is DtnEvent.BundleRemoved -> write(context, "bundle_removed", mapOf("key" to event.bundleKeyShort, "reason" to event.reason, "t_ns" to now))
                    else -> Unit
                }
            }
        }
        write(context, "armed", mapOf("already" to false))
    }

    /** Queues deterministic, tagged test messages. Text = `<tag>-<n>|` + filler to the requested size. */
    private suspend fun send(context: Context, intent: Intent) {
        val to = intent.getStringExtra("to")!!
        val contact = NetworkingRuntime.database.all().single { it.localName == to }
        val count = intent.getIntExtra("count", 1)
        val size = intent.getIntExtra("size", 48)
        val tag = intent.getStringExtra("tag") ?: "VAL"
        val gap = intent.getIntExtra("gap_ms", 0).toLong()
        repeat(count) { index ->
            val id = "$tag-${"%03d".format(index + 1)}"
            val head = "$id|"
            val text = head + "x".repeat((size - head.length).coerceAtLeast(1))
            val result = ItantraNetwork.queueTrustedMessage(contact.nodeId.toHex(), text, "en")
            write(
                context, "queued",
                mapOf(
                    "id" to id, "bundle" to result.getOrNull(), "ok" to result.isSuccess, "error" to result.exceptionOrNull()?.message,
                    "text_sha" to sha(text), "bytes" to text.toByteArray().size, "t_ns" to SystemClock.elapsedRealtimeNanos(),
                ),
            )
            if (gap > 0) delay(gap)
        }
    }

    private suspend fun bench(context: Context, intent: Intent) {
        val session = NetworkingRuntime.session.value ?: error("Emergency mode is off")
        val handle = session.core.sessionHandles().firstOrNull() ?: error("no secure session")
        val params = BenchmarkParams(
            trials = intent.getIntExtra("trials", 20),
            hops = intent.getIntExtra("hops", 1),
            payloadBytes = intent.getIntExtra("payload", 200),
            echoPayload = intent.getBooleanExtra("echo", true),
            timeoutMs = intent.getIntExtra("timeout_ms", 15_000).toLong(),
            gapMs = intent.getIntExtra("gap_ms", 200).toLong(),
        )
        val summary = session.benchmark.run(handle, params)
        write(
            context, "bench",
            mapOf(
                "label" to summary.label, "attempts" to summary.attempts, "successes" to summary.successes,
                "failures" to summary.failures.toString(), "rtt_min" to summary.rttMsMin, "rtt_med" to summary.rttMsMedian,
                "rtt_p90" to summary.rttMsP90, "rtt_max" to summary.rttMsMax, "csv" to summary.csvFile.absolutePath,
                "mtu" to handle.negotiatedMtu, "link" to handle.linkId,
            ),
        )
    }

    private fun state(context: Context) {
        val session = NetworkingRuntime.session.value
        write(
            context, "state",
            mapOf(
                "emergency" to NetworkingRuntime.emergencyState.value.toString(),
                "status" to session?.ble?.status?.value?.toString(),
                "peers" to session?.ble?.peers?.value?.joinToString(";") { "${it.shortIdHex.take(8)}:${it.rssi}:linked=${it.linked}" },
                "sessions" to session?.core?.sessions?.value?.joinToString(";") { "${it.linkId}:${it.transportKind.label}:mtu=${it.negotiatedMtu}:hs=${"%.1f".format(it.handshakeMs)}" },
                "outgoing" to NetworkingRuntime.dtn.outgoingMessages().groupingBy { it.state.name }.eachCount().toString(),
                "delivered" to NetworkingRuntime.dtn.deliveredMessages().size,
                "bundles" to NetworkingRuntime.dtn.storedBundles().joinToString(";") { "${it.storageKey.take(8)}:${it.origin}:t=${it.copyTokens}" },
                "contacts" to NetworkingRuntime.database.all().joinToString(";") { it.localName },
                "sos_out" to session?.sos?.outgoing?.value?.let { "${it.phase}:${it.trust}:sas=${it.shortAuthString}:chat=${it.chat.size}:declined=${it.declinedCount}" },
                "sos_in" to session?.sos?.incomingOffers?.value?.joinToString(";") { "${it.phase}:${it.trust}:sas=${it.shortAuthString}:chat=${it.chat.size}" },
            ),
        )
    }

    companion object {
        @Volatile private var armed = false

        private fun dir(context: Context) = File(context.filesDir, "validation").also { it.mkdirs() }

        private fun sha(text: String) = Primitives.sha256(text.toByteArray()).copyOf(8).toHex()

        @Synchronized
        fun write(context: Context, event: String, fields: Map<String, Any?>) {
            val line = StringBuilder("{\"ev\":\"").append(event).append("\",\"wall_ms\":").append(System.currentTimeMillis())
            fields.forEach { (k, v) ->
                line.append(",\"").append(k).append("\":")
                when (v) {
                    null -> line.append("null")
                    is Number, is Boolean -> line.append(v)
                    else -> line.append('"').append(v.toString().replace("\\", "\\\\").replace("\"", "\\\"")).append('"')
                }
            }
            File(dir(context), "val.jsonl").appendText(line.append("}\n").toString())
        }
    }
}
