package com.chmod777.itantra.ui.app

import android.view.HapticFeedbackConstants
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Continuous listening: the microphone runs from the moment the screen opens until Done, Back,
 * or the screen going away under it.
 *
 * The demo's 950 ms "transcribing" beat is gone. [isTranscribing] comes from the real recognizer,
 * which takes as long as the take is long, and can come back with nothing — in which case
 * [captureError] appears here rather than the screen pretending a message was made.
 */
@Composable
internal fun HandsFreeScreen(
    /** False when the microphone did not start; the screen must not animate at nothing. */
    onStartRecording: () -> Boolean,
    onStopRecording: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    isTranscribing: Boolean,
    captureError: CaptureError? = null,
    onCaptureErrorAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    val view = LocalView.current

    // Starting in a DisposableEffect ties the microphone to the screen itself: whichever way the
    // screen goes away — Done, Back, the system taking the app down — the recorder is stopped.
    DisposableEffect(Unit) {
        recording = onStartRecording()
        onDispose { onStopRecording() }
    }

    // Leaving the app is not the screen going away: the composition survives, so the recorder
    // kept running in the background and `appops ... RECORD_AUDIO` still read (running) with the
    // phone on the home screen. An app that holds the microphone while nobody is looking at it
    // is the kind of thing this project exists to be trusted about, so the take ends at ON_STOP.
    // Compose's own LocalLifecycleOwner is deprecated in favour of a library this project does
    // not depend on, and lifecycle-runtime-compose is not worth adding to a build file the other
    // lanes share for one observer. The activity has the lifecycle already.
    val context = LocalContext.current
    val lifecycle = remember(context) { (context.findActivity() as? LifecycleOwner)?.lifecycle }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && recording) {
                onStopRecording()
                recording = false
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(recording, isTranscribing) {
        if (recording && !isTranscribing) {
            while (true) {
                delay(1000)
                seconds++
            }
        }
    }

    BackHandler(enabled = !isTranscribing, onBack = onBack)

    Column(
        modifier
            .fillMaxSize()
            .background(Ink.night)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopBar(title = "Hands-free", onBack = { if (!isTranscribing) onBack() })
        Spacer(Modifier.weight(1f))
        TalkOrb(
            state = when {
                isTranscribing -> OrbState.TRANSCRIBING
                recording -> OrbState.LISTENING
                else -> OrbState.IDLE
            },
            diameter = 208.dp
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = Ink.onGold, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(44.dp))
        Text(
            when {
                isTranscribing -> "Transcribing…"
                recording -> "Listening…"
                else -> "Not listening"
            },
            color = if (recording && !isTranscribing) Ink.gold else Ink.muted,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                isTranscribing -> "Preparing your message"
                recording -> "Speak normally  ·  %d:%02d".format(seconds / 60, seconds % 60)
                else -> "The microphone is not running"
            },
            color = Ink.muted,
            fontSize = 14.sp
        )
        if (captureError != null) {
            Spacer(Modifier.height(20.dp))
            CaptureErrorStrip(
                error = captureError,
                onAction = onCaptureErrorAction,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        // Two ways to end up not listening — a take that heard nothing, and leaving the app and
        // coming back — so the one button on the screen offers the way back rather than sitting
        // there greyed out.
        PrimaryPill(
            label = if (recording || isTranscribing) "Done" else "Listen again",
            enabled = !isTranscribing,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (recording) {
                    onStopRecording()
                    recording = false
                    onDone()
                } else {
                    seconds = 0
                    recording = onStartRecording()
                }
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        )
    }
}
