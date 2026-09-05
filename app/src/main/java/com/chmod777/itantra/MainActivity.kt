package com.chmod777.itantra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chmod777.itantra.ui.theme.SIH_iTantraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SIH_iTantraTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF07111C)
                ) { innerPadding ->
                    HoldToTalkScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun HoldToTalkScreen(modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "iTantra",
            color = Color(0xFFF4F7FB),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Button(
                onClick = { pressed = true },
                modifier = Modifier.size(184.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF8AD3C),
                    contentColor = Color(0xFF2C1800)
                )
            ) {
                Text("HOLD TO TALK", fontWeight = FontWeight.Bold)
            }
            Text(
                text = if (pressed) "pressed" else "Ready",
                color = Color(0xFF91A2B4)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SIH_iTantraTheme {
        HoldToTalkScreen()
    }
}
