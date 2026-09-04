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
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsGranted = BluetoothPermissions.areGranted(context)
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(BluetoothPermissions.requiredRuntimePermissions())
        }
    }

    Column(
        modifier = modifier,
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
            val status = BluetoothPermissions.adapterStatus(context)
            Text(
                text = when (status) {
                    AdapterStatus.Enabled -> "Bluetooth adapter: ON"
                    AdapterStatus.Disabled -> "Bluetooth adapter: OFF — turn it on to continue."
                    AdapterStatus.NotSupported -> "Bluetooth is not supported on this device."
                },
            )
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
