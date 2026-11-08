package com.chmod777.itantra.protocol.cbor

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Thrown for every structurally invalid, non-canonical or over-limit input. */
class MalformedInputException(message: String) : Exception(message)

/**
 * Deterministic CBOR subset used by every iTantra structure (spec §16, §49).
 *
 * Supported: unsigned integers (0..Long.MAX_VALUE), byte strings, UTF-8 text
 * strings, arrays, maps keyed by unsigned integers, and booleans. Everything else
 * (negative ints, floats, tags, null, indefinite lengths) is rejected, so there is
 * exactly one valid encoding for every value (RFC 8949 §4.2.1 for this subset).
 */
sealed interface Cbor {
    data class UInt(val value: Long) : Cbor {
        init {
            require(value >= 0) { "negative integers are outside the iTantra CBOR subset" }
        }
    }

    class Bytes(val value: ByteArray) : Cbor {
        override fun equals(other: Any?): Boolean = other is Bytes && value.contentEquals(other.value)
        override fun hashCode(): Int = value.contentHashCode()
        override fun toString(): String = "Bytes(${value.size})"
    }

    data class Text(val value: String) : Cbor

    data class Arr(val items: List<Cbor>) : Cbor

    /** Keys are kept sorted, so iteration order is the canonical order. */
    class Map(entries: kotlin.collections.Map<Long, Cbor>) : Cbor {
        val entries: java.util.SortedMap<Long, Cbor> = java.util.TreeMap(entries)

        init {
            require(entries.keys.all { it >= 0 }) { "map keys must be unsigned" }
        }

        override fun equals(other: Any?): Boolean = other is Map && entries == other.entries
        override fun hashCode(): Int = entries.hashCode()
        override fun toString(): String = "Map(${entries.keys})"
    }

    data class Bool(val value: Boolean) : Cbor
}

/** Hard ceilings applied while decoding. Every structure passes explicit limits. */
data class CborLimits(
    val maxInputBytes: Int,
    /** Container nesting levels allowed: 1 = one flat map/array of scalars. */
    val maxDepth: Int = 6,
    val maxContainerItems: Int = 256,
    val maxTotalItems: Int = 2_048,
    val maxByteStringBytes: Int = maxInputBytes,
    val maxTextBytes: Int = maxInputBytes,
)

object CborCodec {
    private const val MAJOR_UINT = 0
    private const val MAJOR_BYTES = 2
    private const val MAJOR_TEXT = 3
    private const val MAJOR_ARRAY = 4
    private const val MAJOR_MAP = 5
    private const val MAJOR_SIMPLE = 7

    fun encode(value: Cbor): ByteArray = ByteArrayOutputStream().also { write(it, value) }.toByteArray()

    private fun write(out: ByteArrayOutputStream, value: Cbor) {
        when (value) {
            is Cbor.UInt -> writeHead(out, MAJOR_UINT, value.value)
            is Cbor.Bytes -> {
                writeHead(out, MAJOR_BYTES, value.value.size.toLong())
                out.write(value.value)
            }
            is Cbor.Text -> {
                val bytes = value.value.toByteArray(StandardCharsets.UTF_8)
                writeHead(out, MAJOR_TEXT, bytes.size.toLong())
                out.write(bytes)
            }
            is Cbor.Arr -> {
                writeHead(out, MAJOR_ARRAY, value.items.size.toLong())
                value.items.forEach { write(out, it) }
            }
            is Cbor.Map -> {
                writeHead(out, MAJOR_MAP, value.entries.size.toLong())
                value.entries.forEach { (key, item) ->
                    writeHead(out, MAJOR_UINT, key)
                    write(out, item)
                }
            }
            is Cbor.Bool -> out.write((MAJOR_SIMPLE shl 5) or if (value.value) 21 else 20)
        }
    }

    private fun writeHead(out: ByteArrayOutputStream, major: Int, argument: Long) {
        val m = major shl 5
        when {
            argument < 24 -> out.write(m or argument.toInt())
            argument <= 0xFF -> {
                out.write(m or 24)
                out.write(argument.toInt())
            }
            argument <= 0xFFFF -> {
                out.write(m or 25)
                out.write((argument shr 8).toInt() and 0xFF)
                out.write(argument.toInt() and 0xFF)
            }
            argument <= 0xFFFF_FFFFL -> {
                out.write(m or 26)
                for (shift in 24 downTo 0 step 8) out.write((argument shr shift).toInt() and 0xFF)
            }
            else -> {
                out.write(m or 27)
                for (shift in 56 downTo 0 step 8) out.write((argument shr shift).toInt() and 0xFF)
            }
        }
    }

    /** Decodes exactly one canonical item spanning the whole input. */
    @Throws(MalformedInputException::class)
    fun decode(input: ByteArray, limits: CborLimits): Cbor {
        if (input.size > limits.maxInputBytes) {
            throw MalformedInputException("input ${input.size} B exceeds ${limits.maxInputBytes} B")
        }
        val reader = Reader(input, limits)
        val value = reader.readItem(depth = 0)
        if (reader.position != input.size) throw MalformedInputException("trailing bytes after CBOR item")
        return value
    }

    private class Reader(private val input: ByteArray, private val limits: CborLimits) {
        var position = 0
        private var totalItems = 0

        fun readItem(depth: Int): Cbor {
            if (depth > limits.maxDepth) throw MalformedInputException("nesting deeper than ${limits.maxDepth}")
            if (++totalItems > limits.maxTotalItems) throw MalformedInputException("more than ${limits.maxTotalItems} items")
            val initial = readByte()
            val major = initial ushr 5
            val info = initial and 0x1F
            if (major == MAJOR_SIMPLE) {
                return when (info) {
                    20 -> Cbor.Bool(false)
                    21 -> Cbor.Bool(true)
                    else -> throw MalformedInputException("unsupported simple/float value $info")
                }
            }
            val argument = readArgument(info)
            return when (major) {
                MAJOR_UINT -> Cbor.UInt(argument)
                MAJOR_BYTES -> Cbor.Bytes(readRaw(checkLength(argument, limits.maxByteStringBytes)))
                MAJOR_TEXT -> Cbor.Text(decodeUtf8(readRaw(checkLength(argument, limits.maxTextBytes))))
                MAJOR_ARRAY -> {
                    val count = checkCount(argument)
                    Cbor.Arr(List(count) { readItem(depth + 1) })
                }
                MAJOR_MAP -> {
                    val count = checkCount(argument)
                    val entries = LinkedHashMap<Long, Cbor>(count)
                    var previousKey = -1L
                    repeat(count) {
                        if (++totalItems > limits.maxTotalItems) throw MalformedInputException("more than ${limits.maxTotalItems} items")
                        val keyHead = readByte()
                        if (keyHead ushr 5 != MAJOR_UINT) throw MalformedInputException("map key is not an unsigned integer")
                        val key = readArgument(keyHead and 0x1F)
                        if (key <= previousKey) throw MalformedInputException("map keys unsorted or duplicated")
                        previousKey = key
                        entries[key] = readItem(depth + 1)
                    }
                    Cbor.Map(entries)
                }
                else -> throw MalformedInputException("unsupported CBOR major type $major")
            }
        }

        private fun checkCount(argument: Long): Int {
            if (argument > limits.maxContainerItems) {
                throw MalformedInputException("container of $argument items exceeds ${limits.maxContainerItems}")
            }
            // Each item needs at least one byte, so a larger claim is a lie.
            if (argument > input.size - position) throw MalformedInputException("container count exceeds input")
            return argument.toInt()
        }

        private fun checkLength(argument: Long, max: Int): Int {
            if (argument > max) throw MalformedInputException("string of $argument B exceeds $max B")
            if (argument > input.size - position) throw MalformedInputException("string length exceeds input")
            return argument.toInt()
        }

        private fun readArgument(info: Int): Long = when {
            info < 24 -> info.toLong()
            info == 24 -> readUnsigned(1).also { if (it < 24) nonCanonical() }
            info == 25 -> readUnsigned(2).also { if (it <= 0xFF) nonCanonical() }
            info == 26 -> readUnsigned(4).also { if (it <= 0xFFFF) nonCanonical() }
            info == 27 -> readUnsigned(8).also {
                if (it < 0) throw MalformedInputException("integer exceeds signed 64-bit range")
                if (it <= 0xFFFF_FFFFL) nonCanonical()
            }
            else -> throw MalformedInputException("indefinite or reserved length encoding")
        }

        private fun nonCanonical(): Nothing = throw MalformedInputException("non-shortest integer encoding")

        private fun readUnsigned(byteCount: Int): Long {
            var value = 0L
            repeat(byteCount) { value = (value shl 8) or readByte().toLong() }
            return value
        }

        private fun readByte(): Int {
            if (position >= input.size) throw MalformedInputException("truncated CBOR")
            return input[position++].toInt() and 0xFF
        }

        private fun readRaw(length: Int): ByteArray {
            if (length > input.size - position) throw MalformedInputException("truncated CBOR string")
            return input.copyOfRange(position, position + length).also { position += length }
        }

        private fun decodeUtf8(bytes: ByteArray): String = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            throw MalformedInputException("invalid UTF-8 text")
        }
    }
}

/** Builds a canonical map. Insertion order is irrelevant; keys are sorted on encode. */
fun cborMap(build: CborMapBuilder.() -> Unit): Cbor.Map = CborMapBuilder().apply(build).build()

class CborMapBuilder {
    private val entries = HashMap<Long, Cbor>()

    fun put(key: Int, value: Cbor) {
        require(entries.put(key.toLong(), value) == null) { "duplicate key $key" }
    }

    fun put(key: Int, value: Long) = put(key, Cbor.UInt(value))
    fun put(key: Int, value: Int) = put(key, Cbor.UInt(value.toLong()))
    fun put(key: Int, value: ByteArray) = put(key, Cbor.Bytes(value))
    fun put(key: Int, value: String) = put(key, Cbor.Text(value))
    fun put(key: Int, value: Boolean) = put(key, Cbor.Bool(value))

    fun build(): Cbor.Map = Cbor.Map(entries)
}

/** Strict typed readers. Any missing, mistyped or out-of-range field is malformed input. */
fun Cbor.asMap(): Cbor.Map = this as? Cbor.Map ?: throw MalformedInputException("expected map")

fun Cbor.Map.requireOnlyKeys(vararg allowed: Int) {
    val allowedSet = allowed.map { it.toLong() }.toSet()
    entries.keys.firstOrNull { it !in allowedSet }?.let { throw MalformedInputException("unexpected field $it") }
}

fun Cbor.Map.uint(key: Int, range: LongRange = 0..Long.MAX_VALUE): Long {
    val value = (entries[key.toLong()] as? Cbor.UInt ?: throw MalformedInputException("field $key: expected uint")).value
    if (value !in range) throw MalformedInputException("field $key: $value outside $range")
    return value
}

fun Cbor.Map.int(key: Int, range: IntRange): Int = uint(key, range.first.toLong()..range.last.toLong()).toInt()

fun Cbor.Map.bytes(key: Int, exactSize: Int): ByteArray {
    val value = bytesUpTo(key, exactSize)
    if (value.size != exactSize) throw MalformedInputException("field $key: expected $exactSize B, got ${value.size}")
    return value
}

fun Cbor.Map.bytesUpTo(key: Int, maxSize: Int, minSize: Int = 0): ByteArray {
    val value = (entries[key.toLong()] as? Cbor.Bytes ?: throw MalformedInputException("field $key: expected bytes")).value
    if (value.size > maxSize || value.size < minSize) {
        throw MalformedInputException("field $key: ${value.size} B outside $minSize..$maxSize")
    }
    return value
}

fun Cbor.Map.text(key: Int, maxBytes: Int, minBytes: Int = 0): String {
    val value = (entries[key.toLong()] as? Cbor.Text ?: throw MalformedInputException("field $key: expected text")).value
    val size = value.toByteArray(StandardCharsets.UTF_8).size
    if (size > maxBytes || size < minBytes) throw MalformedInputException("field $key: text $size B outside $minBytes..$maxBytes")
    return value
}

fun Cbor.Map.bool(key: Int): Boolean =
    (entries[key.toLong()] as? Cbor.Bool ?: throw MalformedInputException("field $key: expected bool")).value

fun Cbor.Map.array(key: Int, maxItems: Int): List<Cbor> {
    val value = (entries[key.toLong()] as? Cbor.Arr ?: throw MalformedInputException("field $key: expected array")).items
    if (value.size > maxItems) throw MalformedInputException("field $key: ${value.size} items exceeds $maxItems")
    return value
}

fun Cbor.Map.has(key: Int): Boolean = entries.containsKey(key.toLong())
