package com.chmod777.itantra.transport.ble

import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.protocol.cbor.MalformedInputException

/**
 * GATT fragment layout (spec §7, IMPLEMENTATION_NOTES §5):
 *
 * `version(1)=1 | connection_session_id(4) | frame_id(u16) | fragment_index(u16) | fragment_count(u16) | payload`
 *
 * The header is 11 bytes, so at the default ATT MTU (23 → 20-byte attribute value)
 * each fragment carries 9 payload bytes.
 */
class Fragment(
    val connectionSessionId: Int,
    val frameId: Int,
    val index: Int,
    val count: Int,
    val payload: ByteArray,
)

object FragmentCodec {
    const val VERSION = 1
    const val HEADER_BYTES = 11

    /** Splits one complete link frame into fragments no larger than [maxFragmentBytes]. */
    fun fragment(
        frame: ByteArray,
        connectionSessionId: Int,
        frameId: Int,
        maxFragmentBytes: Int,
        config: ProtocolConfig,
    ): List<ByteArray> {
        require(frame.isNotEmpty()) { "frames are never empty" }
        require(frame.size <= config.maxLinkFrameBytes) { "frame ${frame.size} B exceeds ${config.maxLinkFrameBytes} B" }
        val payloadPerFragment = maxFragmentBytes - HEADER_BYTES
        require(payloadPerFragment > 0) { "fragment size $maxFragmentBytes leaves no room for payload" }
        val count = (frame.size + payloadPerFragment - 1) / payloadPerFragment
        require(count <= config.maxFragmentsPerFrame) { "frame needs $count fragments (> ${config.maxFragmentsPerFrame})" }
        return List(count) { index ->
            val start = index * payloadPerFragment
            val end = minOf(frame.size, start + payloadPerFragment)
            val out = ByteArray(HEADER_BYTES + (end - start))
            out[0] = VERSION.toByte()
            writeInt(out, 1, connectionSessionId)
            writeU16(out, 5, frameId and 0xFFFF)
            writeU16(out, 7, index)
            writeU16(out, 9, count)
            System.arraycopy(frame, start, out, HEADER_BYTES, end - start)
            out
        }
    }

    @Throws(MalformedInputException::class)
    fun parse(bytes: ByteArray): Fragment {
        if (bytes.size <= HEADER_BYTES) throw MalformedInputException("fragment shorter than header+1")
        if (bytes[0].toInt() != VERSION) throw MalformedInputException("unknown fragment version ${bytes[0]}")
        val count = readU16(bytes, 9)
        val index = readU16(bytes, 7)
        if (count == 0 || index >= count) throw MalformedInputException("fragment index $index / count $count invalid")
        return Fragment(
            connectionSessionId = readInt(bytes, 1),
            frameId = readU16(bytes, 5),
            index = index,
            count = count,
            payload = bytes.copyOfRange(HEADER_BYTES, bytes.size),
        )
    }

    private fun writeInt(out: ByteArray, at: Int, value: Int) {
        for (i in 0 until 4) out[at + i] = (value ushr (24 - 8 * i)).toByte()
    }

    private fun writeU16(out: ByteArray, at: Int, value: Int) {
        out[at] = (value ushr 8).toByte()
        out[at + 1] = value.toByte()
    }

    private fun readInt(bytes: ByteArray, at: Int): Int =
        (0 until 4).fold(0) { acc, i -> (acc shl 8) or (bytes[at + i].toInt() and 0xFF) }

    private fun readU16(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
}

/**
 * Per-peer, per-direction reassembly with hard bounds (spec §7): frame size,
 * fragment count, memory per peer, concurrent frames, timeout, duplicate and
 * conflict handling. Not thread-safe; each link feeds it from one thread.
 */
class FragmentReassembler(private val config: ProtocolConfig) {
    sealed interface Result {
        class Complete(val frame: ByteArray) : Result
        data object Pending : Result
        data object Duplicate : Result
        class Rejected(val reason: String) : Result
    }

    private class Partial(val count: Int, val startedAtMs: Long) {
        val parts = arrayOfNulls<ByteArray>(count)
        var received = 0
        var bytes = 0
    }

    private var lockedSessionId: Int? = null
    private val partials = LinkedHashMap<Int, Partial>()
    private var bufferedBytes = 0

    val bufferedBytesForTest: Int get() = bufferedBytes

    fun accept(raw: ByteArray, nowMs: Long): Result {
        sweep(nowMs)
        val fragment = try {
            FragmentCodec.parse(raw)
        } catch (e: MalformedInputException) {
            return Result.Rejected(e.message ?: "malformed fragment")
        }
        val sessionId = lockedSessionId
        if (sessionId == null) {
            lockedSessionId = fragment.connectionSessionId
        } else if (sessionId != fragment.connectionSessionId) {
            return Result.Rejected("fragment from a different connection session")
        }
        if (fragment.count > config.maxFragmentsPerFrame) return Result.Rejected("fragment count ${fragment.count} over cap")

        val partial = partials[fragment.frameId] ?: run {
            if (partials.size >= config.maxConcurrentReassembliesPerPeer) {
                return Result.Rejected("too many concurrent frames")
            }
            Partial(fragment.count, nowMs).also { partials[fragment.frameId] = it }
        }
        if (partial.count != fragment.count) {
            drop(fragment.frameId)
            return Result.Rejected("conflicting fragment_count for frame ${fragment.frameId}")
        }
        val existing = partial.parts[fragment.index]
        if (existing != null) {
            if (existing.contentEquals(fragment.payload)) return Result.Duplicate
            drop(fragment.frameId)
            return Result.Rejected("conflicting payload for fragment ${fragment.index}")
        }
        if (partial.bytes + fragment.payload.size > config.maxLinkFrameBytes) {
            drop(fragment.frameId)
            return Result.Rejected("frame exceeds ${config.maxLinkFrameBytes} B")
        }
        if (bufferedBytes + fragment.payload.size > config.maxReassemblyBytesPerPeer) {
            drop(fragment.frameId)
            return Result.Rejected("per-peer reassembly memory cap reached")
        }
        partial.parts[fragment.index] = fragment.payload
        partial.received++
        partial.bytes += fragment.payload.size
        bufferedBytes += fragment.payload.size
        if (partial.received < partial.count) return Result.Pending

        val frame = ByteArray(partial.bytes)
        var offset = 0
        partial.parts.forEach { part ->
            System.arraycopy(part!!, 0, frame, offset, part.size)
            offset += part.size
        }
        drop(fragment.frameId)
        return Result.Complete(frame)
    }

    /** Discards reassemblies older than the timeout; returns how many were dropped. */
    fun sweep(nowMs: Long): Int {
        val expired = partials.filterValues { nowMs - it.startedAtMs > config.reassemblyTimeoutMs }.keys.toList()
        expired.forEach { drop(it) }
        return expired.size
    }

    fun clear() {
        partials.clear()
        bufferedBytes = 0
    }

    private fun drop(frameId: Int) {
        partials.remove(frameId)?.let { bufferedBytes -= it.bytes }
    }
}
