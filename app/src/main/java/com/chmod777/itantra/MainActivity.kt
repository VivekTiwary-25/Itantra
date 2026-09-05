package com.chmod777.itantra

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
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
    var isHolding by remember { mutableStateOf(false) }
    val view = LocalView.current
    val idleAlpha by animateFloatAsState(
        targetValue = if (isHolding) 0f else 1f,
        animationSpec = tween(180),
        label = "idle alpha"
    )
    val activeAlpha by animateFloatAsState(
        targetValue = if (isHolding) 1f else 0f,
        animationSpec = tween(180),
        label = "active alpha"
    )

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
            Box(
                modifier = Modifier.size(184.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(view) {
                            detectTapGestures(
                                onPress = {
                                    isHolding = true
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    val released = try {
                                        tryAwaitRelease()
                                    } finally {
                                        isHolding = false
                                    }
                                    if (released) {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                }
                            )
                        }
                        .graphicsLayer { alpha = idleAlpha },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF8AD3C),
                        contentColor = Color(0xFF2C1800)
                    )
                ) {
                    Text("HOLD TO TALK", fontWeight = FontWeight.Bold)
                }
                RecordingPulse(activeAlpha)
            }
            Text(
                text = if (isHolding) "Recording..." else "Idle",
                color = Color(0xFF91A2B4)
            )
        }
    }
}

@Composable
private fun RecordingPulse(alpha: Float) {
    val transition = rememberInfiniteTransition(label = "recording pulse")
    val scale by transition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.38f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing)),
        label = "pulse scale"
    )
    val ringAlpha = ((1.38f - scale) / 0.6f).coerceIn(0f, 0.72f)

    Box(
        modifier = Modifier
            .size(166.dp)
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = ringAlpha
                }
                .border(1.dp, Color(0x6BF8AD3C), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(Color(0xFFF8AD3C))
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SIH_iTantraTheme {
        HoldToTalkScreen()
    }
}
