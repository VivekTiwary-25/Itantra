package com.chmod777.itantra.protocol

import com.chmod777.itantra.crypto.Primitives
import com.chmod777.itantra.protocol.cbor.Cbor
import com.chmod777.itantra.protocol.cbor.CborCodec
import com.chmod777.itantra.protocol.cbor.CborLimits
import com.chmod777.itantra.protocol.cbor.MalformedInputException
import com.chmod777.itantra.protocol.cbor.array
import com.chmod777.itantra.protocol.cbor.asMap
import com.chmod777.itantra.protocol.cbor.bool
import com.chmod777.itantra.protocol.cbor.bytes
import com.chmod777.itantra.protocol.cbor.bytesUpTo
import com.chmod777.itantra.protocol.cbor.cborMap
import com.chmod777.itantra.protocol.cbor.int
import com.chmod777.itantra.protocol.cbor.requireOnlyKeys
import com.chmod777.itantra.protocol.cbor.text
import com.chmod777.itantra.protocol.cbor.uint

/**
 * Protocol frames carried only inside Noise transport messages (spec §24–§29, §36–§42).
 * Encoding: canonical CBOR map, key 0 = frame type. Every field is bounded on decode.
 */
sealed interface ProtocolFrame {
    val type: FrameType

    fun encode(): ByteArray = CborCodec.encode(toMap())
    fun toMap(): Cbor.Map

    companion object {
        @Throws(MalformedInputException::class)
        fun decode(bytes: ByteArray, config: ProtocolConfig): ProtocolFrame {
            val map = CborCodec.decode(
                bytes,
                CborLimits(
                    maxInputBytes = config.maxLinkFrameBytes,
                    maxDepth = 3,
                    maxContainerItems = maxOf(config.inventoryPageSize, config.maxWantsPerEncounterRound, 16),
                    maxTotalItems = 1 + 16 + maxOf(config.inventoryPageSize, config.maxWantsPerEncounterRound) * 10,
                ),
            ).asMap()
            val type = FrameType.fromWire(map.uint(0)) ?: throw MalformedInputException("unknown frame type")
            return type.decoder(map, config)
        }
    }
}

enum class FrameType(val wire: Int, val decoder: (Cbor.Map, ProtocolConfig) -> ProtocolFrame) {
    INVENTORY(1, InventoryFrame::decode),
    INVENTORY_END(2, InventoryEndFrame::decode),
    WANT(3, WantFrame::decode),
    BUNDLE(4, BundleFrame::decode),
    BUNDLE_ACK(5, BundleAckFrame::decode),
    TOMBSTONE(6, TombstoneFrame::decode),
    SOS_OFFER(10, SosOfferFrame::decode),
    SOS_ACCEPT(11, SosSimpleFrame::decode),
    SOS_DECLINE(12, SosSimpleFrame::decode),
    SOS_CONFIRM(13, SosSimpleFrame::decode),
    SOS_BUSY(14, SosSimpleFrame::decode),
    SOS_CHAT(15, SosChatFrame::decode),
    SOS_END(16, SosSimpleFrame::decode),
    SOS_REQUEST(17, SosRequestFrame::decode),
    SOS_CANCEL(18, SosCancelFrame::decode),
    PROBE(20, ProbeFrame::decode),
    PROBE_ECHO(21, ProbeEchoFrame::decode);

    companion object {
        fun fromWire(value: Long): FrameType? = entries.firstOrNull { it.wire.toLong() == value }
    }
}

private fun Cbor.Map.expectType(type: FrameType) {
    if (uint(0) != type.wire.toLong()) throw MalformedInputException("frame type mismatch")
}

// ---------------------------------------------------------------- DTN frames

enum class WantMode(val wire: Int) {
    RELAY(1),
    DESTINATION(2);

    companion object {
        fun fromWire(v: Long) = entries.firstOrNull { it.wire.toLong() == v } ?: throw MalformedInputException("bad want mode")
    }
}

/** Plaintext-free bundle summary (spec §24). `ciphertextHash` makes dedupe (id, hash) (audit #4). */
class BundleSummary(
    val bundleId: ByteArray,
    val ciphertextHash: ByteArray,
    val destinationTag: ByteArray,
    val kind: BundleKind,
    val priority: Priority,
    val remainingLifetimeMs: Long,
    val payloadSize: Int,
    val copyTokens: Int,
) {
    fun toCbor(): Cbor.Map = cborMap {
        put(1, bundleId)
        put(2, ciphertextHash)
        put(3, destinationTag)
        put(4, kind.wire)
        put(5, priority.wire)
        put(6, remainingLifetimeMs)
        put(7, payloadSize)
        put(8, copyTokens)
    }

    companion object {
        fun fromCbor(value: Cbor, config: ProtocolConfig): BundleSummary {
            val map = value.asMap()
            map.requireOnlyKeys(1, 2, 3, 4, 5, 6, 7, 8)
            map.int(4, BundleKind.PRIVATE.wire..BundleKind.PRIVATE.wire)
            return BundleSummary(
                bundleId = map.bytes(1, ID_BYTES),
                ciphertextHash = map.bytes(2, HASH_BYTES),
                destinationTag = map.bytes(3, ID_BYTES),
                kind = BundleKind.PRIVATE,
                priority = Priority.fromWire(map.uint(5)) ?: throw MalformedInputException("bad priority"),
                remainingLifetimeMs = map.uint(6, 0..config.normalPrivateLifetimeMs),
                payloadSize = map.int(7, 1..config.maxCiphertextBytes),
                copyTokens = map.int(8, 0..config.urgentPrivateCopyBudget.coerceAtLeast(config.normalPrivateCopyBudget)),
            )
        }
    }
}

class InventoryFrame(val roundId: Long, val pageIndex: Int, val summaries: List<BundleSummary>) : ProtocolFrame {
    override val type = FrameType.INVENTORY
    override fun toMap() = cborMap {
        put(0, type.wire)
        put(1, roundId)
        put(2, pageIndex)
        put(3, Cbor.Arr(summaries.map { it.toCbor() }))
    }

    companion object {
        fun decode(map: Cbor.Map, config: ProtocolConfig): InventoryFrame {
            map.expectType(FrameType.INVENTORY)
            map.requireOnlyKeys(0, 1, 2, 3)
            return InventoryFrame(
                roundId = map.uint(1, 0..0xFFFF_FFFFL),
                pageIndex = map.int(2, 0 until config.maxInventoryPages),
                summaries = map.array(3, config.inventoryPageSize).map { BundleSummary.fromCbor(it, config) },
            )
        }
    }
}

class InventoryEndFrame(val roundId: Long, val totalPages: Int) : ProtocolFrame {
    override val type = FrameType.INVENTORY_END
    override fun toMap() = cborMap {
        put(0, type.wire)
        put(1, roundId)
        put(2, totalPages)
    }

    companion object {
        fun decode(map: Cbor.Map, config: ProtocolConfig): InventoryEndFrame {
            map.expectType(FrameType.INVENTORY_END)
            map.requireOnlyKeys(0, 1, 2)
            return InventoryEndFrame(map.uint(1, 0..0xFFFF_FFFFL), map.int(2, 0..config.maxInventoryPages))
        }
    }
}

class WantEntry(val bundleId: ByteArray, val ciphertextHash: ByteArray, val mode: WantMode)

class WantFrame(val roundId: Long, val wants: List<WantEntry>) : ProtocolFrame {
    override val type = FrameType.WANT
    override fun toMap() = cborMap {
        put(0, type.wire)
        put(1, roundId)
        put(2, Cbor.Arr(wants.map { cborMap { put(1, it.bundleId); put(2, it.ciphertextHash); put(3, it.mode.wire) } }))
    }

    companion object {
        fun decode(map: Cbor.Map, config: ProtocolConfig): WantFrame {
            map.expectType(FrameType.WANT)
            map.requireOnlyKeys(0, 1, 2)
            return WantFrame(
                roundId = map.uint(1, 0..0xFFFF_FFFFL),
                wants = map.array(2, config.maxWantsPerEncounterRound).map {
                    val entry = it.asMap()
                    entry.requireOnlyKeys(1, 2, 3)
                    WantEntry(entry.bytes(1, ID_BYTES), entry.bytes(2, HASH_BYTES), WantMode.fromWire(entry.uint(3)))
                },
            )
        }
    }
}

/** One bundle transfer. `immutableBytes` is exactly [PrivateBundleV1.encodeImmutable]. */
class BundleFrame(val mode: WantMode, val immutableBytes: ByteArray, val relayState: RelayState) : ProtocolFrame {
    override val type = FrameType.BUNDLE
    override fun toMap() = cborMap {
        put(0, type.wire)
        put(1, mode.wire)
        put(2, immutableBytes)
        put(3, relayState.copyTokens)
        put(4, relayState.hopCount)
        put(5, relayState.accumulatedAgeMs)
    }

    companion object {
        fun decode(map: Cbor.Map, config: ProtocolConfig): BundleFrame {
            map.expectType(FrameType.BUNDLE)
            map.requireOnlyKeys(0, 1, 2, 3, 4, 5)
            return BundleFrame(
                mode = WantMode.fromWire(map.uint(1)),
                immutableBytes = map.bytesUpTo(2, config.maxCiphertextBytes + 256, minSize = 1),
                relayState = RelayState(
                    copyTokens = map.int(3, 0..maxOf(config.urgentPrivateCopyBudget, config.normalPrivateCopyBudget)),
                    hopCount = map.int(4, 0..255),
                    accumulatedAgeMs = map.uint(5),
                ),
            )
        }
    }
}

/**
 * Hop acknowledgement. It means only "I persisted / already had / refused these
 * bytes" (spec §20). It is NEVER evidence of delivery to the end recipient.
 */
enum class AckStatus(val wire: Int) {
    PERSISTED(1),
    ALREADY_HAVE(2),
    REJECTED(3),
    TOMBSTONED(4),
    ACCEPTED_AS_DESTINATION_ATTEMPT(5);

    companion object {
        fun fromWire(v: Long) = entries.firstOrNull { it.wire.toLong() == v } ?: throw MalformedInputException("bad ack status")
    }
}

class BundleAckFrame(val bundleId: ByteArray, val ciphertextHash: ByteArray, val status: AckStatus) : ProtocolFrame {
    override val type = FrameType.BUNDLE_ACK
    override fun toMap() = cborMap {
        put(0, type.wire)
        put(1, bundleId)
        put(2, ciphertextHash)
        put(3, status.wire)
    }

    companion object {
        fun decode(map: Cbor.Map, @Suppress("UNUSED_PARAMETER") config: ProtocolConfig): BundleAckFrame {
            map.expectType(FrameType.BUNDLE_ACK)
            map.requireOnlyKeys(0, 1, 2, 3)
            return BundleAckFrame(map.bytes(1, ID_BYTES), map.bytes(2, HASH_BYTES), AckStatus.fromWire(map.uint(3)))
        }
    }
}

class TombstoneFrame(val tombstoneBytes: ByteArray) : ProtocolFrame {
    override val type = FrameType.TOMBSTONE
    override fun toMap() = cborMap {
        put(0, type.wire)
        put(1, tombstoneBytes)
    }

    companion object {
        fun decode(map: Cbor.Map, @Suppress("UNUSED_PARAMETER") config: ProtocolConfig): TombstoneFrame {
            map.expectType(FrameType.TOMBSTONE)
            map.requireOnlyKeys(0, 1)
            return TombstoneFrame(map.bytesUpTo(1, 128, minSize = 1))
        }
    }
}

// ---------------------------------------------------------------- SOS frames

enum class SosCategory(val wire: Int, val label: String) {
    MEDICAL(1, "Medical"),
    TRAPPED(2, "Trapped"),
    UNSAFE(3, "Unsafe"),
    COMMUNICATION(4, "Communication"),
    OTHER(5, "Other");

    companion object {
        fun fromWire(v: Long) = entries.firstOrNull { it.wire.toLong() == v } ?: throw MalformedInputException("bad SOS category")
    }
}

private const val SOS_LANGUAGE_MAX = 16

/**
 * Minimal pre-accept SOS disclosure (spec §34 minus credential-present, audit G).
 * The incident key signs the immutable fields (audit #5, domain `itantra-v1/sos-offer`).
 */
class SosOfferFrame(
    val sosId: ByteArray,
    val category: SosCategory,
    val language: String,
    val ageMs: Long,
    val incidentPublicKey: ByteArray,
    val signature: ByteArray,
) : ProtocolFrame {
    override val type = FrameType.SOS_OFFER
    override fun toMap() = cborMap {
        put(0, type.wire)
        put(1, sosId)
        put(2, category.wire)
        put(3, language)
        put(4, ageMs)
        put(5, incidentPublicKey)
        put(6, signature)
    }

    companion object {
        fun signedBytes(sosId: ByteArray, category: SosCategory, language: String, incidentPublicKey: ByteArray): ByteArray =
            CborCodec.encode(cborMap { put(1, sosId); put(2, category.wire); put(3, language); put(4, incidentPublicKey) })

        fun decode(map: Cbor.Map, config: ProtocolConfig): SosOfferFrame {
            map.expectType(FrameType.SOS_OFFER)
            map.requireOnlyKeys(0, 1, 2, 3, 4, 5, 6)
            return SosOfferFrame(
                sosId = map.bytes(1, ID_BYTES),
                category = SosCategory.fromWire(map.uint(2)),
                language = map.text(3, SOS_LANGUAGE_MAX, minBytes = 1),
                ageMs = map.uint(4, 0..config.activeSosIdleTimeoutMs),
                incidentPublicKey = map.bytes(5, Primitives.KEY_BYTES),
                signature = map.bytes(6, Primitives.SIGNATURE_BYTES),
            )
        }
    }
}

/** SOS control frames that carry only the incident ID. */
class SosSimpleFrame(override val type: FrameType, val sosId: ByteArray) : ProtocolFrame {
    override fun toMap() = cborMap {
        put(0, type.wire)
        put(1, sosId)
    }

    companion object {
        private val SIMPLE_TYPES = setOf(FrameType.SOS_ACCEPT, FrameType.SOS_DECLINE, FrameType.SOS_CONFIRM, FrameType.SOS_BUSY, FrameType.SOS_END)

        fun decode(map: Cbor.Map, @Suppress("UNUSED_PARAMETER") config: ProtocolConfig): SosSimpleFrame {
            val type = FrameType.fromWire(map.uint(0))?.takeIf { it in SIMPLE_TYPES }
                ?: throw MalformedInputException("not a simple SOS frame")
            map.requireOnlyKeys(0, 1)
            return SosSimpleFrame(type, map.bytes(1, ID_BYTES))
        }
    }
}

class SosChatFrame(val sosId: ByteArray, val sequence: Long, val language: String, val text: String) : ProtocolFrame {
    override val type = FrameType.SOS_CHAT
    override fun toMap() = cborMap {
        put(0, type.wire)
        put(1, sosId)
        put(2, sequence)
        put(3, language)
        put(4, text)
    }

    companion object {
        fun decode(map: Cbor.Map, config: ProtocolConfig): SosChatFrame {
            map.expectType(FrameType.SOS_CHAT)
            map.requireOnlyKeys(0, 1, 2, 3, 4)
            return SosChatFrame(
                sosId = map.bytes(1, ID_BYTES),
                sequence = map.uint(2, 0..0xFFFF_FFFFL),
                language = map.text(3, SOS_LANGUAGE_MAX, minBytes = 1),
                text = map.text(4, config.sosChatTextMaxBytes, minBytes = 1),
            )
        }
    }
}

/** Multi-hop SOS request (spec §40). Mutable relay fields are excluded from the signature. */
class SosRequestFrame(
    val sosId: ByteArray,
    val requestFrameId: ByteArray,
    val incidentPublicKey: ByteArray,
    val category: SosCategory,
    val language: String,
    val ageMs: Long,
    val remainingTtlMs: Long,
    val hopCount: Int,
    val hopLimit: Int,
    val signature: ByteArray,
) : ProtocolFrame {
    override val type = FrameType.SOS_REQUEST
    override fun toMap() = cborMap {
        put(0, type.wire)
        put(1, sosId)
        put(2, requestFrameId)
        put(3, incidentPublicKey)
        put(4, category.wire)
        put(5, language)
        put(6, ageMs)
        put(7, remainingTtlMs)
        put(8, hopCount)
        put(9, hopLimit)
        put(10, signature)
    }

    fun signedBytes(): ByteArray = signedBytes(sosId, requestFrameId, incidentPublicKey, category, language, hopLimit)

    fun forwarded(extraAgeMs: Long): SosRequestFrame = SosRequestFrame(
        sosId, requestFrameId, incidentPublicKey, category, language,
        ageMs + extraAgeMs, (remainingTtlMs - extraAgeMs).coerceAtLeast(0), hopCount + 1, hopLimit, signature,
    )

    companion object {
        const val MAX_HOP_LIMIT = 8

        fun signedBytes(
            sosId: ByteArray,
            requestFrameId: ByteArray,
            incidentPublicKey: ByteArray,
            category: SosCategory,
            language: String,
            hopLimit: Int,
        ): ByteArray = CborCodec.encode(
            cborMap {
                put(1, sosId)
                put(2, requestFrameId)
                put(3, incidentPublicKey)
                put(4, category.wire)
                put(5, language)
                put(6, hopLimit)
            },
        )

        fun decode(map: Cbor.Map, config: ProtocolConfig): SosRequestFrame {
            map.expectType(FrameType.SOS_REQUEST)
            map.requireOnlyKeys(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            val hopLimit = map.int(9, 1..MAX_HOP_LIMIT)
            return SosRequestFrame(
                sosId = map.bytes(1, ID_BYTES),
                requestFrameId = map.bytes(2, ID_BYTES),
                incidentPublicKey = map.bytes(3, Primitives.KEY_BYTES),
                category = SosCategory.fromWire(map.uint(4)),
                language = map.text(5, SOS_LANGUAGE_MAX, minBytes = 1),
                ageMs = map.uint(6, 0..config.activeSosIdleTimeoutMs),
                remainingTtlMs = map.uint(7, 0..config.sosDiscoveryTtlMs),
                hopCount = map.int(8, 0..hopLimit),
                hopLimit = hopLimit,
                signature = map.bytes(10, Primitives.SIGNATURE_BYTES),
            )
        }
    }
}

enum class SosEndReason(val wire: Int) {
    USER_CANCELLED(1),
    SESSION_ENDED(2),
    RESPONDER_ACCEPTED(3),
    EXPIRED(4);

    companion object {
        fun fromWire(v: Long) = entries.firstOrNull { it.wire.toLong() == v } ?: throw MalformedInputException("bad SOS reason")
    }
}

/** Suppression object (spec §42), signed by the incident key. */
class SosCancelFrame(val sosId: ByteArray, val reason: SosEndReason, val signature: ByteArray) : ProtocolFrame {
    override val type = FrameType.SOS_CANCEL
    override fun toMap() = cborMap {
        put(0, type.wire)
        put(1, sosId)
        put(2, reason.wire)
        put(3, signature)
    }

    companion object {
        fun signedBytes(sosId: ByteArray, reason: SosEndReason): ByteArray =
            CborCodec.encode(cborMap { put(1, sosId); put(2, reason.wire) })

        fun decode(map: Cbor.Map, @Suppress("UNUSED_PARAMETER") config: ProtocolConfig): SosCancelFrame {
            map.expectType(FrameType.SOS_CANCEL)
            map.requireOnlyKeys(0, 1, 2, 3)
            return SosCancelFrame(map.bytes(1, ID_BYTES), SosEndReason.fromWire(map.uint(2)), map.bytes(3, Primitives.SIGNATURE_BYTES))
        }
    }
}

// ---------------------------------------------------------------- Benchmark frames

/**
 * Diagnostic probe for transport benchmarking. Forwarded by relays while
 * `hopsRemaining > 0`; the last hop echoes. Timing is only ever measured on the
 * originator's monotonic clock, plus each relay's own local processing duration.
 */
class ProbeFrame(val probeId: ByteArray, val hopsRemaining: Int, val hopsTraversed: Int, val echoPayload: Boolean, val payload: ByteArray) :
    ProtocolFrame {
    override val type = FrameType.PROBE
    override fun toMap() = cborMap {
        put(0, type.wire)
        put(1, probeId)
        put(2, hopsRemaining)
        put(3, hopsTraversed)
        put(4, echoPayload)
        put(5, payload)
    }

    companion object {
        const val PROBE_ID_BYTES = 8
        const val MAX_HOPS = 4

        fun decode(map: Cbor.Map, config: ProtocolConfig): ProbeFrame {
            map.expectType(FrameType.PROBE)
            map.requireOnlyKeys(0, 1, 2, 3, 4, 5)
            return ProbeFrame(
                probeId = map.bytes(1, PROBE_ID_BYTES),
                hopsRemaining = map.int(2, 0..MAX_HOPS),
                hopsTraversed = map.int(3, 0..MAX_HOPS),
                echoPayload = map.bool(4),
                payload = map.bytesUpTo(5, config.maxProbePayloadBytes),
            )
        }
    }
}

class ProbeEchoFrame(val probeId: ByteArray, val hopsTraversed: Int, val relayProcessingNs: List<Long>, val payload: ByteArray) : ProtocolFrame {
    override val type = FrameType.PROBE_ECHO
    override fun toMap() = cborMap {
        put(0, type.wire)
        put(1, probeId)
        put(2, hopsTraversed)
        put(3, Cbor.Arr(relayProcessingNs.map { Cbor.UInt(it) }))
        put(4, payload)
    }

    fun withRelayProcessing(ns: Long) = ProbeEchoFrame(probeId, hopsTraversed, relayProcessingNs + ns, payload)

    companion object {
        fun decode(map: Cbor.Map, config: ProtocolConfig): ProbeEchoFrame {
            map.expectType(FrameType.PROBE_ECHO)
            map.requireOnlyKeys(0, 1, 2, 3, 4)
            return ProbeEchoFrame(
                probeId = map.bytes(1, ProbeFrame.PROBE_ID_BYTES),
                hopsTraversed = map.int(2, 0..ProbeFrame.MAX_HOPS),
                relayProcessingNs = map.array(3, ProbeFrame.MAX_HOPS * 2).map {
                    (it as? Cbor.UInt ?: throw MalformedInputException("bad relay timing")).value
                },
                payload = map.bytesUpTo(4, config.maxProbePayloadBytes),
            )
        }
    }
}
