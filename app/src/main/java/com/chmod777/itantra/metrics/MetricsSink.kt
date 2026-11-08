package com.chmod777.itantra.metrics

/**
 * Benchmark/debug event sink (spec §54). Callers must never pass plaintext,
 * contact names, keys, pair secrets, deletion secrets or conversation content.
 */
fun interface MetricsSink {
    fun record(event: String, fields: Map<String, Any?>)

    companion object {
        val NONE = MetricsSink { _, _ -> }
    }
}
