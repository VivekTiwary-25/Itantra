package com.chmod777.itantra.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.chmod777.itantra.metrics.MetricsSink

/**
 * BLE advertiser (spec §4.1, audit #7). Payload is exactly the 10-byte service
 * data under the iTantra UUID; device name and TX power are explicitly excluded.
 */
@SuppressLint("MissingPermission")
class BleAdvertiser(private val context: Context, private val metrics: MetricsSink) {
    sealed interface Status {
        data object Stopped : Status
        data object Starting : Status
        data object Advertising : Status
        data class Failed(val reason: String) : Status
    }

    @Volatile var status: Status = Status.Stopped
        private set
    private var callback: AdvertiseCallback? = null

    fun start(payload: AdvertisementPayload, onStatus: (Status) -> Unit) {
        stop()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val advertiser = adapter?.bluetoothLeAdvertiser
        if (adapter == null || !adapter.isEnabled) return publish(Status.Failed("Bluetooth is off"), onStatus)
        if (advertiser == null) return publish(Status.Failed("BLE advertising unsupported on this phone"), onStatus)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(ParcelUuid(GattProfile.SERVICE_UUID), payload.encode())
            .build()
        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) = publish(Status.Advertising, onStatus)
            override fun onStartFailure(errorCode: Int) = publish(Status.Failed(describe(errorCode)), onStatus)
        }
        callback = cb
        publish(Status.Starting, onStatus)
        try {
            advertiser.startAdvertising(settings, data, cb)
        } catch (e: SecurityException) {
            publish(Status.Failed("missing Bluetooth advertise permission"), onStatus)
        } catch (e: IllegalStateException) {
            publish(Status.Failed("adapter not ready: ${e.message}"), onStatus)
        }
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(cb) }
        status = Status.Stopped
    }

    private fun publish(newStatus: Status, onStatus: (Status) -> Unit) {
        status = newStatus
        metrics.record("adv_status", mapOf("status" to newStatus.toString()))
        onStatus(newStatus)
    }

    private fun describe(errorCode: Int) = when (errorCode) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "advertisement too large"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many advertisers"
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "already started"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "internal error"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "advertising unsupported"
        else -> "error $errorCode"
    }
}

/** A deduplicated scan hit. The [device] handle stays inside `transport/ble`. */
class ScanHit(val device: BluetoothDevice, val payload: AdvertisementPayload, val rssi: Int)

/**
 * BLE scanner (spec §4.1, audit H): always filtered by the iTantra service data so
 * screen-off scanning is allowed; starts are bounded by [com.chmod777.itantra.transport.ScanRestartLimiter].
 */
@SuppressLint("MissingPermission")
class BleScanner(private val context: Context, private val metrics: MetricsSink) {
    @Volatile var scanning = false
        private set
    @Volatile var lastError: String? = null
        private set
    private var callback: ScanCallback? = null

    fun start(onHit: (ScanHit) -> Unit, onFailure: (String) -> Unit): Boolean {
        stop()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            lastError = "scanner unavailable (Bluetooth off?)"
            onFailure(lastError!!)
            return false
        }
        val filter = ScanFilter.Builder()
            .setServiceData(ParcelUuid(GattProfile.SERVICE_UUID), byteArrayOf(AdvertisementPayload.VERSION.toByte()), byteArrayOf(0xFF.toByte()))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .build()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result, onHit)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { handle(it, onHit) }
            override fun onScanFailed(errorCode: Int) {
                scanning = false
                lastError = "scan failed ($errorCode)"
                metrics.record("scan_failed", mapOf("code" to errorCode))
                onFailure(lastError!!)
            }
        }
        callback = cb
        return try {
            scanner.startScan(listOf(filter), settings, cb)
            scanning = true
            lastError = null
            metrics.record("scan_start", emptyMap())
            true
        } catch (e: SecurityException) {
            lastError = "missing Bluetooth scan permission"
            onFailure(lastError!!)
            false
        }
    }

    private fun handle(result: ScanResult, onHit: (ScanHit) -> Unit) {
        val payload = AdvertisementPayload.parse(result.scanRecord?.getServiceData(ParcelUuid(GattProfile.SERVICE_UUID))) ?: return
        onHit(ScanHit(result.device, payload, result.rssi))
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        scanning = false
        runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(cb) }
    }
}
