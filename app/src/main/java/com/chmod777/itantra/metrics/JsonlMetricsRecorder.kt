package com.chmod777.itantra.metrics

import com.chmod777.itantra.dtn.DtnClock
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.Executors

/**
 * Raw, auditable event log (execution spec §5). One JSON object per line:
 * `{"t_ns":<monotonic>,"wall_ms":<wall>,"dev":<device label>,"run":<run id>,"ev":<event>, ...fields}`.
 *
 * `t_ns` is this phone's monotonic clock and must never be subtracted across phones.
 * Callers must not pass plaintext, names, keys or secrets (spec §54).
 */
class JsonlMetricsRecorder(
    directory: File,
    val deviceLabel: String,
    val runId: String,
    private val clock: DtnClock,
) : MetricsSink {
    val file: File = File(directory, "events_$runId.jsonl")
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "itantra-metrics").apply { isDaemon = true } }
    private var writer: BufferedWriter? = null
    private var linesSinceFlush = 0

    init {
        directory.mkdirs()
    }

    override fun record(event: String, fields: Map<String, Any?>) {
        val tNs = clock.monotonicNs()
        val wall = clock.wallMs()
        executor.execute {
            val out = writer ?: BufferedWriter(FileWriter(file, true)).also { writer = it }
            val line = StringBuilder(128)
            line.append("{\"t_ns\":").append(tNs)
                .append(",\"wall_ms\":").append(wall)
                .append(",\"dev\":").appendJson(deviceLabel)
                .append(",\"run\":").appendJson(runId)
                .append(",\"ev\":").appendJson(event)
            fields.forEach { (k, v) -> line.append(',').appendJson(k).append(':').appendJsonValue(v) }
            line.append('}')
            out.write(line.toString())
            out.newLine()
            if (++linesSinceFlush >= 20 || event.startsWith("probe_result") || event.startsWith("bench_")) {
                out.flush()
                linesSinceFlush = 0
            }
        }
    }

    fun flush() {
        executor.submit { writer?.flush() }.get()
    }

    fun close() {
        executor.submit {
            writer?.flush()
            writer?.close()
            writer = null
        }.get()
        executor.shutdown()
    }

    companion object {
        internal fun StringBuilder.appendJson(value: String): StringBuilder {
            append('"')
            value.forEach { ch ->
                when {
                    ch == '"' -> append("\\\"")
                    ch == '\\' -> append("\\\\")
                    ch == '\n' -> append("\\n")
                    ch == '\r' -> append("\\r")
                    ch == '\t' -> append("\\t")
                    ch < ' ' -> append("\\u%04x".format(ch.code))
                    else -> append(ch)
                }
            }
            return append('"')
        }

        internal fun StringBuilder.appendJsonValue(value: Any?): StringBuilder = when (value) {
            null -> append("null")
            is Boolean -> append(value)
            is Int, is Long, is Short, is Byte -> append(value.toString())
            is Double -> if (value.isFinite()) append(value) else append("null")
            is Float -> if (value.isFinite()) append(value) else append("null")
            else -> appendJson(value.toString())
        }
    }
}
