package com.chmod777.itantra.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Bluetooth Classic discovery behind the transport boundary.
 *
 * Call [start] only after [BluetoothPermissions.areGranted] returns true and
 * close this object when the caller's UI is disposed.
 */
class BluetoothDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private var receiverRegistered = false
    private var onEvent: ((DiscoveryEvent) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?: return
                    onEvent?.invoke(
                        DiscoveryEvent.DeviceFound(
                            NearbyBluetoothDevice(
                                name = device.name ?: "Unnamed device",
                                address = device.address,
                            ),
                        ),
                    )
                }

                android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    onEvent?.invoke(DiscoveryEvent.Started)
                }

                android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    onEvent?.invoke(DiscoveryEvent.Finished)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(onEvent: (DiscoveryEvent) -> Unit): Boolean {
        this.onEvent = onEvent
        registerReceiverIfNeeded()

        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return false
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        return adapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun cancel() {
        appContext.getSystemService(BluetoothManager::class.java)?.adapter?.cancelDiscovery()
    }

    fun close() {
        cancel()
        if (receiverRegistered) {
            appContext.unregisterReceiver(receiver)
            receiverRegistered = false
        }
        onEvent = null
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            // Bluetooth discovery broadcasts are sent by Android's Bluetooth
            // service, which is outside this app's process.
            ContextCompat.RECEIVER_EXPORTED,
        )
        receiverRegistered = true
    }
}

sealed interface DiscoveryEvent {
    data object Started : DiscoveryEvent
    data object Finished : DiscoveryEvent
    data class DeviceFound(val device: NearbyBluetoothDevice) : DiscoveryEvent
}

data class NearbyBluetoothDevice(
    val name: String,
    val address: String,
)

@Suppress("DEPRECATION")
private inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? =
    getParcelableExtra(key)
