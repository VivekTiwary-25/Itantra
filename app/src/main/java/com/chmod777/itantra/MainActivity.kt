package com.chmod777.itantra


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chmod777.itantra.ui.theme.SIH_iTantraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val ttsHelper = TtsHelper(applicationContext)

        setContent {
            SIH_iTantraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TtsScreen(
                        modifier = Modifier.padding(innerPadding),
                        onSpeak = { text, languageCode ->
                            Thread {
                                ttsHelper.speak(text, languageCode)
                            }.start()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TtsScreen(modifier: Modifier = Modifier, onSpeak: (String, String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var isHindi by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(if (isHindi) "Enter Hindi text" else "Enter English text") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { isHindi = !isHindi },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isHindi) "Switch to English" else "Switch to Hindi")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onSpeak(text, if (isHindi) "hi" else "en") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Speak")
        }
    }
}