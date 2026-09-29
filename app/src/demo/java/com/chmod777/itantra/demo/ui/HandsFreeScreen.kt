package com.chmod777.itantra.demo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.delay

/**
 * Continuous listening (Home tile and reply row). Done → short transcribing beat
 * → [onTranscribed]; the caller injects the Hands-free transcript, never PTT's.
 */
@Composable
fun HandsFreeScreen(replyTo: String?, onBack: () -> Unit, onTranscribed: () -> Unit) {
    var finishing by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    val view = LocalView.current

    BackHandler(enabled = !finishing, onBack = onBack)
    LaunchedEffect(finishing) {
        if (finishing) {
            delay(TRANSCRIBE_BEAT_MS)
            onTranscribed()
        } else {
            while (true) {
                delay(1000)
                seconds++
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.night)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopBar(title = if (replyTo != null) "Reply to $replyTo" else "Hands-free", onBack = { if (!finishing) onBack() })
        Spacer(Modifier.weight(1f))
        TalkOrb(state = if (finishing) OrbState.TRANSCRIBING else OrbState.LISTENING, diameter = 208.dp) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = Ink.onGold, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(44.dp))
        Text(
            if (finishing) "Transcribing…" else "Listening…",
            color = if (finishing) Ink.muted else Ink.gold,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (finishing) "Preparing your message" else "Speak normally  ·  %d:%02d".format(seconds / 60, seconds % 60),
            color = Ink.muted,
            fontSize = 14.sp
        )
        Spacer(Modifier.weight(1f))
        PrimaryPill(
            label = "Done",
            enabled = !finishing,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                finishing = true
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        )
    }
}
