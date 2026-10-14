package com.chmod777.itantra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.chmod777.itantra.network.ItantraNetwork
import com.chmod777.itantra.ui.app.ITantraApp
import com.chmod777.itantra.ui.theme.SIH_iTantraTheme

/**
 * The shell. Everything the app does lives in [ITantraApp] and the state holder behind it
 * (ui/app/AppState.kt); this class only opens the engine and hands over to Compose.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opens the networking store and identity before any screen reads them.
        ItantraNetwork.init(this)
        enableEdgeToEdge()
        setContent {
            SIH_iTantraTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF07111C)
                ) { innerPadding ->
                    ITantraApp(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ITantraAppPreview() {
    SIH_iTantraTheme {
        ITantraApp()
    }
}
