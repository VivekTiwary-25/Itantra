package com.chmod777.itantra.transport.ble

import com.chmod777.itantra.protocol.LinkFrame
import java.util.UUID

/**
 * Legacy-advertising-sized service data (audit #7): `version(1) | flags(1) | short_id(8)`.
 *
 * On air: flags AD (3 B) + service-data-128 AD (1 len + 1 type + 16 UUID + 10 data = 28 B)
 * = 31 B. No device name, TX power, user name, key or fingerprint is advertised.
 */
data class AdvertisementPayload(val version: Int, val flags: Int, val shortId: ByteArray) {
    val sosActive: Boolean get() = flags and FLAG_SOS_ACTIVE != 0
    val availableToHelp: Boolean get() = flags and FLAG_AVAILABLE_TO_HELP != 0

    fun encode(): ByteArray = byteArrayOf(version.toByte(), flags.toByte()) + shortId

    override fun equals(other: Any?): Boolean = other is AdvertisementPayload &&
        version == other.version && flags == other.flags && shortId.contentEquals(other.shortId)

    override fun hashCode(): Int = shortId.contentHashCode()

    companion object {
        const val VERSION = 1
        const val FLAG_SOS_ACTIVE = 0x01
        const val FLAG_AVAILABLE_TO_HELP = 0x02
        const val ENCODED_BYTES = 2 + LinkFrame.SHORT_ID_BYTES

        /** Returns null for anything that is not a well-formed v1 iTantra advertisement. */
        fun parse(serviceData: ByteArray?): AdvertisementPayload? {
            if (serviceData == null || serviceData.size != ENCODED_BYTES) return null
            if (serviceData[0].toInt() != VERSION) return null
            return AdvertisementPayload(VERSION, serviceData[1].toInt() and 0xFF, serviceData.copyOfRange(2, ENCODED_BYTES))
        }
    }
}

/** The single custom iTantra GATT service (spec §7). UUIDs are fixed protocol constants. */
object GattProfile {
    val SERVICE_UUID: UUID = UUID.fromString("7a1c0001-5e7d-4c9b-9f2a-1a7a47a10001")
    val CLIENT_TO_SERVER_UUID: UUID = UUID.fromString("7a1c0002-5e7d-4c9b-9f2a-1a7a47a10001")
    val SERVER_TO_CLIENT_UUID: UUID = UUID.fromString("7a1c0003-5e7d-4c9b-9f2a-1a7a47a10001")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** ATT header overhead for a write/indication value. */
    const val ATT_OVERHEAD = 3
    const val DEFAULT_ATT_MTU = 23

    /**
     * Largest attribute value Android accepts (Core spec GATT maximum). On API 33+
     * `writeCharacteristic` / `notifyCharacteristicChanged` throw for longer values,
     * so an MTU of 517 still allows only 512-byte fragments (found on real phones).
     */
    const val MAX_ATTRIBUTE_VALUE = 512

    /** Bytes available for one fragment (header + payload) at a negotiated ATT MTU. */
    fun fragmentBudget(attMtu: Int): Int = minOf(attMtu - ATT_OVERHEAD, MAX_ATTRIBUTE_VALUE)
}
