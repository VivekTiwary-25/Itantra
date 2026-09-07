package com.chmod777.itantra.transport

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Android-version-aware permission and adapter checks for the transport lane. */
object BluetoothPermissions {
    fun requiredRuntimePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun areGranted(context: Context): Boolean =
        requiredRuntimePermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

    /** Call only after [areGranted] returns true. */
    @SuppressLint("MissingPermission")
    fun adapterStatus(context: Context): AdapterStatus {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return AdapterStatus.NotSupported

        return if (adapter.isEnabled) AdapterStatus.Enabled else AdapterStatus.Disabled
    }

    /**
     * Returns bonded devices as app data rather than exposing Android's
     * [android.bluetooth.BluetoothDevice] outside the transport lane.
     * Call only after [areGranted] returns true.
     */
    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<PairedBluetoothDevice> {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return emptyList()

        return adapter.bondedDevices
            .map { device ->
                PairedBluetoothDevice(
                    name = device.name ?: "Unnamed device",
                    address = device.address,
                )
            }
            .sortedBy { device -> device.name.lowercase() }
    }

    /** Starts Android's system-controlled bond (pairing) flow for a discovered device. */
    @SuppressLint("MissingPermission")
    fun requestPairing(context: Context, address: String): PairingRequestResult {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return PairingRequestResult.NotSupported
        val device = adapter.getRemoteDevice(address)

        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            return PairingRequestResult.AlreadyPaired
        }

        // Discovery reduces connection and pairing reliability, so stop it first.
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        return if (device.createBond()) {
            PairingRequestResult.Started
        } else {
            PairingRequestResult.CouldNotStart
        }
    }
}

sealed interface AdapterStatus {
    data object Enabled : AdapterStatus
    data object Disabled : AdapterStatus
    data object NotSupported : AdapterStatus
}

data class PairedBluetoothDevice(
    val name: String,
    val address: String,
)

sealed interface PairingRequestResult {
    data object Started : PairingRequestResult
    data object AlreadyPaired : PairingRequestResult
    data object CouldNotStart : PairingRequestResult
    data object NotSupported : PairingRequestResult
}
