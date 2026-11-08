package com.chmod777.itantra.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.chmod777.itantra.dtn.DtnClock
import com.chmod777.itantra.metrics.MetricsSink
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.transport.LinkClosedException
import com.chmod777.itantra.transport.LinkRole
import com.chmod777.itantra.transport.PeerLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * GATT client side of a link (canonical v1 transport, spec §5, §7). The
 * [BluetoothDevice] never leaves `transport/ble`; upper layers see only the
 * advertised short ID.
 *
 * Setup: connectGatt → discoverServices → requestMtu → write CCCD (indications)
 * → HELLO exchange → READY. Every step is one GATT operation awaited with a
 * timeout; status 133 and other transient failures retry with bounded backoff.
 */
@SuppressLint("MissingPermission")
class BleGattClientLink(
    private val context: Context,
    private val device: BluetoothDevice,
    advertisedShortId: ByteArray,
    localShortId: ByteArray,
    config: ProtocolConfig,
    clock: DtnClock,
    metrics: MetricsSink,
) : BleGattLinkCore(LinkRole.INITIATOR, config, clock, metrics, localShortId, advertisedShortId) {

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var clientToServer: BluetoothGattCharacteristic? = null
    @Volatile private var step: CompletableDeferred<Int>? = null
    @Volatile private var connected = CompletableDeferred<Int>()

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected.complete(status)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // During setup this fails the pending step (e.g. status 133); afterwards it closes the link.
                if (!connected.isCompleted) connected.complete(if (status == BluetoothGatt.GATT_SUCCESS) -1 else status)
                step?.complete(-2)
                if (state == PeerLink.State.READY) onTransportDisconnected("gatt disconnected status=$status")
                // A lingering (duplicate-dropped) client is released once Android reports the connection gone.
                if (keepConnectionOnRelease) closeLingering()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            step?.complete(status)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) attMtu = mtu
            step?.complete(status)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            step?.complete(status)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            onFragmentWriteComplete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == GattProfile.SERVER_TO_CLIENT_UUID) onFragmentReceived(value)
        }

        @Deprecated("Pre-API-33 delivery path")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && characteristic.uuid == GattProfile.SERVER_TO_CLIENT_UUID) {
                characteristic.value?.let { onFragmentReceived(it.copyOf()) }
            }
        }
    }

    override suspend fun connect() {
        check(state == PeerLink.State.IDLE) { "connect() called twice" }
        mutableState.value = PeerLink.State.CONNECTING
        mark("gatt_connect_start")
        try {
            withTimeout(config.linkSetupTimeoutMs * (config.gattRetryBackoffMs.size + 1)) { connectWithRetry() }
            setUpPipe()
            completeHelloAndBecomeReady()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException && e !is TimeoutCancellationException) {
                releaseTransport()
                throw e
            }
            failAndClose("setup failed: ${e.message}")
            throw LinkClosedException("GATT client setup failed: ${e.message}", e)
        }
    }

    private suspend fun connectWithRetry() {
        var attempt = 0
        while (true) {
            connected = CompletableDeferred()
            // Still the supported LE connect path for minSdk 26; newer overloads are API-gated.
            @Suppress("DEPRECATION")
            val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            gatt = g ?: throw LinkClosedException("connectGatt returned null")
            val status = try {
                withTimeout(config.gattConnectTimeoutMs) { connected.await() }
            } catch (_: TimeoutCancellationException) {
                -3
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mark("gatt_connected", mapOf("attempt" to attempt))
                return
            }
            mark("gatt_connect_failed", mapOf("status" to status, "attempt" to attempt))
            closeGatt()
            if (attempt >= config.gattRetryBackoffMs.size) throw LinkClosedException("GATT connect failed after ${attempt + 1} attempts (last status $status)")
            delay(config.gattRetryBackoffMs[attempt++])
        }
    }

    private suspend fun setUpPipe() {
        val g = gatt ?: throw LinkClosedException("no gatt")
        // Give the stack a moment after connect before discovery; some OEM stacks drop an immediate request.
        delay(150)
        runStep("services_discovered") { g.discoverServices() }
        val service = g.getService(GattProfile.SERVICE_UUID) ?: throw LinkClosedException("peer has no iTantra GATT service")
        val c2s = service.getCharacteristic(GattProfile.CLIENT_TO_SERVER_UUID) ?: throw LinkClosedException("missing ClientToServer characteristic")
        val s2c = service.getCharacteristic(GattProfile.SERVER_TO_CLIENT_UUID) ?: throw LinkClosedException("missing ServerToClient characteristic")
        clientToServer = c2s

        // MTU is optional: on failure we keep 23 and fragment smaller (never assume a large default).
        val mtuStatus = runStep("mtu", allowFailure = true) { g.requestMtu(config.requestedAttMtu) }
        mark("mtu_result", mapOf("mtu" to attMtu, "status" to mtuStatus))

        if (!g.setCharacteristicNotification(s2c, true)) throw LinkClosedException("setCharacteristicNotification failed")
        val cccd = s2c.getDescriptor(GattProfile.CCCD_UUID) ?: throw LinkClosedException("missing CCCD")
        runStep("cccd_ready") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }
        g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
    }

    /** Runs exactly one GATT operation and waits for its callback. */
    private suspend fun runStep(name: String, allowFailure: Boolean = false, start: () -> Boolean): Int {
        val completion = CompletableDeferred<Int>()
        step = completion
        try {
            if (!start()) {
                if (allowFailure) return -4
                throw LinkClosedException("$name could not start")
            }
            val status = try {
                withTimeout(config.gattOperationTimeoutMs) { completion.await() }
            } catch (_: TimeoutCancellationException) {
                if (allowFailure) return -3
                throw LinkClosedException("$name timed out")
            }
            if (status == -2) throw LinkClosedException("disconnected during $name")
            if (status != BluetoothGatt.GATT_SUCCESS && !allowFailure) throw LinkClosedException("$name failed with status $status")
            mark(name, mapOf("status" to status))
            return status
        } finally {
            step = null
        }
    }

    override fun startFragmentWrite(fragment: ByteArray): Boolean {
        val g = gatt ?: return false
        val characteristic = clientToServer ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(characteristic, fragment, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = fragment
            @Suppress("DEPRECATION")
            g.writeCharacteristic(characteristic)
        }
    }

    private fun closeGatt() {
        val g = gatt ?: return
        gatt = null
        runCatching { g.disconnect() }
        runCatching { g.close() }
    }

    override fun releaseTransport() {
        if (keepConnectionOnRelease) {
            lingering += this
            while (lingering.size > MAX_LINGERING) lingering.firstOrNull()?.closeLingering()
        } else {
            closeGatt()
        }
    }

    private fun closeLingering() {
        lingering -= this
        closeGatt()
    }

    companion object {
        private const val MAX_LINGERING = 4
        private val lingering: MutableSet<BleGattClientLink> = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

        /** Releases every lingering GATT client (Emergency mode stopping). */
        fun closeAllLingering() = lingering.toList().forEach { it.closeLingering() }
    }
}
