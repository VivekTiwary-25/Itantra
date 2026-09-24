package com.chmod777.itantra.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.chmod777.itantra.dtn.DtnClock
import com.chmod777.itantra.metrics.MetricsSink
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.transport.LinkRole
import com.chmod777.itantra.transport.PeerLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * The single iTantra GATT service (spec §7):
 * `ClientToServer` WRITE (with response) and `ServerToClient` INDICATE + CCCD.
 *
 * Each connecting central gets a [BleGattServerLink]; it becomes READY after the
 * central enables indications and the HELLO exchange completes.
 */
@SuppressLint("MissingPermission")
class BleGattServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val config: ProtocolConfig,
    private val clock: DtnClock,
    private val metrics: MetricsSink,
    private val localShortId: () -> ByteArray,
    private val onLinkReady: (BleGattServerLink) -> Unit,
) {
    private var server: BluetoothGattServer? = null
    private var serverToClient: BluetoothGattCharacteristic? = null
    private val links = ConcurrentHashMap<String, BleGattServerLink>()
    private val serviceAdded = CompletableDeferred<Boolean>()

    val activeLinkCount: Int get() = links.size

    private val callback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            serviceAdded.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val key = device.address
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (links.size >= config.maxLinks) {
                        metrics.record("gatt_server_reject", mapOf("cause" to "link limit"))
                        server?.cancelConnection(device)
                        return
                    }
                    val link = BleGattServerLink(this@BleGattServer, device, localShortId(), config, clock, metrics)
                    links.put(key, link)?.let { old -> scope.launch { old.close() } }
                    link.onCentralConnected()
                }
                BluetoothProfile.STATE_DISCONNECTED -> links.remove(key)?.onDisconnected("central disconnected status=$status")
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            links[device.address]?.onMtu(mtu)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            val link = links[device.address]
            val acceptable = link != null && characteristic.uuid == GattProfile.CLIENT_TO_SERVER_UUID && !preparedWrite && offset == 0 && value != null
            if (responseNeeded) {
                server?.sendResponse(device, requestId, if (acceptable) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null)
            }
            if (acceptable) link!!.onClientWrite(value!!.copyOf())
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            val isCccd = descriptor.uuid == GattProfile.CCCD_UUID && descriptor.characteristic.uuid == GattProfile.SERVER_TO_CLIENT_UUID
            if (responseNeeded) server?.sendResponse(device, requestId, if (isCccd) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, 0, null)
            if (isCccd && value != null && value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                links[device.address]?.let { link -> scope.launch { link.onIndicationsEnabled() } }
            }
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            links[device.address]?.onIndicationSent(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    /** Opens the server and registers the service. Returns false if Android refused. */
    suspend fun start(): Boolean {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return false
        val opened = manager.openGattServer(context, callback) ?: return false
        server = opened
        val service = BluetoothGattService(GattProfile.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                GattProfile.CLIENT_TO_SERVER_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            ),
        )
        val s2c = BluetoothGattCharacteristic(
            GattProfile.SERVER_TO_CLIENT_UUID,
            BluetoothGattCharacteristic.PROPERTY_INDICATE,
            0,
        )
        s2c.addDescriptor(
            BluetoothGattDescriptor(GattProfile.CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE),
        )
        service.addCharacteristic(s2c)
        serverToClient = service.getCharacteristic(GattProfile.SERVER_TO_CLIENT_UUID)
        if (!opened.addService(service)) return false
        val ok = try {
            withTimeout(config.gattOperationTimeoutMs) { serviceAdded.await() }
        } catch (_: TimeoutCancellationException) {
            false
        }
        metrics.record("gatt_server_started", mapOf("ok" to ok))
        return ok
    }

    internal fun indicate(device: BluetoothDevice, fragment: ByteArray): Boolean {
        val s = server ?: return false
        val characteristic = serverToClient ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            s.notifyCharacteristicChanged(device, characteristic, true, fragment) == BluetoothStatusCodes.SUCCESS
        } else {
            // Pre-33 the value lives on the shared characteristic object, so serialise across devices.
            synchronized(characteristic) {
                @Suppress("DEPRECATION")
                characteristic.value = fragment
                @Suppress("DEPRECATION")
                s.notifyCharacteristicChanged(device, characteristic, true)
            }
        }
    }

    /** Only the device's current link may tear down the connection; a superseded link just goes away. */
    internal fun disconnect(link: BleGattServerLink, device: BluetoothDevice) {
        val current = links[device.address]
        if (current != null && current !== link) return
        links.remove(device.address, link)
        runCatching { server?.cancelConnection(device) }
    }

    internal fun linkReady(link: BleGattServerLink) = onLinkReady(link)

    fun sweep() = links.values.forEach { it.sweepReassembly() }

    fun stop() {
        links.values.forEach { link -> scope.launch { link.close() } }
        links.clear()
        runCatching { server?.clearServices() }
        runCatching { server?.close() }
        server = null
    }
}

/** Server-side end of a link. Writes are indications, confirmed by `onNotificationSent`. */
@SuppressLint("MissingPermission")
class BleGattServerLink internal constructor(
    private val owner: BleGattServer,
    private val device: BluetoothDevice,
    localShortId: ByteArray,
    config: ProtocolConfig,
    clock: DtnClock,
    metrics: MetricsSink,
) : BleGattLinkCore(LinkRole.RESPONDER, config, clock, metrics, localShortId, initialPeerShortId = null) {

    @Volatile private var readying = false

    /** Link-layer address, used only inside `transport/ble` to avoid duplicate links. */
    internal val deviceAddress: String get() = device.address

    internal fun onCentralConnected() {
        mutableState.value = PeerLink.State.CONNECTING
        mark("gatt_server_central_connected")
    }

    internal fun onMtu(mtu: Int) {
        attMtu = mtu
        mark("mtu_result", mapOf("mtu" to mtu, "status" to 0))
    }

    internal fun onClientWrite(value: ByteArray) = onFragmentReceived(value)
    internal fun onIndicationSent(success: Boolean) = onFragmentWriteComplete(success)
    internal fun onDisconnected(reason: String) = onTransportDisconnected(reason)

    internal suspend fun onIndicationsEnabled() {
        if (readying) return
        readying = true
        mark("cccd_ready")
        try {
            completeHelloAndBecomeReady()
            owner.linkReady(this)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            failAndClose("server setup failed: ${e.message}")
        }
    }

    /** The central initiates; the server side never calls connect(). */
    override suspend fun connect() = Unit

    override fun startFragmentWrite(fragment: ByteArray): Boolean = owner.indicate(device, fragment)

    override fun releaseTransport() = owner.disconnect(this, device)
}
