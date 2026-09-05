package com.chmod777.itantra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chmod777.itantra.transport.AdapterStatus
import com.chmod777.itantra.transport.BluetoothPermissions
import com.chmod777.itantra.transport.BluetoothDiscovery
import com.chmod777.itantra.transport.DiscoveryEvent
import com.chmod777.itantra.transport.NearbyBluetoothDevice
import com.chmod777.itantra.transport.PairedBluetoothDevice
import com.chmod777.itantra.transport.PairingRequestResult
import com.chmod777.itantra.transport.BluetoothRfcommTransport
import com.chmod777.itantra.transport.RfcommConnectionState
import com.chmod777.itantra.ui.theme.SIH_iTantraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SIH_iTantraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BluetoothPermissionScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun BluetoothPermissionScreen(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var permissionsGranted by remember {
        mutableStateOf(BluetoothPermissions.areGranted(context))
    }
    var adapterStatus by remember { mutableStateOf<AdapterStatus?>(null) }
    var pairedDevices by remember { mutableStateOf(emptyList<PairedBluetoothDevice>()) }
    var nearbyDevices by remember { mutableStateOf(emptyList<NearbyBluetoothDevice>()) }
    var isDiscovering by remember { mutableStateOf(false) }
    var discoveryMessage by remember { mutableStateOf<String?>(null) }
    var pairingMessage by remember { mutableStateOf<String?>(null) }
    val discovery = remember { BluetoothDiscovery(context) }
    val rfcommTransport = remember { BluetoothRfcommTransport(context) }
    var connectionState by remember { mutableStateOf<RfcommConnectionState>(RfcommConnectionState.Idle) }

    DisposableEffect(discovery) {
        onDispose { discovery.close() }
    }

    DisposableEffect(rfcommTransport) {
        onDispose { rfcommTransport.close() }
    }

    val refreshBluetoothState = {
        if (BluetoothPermissions.areGranted(context)) {
            adapterStatus = BluetoothPermissions.adapterStatus(context)
            pairedDevices = if (adapterStatus == AdapterStatus.Enabled) {
                BluetoothPermissions.pairedDevices(context)
            } else {
                emptyList()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsGranted = BluetoothPermissions.areGranted(context)
        refreshBluetoothState()
    }

    LaunchedEffect(permissionsGranted) {
        if (!permissionsGranted) {
            permissionLauncher.launch(BluetoothPermissions.requiredRuntimePermissions())
        } else {
            refreshBluetoothState()
        }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "iTantra transport")

        if (!permissionsGranted) {
            Text(text = "Nearby-device permission is needed to find and connect to Bluetooth devices.")
            Button(onClick = {
                permissionLauncher.launch(BluetoothPermissions.requiredRuntimePermissions())
            }) {
                Text("Allow Bluetooth access")
            }
        } else {
            Text(
                text = when (adapterStatus) {
                    AdapterStatus.Enabled -> "Bluetooth adapter: ON"
                    AdapterStatus.Disabled -> "Bluetooth adapter: OFF — turn it on to continue."
                    AdapterStatus.NotSupported -> "Bluetooth is not supported on this device."
                    null -> "Checking Bluetooth adapter…"
                },
            )

            if (adapterStatus == AdapterStatus.Enabled) {
                Text(text = "Paired devices (${pairedDevices.size})")
                if (pairedDevices.isEmpty()) {
                    Text(text = "No paired Bluetooth devices found.")
                } else {
                    pairedDevices.forEach { device ->
                        Text(text = "• ${device.name}")
                        Button(
                            onClick = {
                                rfcommTransport.connect(device.address) { state -> connectionState = state }
                            },
                            enabled = connectionState !is RfcommConnectionState.Listening &&
                                connectionState !is RfcommConnectionState.Connecting &&
                                connectionState !is RfcommConnectionState.Connected,
                        ) {
                            Text("Connect to ${device.name}")
                        }
                    }
                }

                Button(onClick = refreshBluetoothState) {
                    Text("Refresh paired devices")
                }

                Text(
                    text = when (val state = connectionState) {
                        RfcommConnectionState.Idle -> "RFCOMM: not connected"
                        RfcommConnectionState.Listening -> "RFCOMM: listening for a connection…"
                        is RfcommConnectionState.Connecting -> "RFCOMM: connecting to ${state.peerName}…"
                        is RfcommConnectionState.Connected -> "RFCOMM: connected to ${state.peerName}"
                        is RfcommConnectionState.Error -> "RFCOMM: ${state.message}"
                    },
                )
                Button(
                    onClick = {
                        rfcommTransport.listen { state -> connectionState = state }
                    },
                    enabled = connectionState !is RfcommConnectionState.Listening &&
                        connectionState !is RfcommConnectionState.Connecting &&
                        connectionState !is RfcommConnectionState.Connected,
                ) {
                    Text("Listen for RFCOMM connection")
                }

                Text(text = "Nearby devices (${nearbyDevices.size})")
                when {
                    isDiscovering -> Text(text = "Scanning for nearby Bluetooth devices…")
                    discoveryMessage != null -> Text(text = discoveryMessage!!)
                }
                nearbyDevices.forEach { device ->
                    Text(text = "• ${device.name}")
                    if (pairedDevices.none { it.address == device.address }) {
                        Button(onClick = {
                            pairingMessage = when (BluetoothPermissions.requestPairing(context, device.address)) {
                                PairingRequestResult.Started -> {
                                    "Pairing requested for ${device.name}. Approve it on both phones, then refresh paired devices."
                                }
                                PairingRequestResult.AlreadyPaired -> "${device.name} is already paired."
                                PairingRequestResult.CouldNotStart -> "Could not start pairing with ${device.name}."
                                PairingRequestResult.NotSupported -> "Bluetooth is not supported on this device."
                            }
                        }) {
                            Text("Pair ${device.name}")
                        }
                    }
                }
                if (pairingMessage != null) Text(text = pairingMessage!!)

                Button(
                    onClick = {
                        nearbyDevices = emptyList()
                        discoveryMessage = null
                        val started = discovery.start { event ->
                            when (event) {
                                DiscoveryEvent.Started -> isDiscovering = true
                                DiscoveryEvent.Finished -> {
                                    isDiscovering = false
                                    if (nearbyDevices.isEmpty()) {
                                        discoveryMessage = "No nearby Bluetooth devices found."
                                    }
                                }
                                is DiscoveryEvent.DeviceFound -> {
                                    if (nearbyDevices.none { it.address == event.device.address }) {
                                        nearbyDevices = nearbyDevices + event.device
                                    }
                                }
                            }
                        }
                        if (!started) {
                            discoveryMessage = "Could not start Bluetooth discovery."
                        } else {
                            // Do not wait for the broadcast before giving the
                            // user feedback that discovery has started.
                            isDiscovering = true
                        }
                    },
                    enabled = !isDiscovering,
                ) {
                    Text("Discover nearby devices")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BluetoothPermissionPreview() {
    SIH_iTantraTheme {
        BluetoothPermissionScreen()
    }
}
