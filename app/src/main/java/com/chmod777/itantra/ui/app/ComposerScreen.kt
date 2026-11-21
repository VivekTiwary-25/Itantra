package com.chmod777.itantra.ui.app

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.service.NetworkingRuntime

// ---------------------------------------------------------------------------- Hands-free

@Composable
fun HandsFreeScreen(onRequestMic: () -> Unit) {
    val store = AppStore
    val capture = store.captureState
    DisposableEffect(Unit) {
        store.startCapture()
        onDispose { if (store.captureState == CaptureState.Recording) store.cancelCapture() }
    }
    val leave: () -> Unit = {
        store.cancelCapture()
        store.back()
    }
    BackHandler(onBack = leave)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("Hands-free", leave)
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(200.dp).clay(CircleShape, Ink.Clay, elevation = 18.dp), contentAlignment = Alignment.Center) {
                when (capture) {
                    CaptureState.Recording -> LiveWaveform({ store.recorder.level }, Ink.Signal, Modifier.width(130.dp), bars = 14, maxHeight = 70.dp)
                    CaptureState.Transcribing -> TranscribingDots(Ink.Signal)
                    else -> StaticWaveform(Ink.TextMute)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                when (capture) {
                    CaptureState.Recording -> "Listening…"
                    CaptureState.Transcribing -> "Transcribing…"
                    is CaptureState.Failed -> "Not listening"
                    CaptureState.Idle -> "Ready"
                },
                color = Ink.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            when (capture) {
                is CaptureState.Failed -> {
                    Text(capture.message, color = Ink.Urgent, fontSize = 14.sp)
                    Row {
                        if (capture.needsPermission) QuietButton("Allow microphone", { store.clearCaptureFailure(); onRequestMic() }, color = Ink.Text)
                        else QuietButton("Try again", { store.clearCaptureFailure(); store.startCapture() }, color = Ink.Text)
                        QuietButton("Type instead", { store.clearCaptureFailure(); store.back(); store.openComposerTyped() })
                    }
                }
                else -> Text("Speak normally. Tap Done when you finish.", color = Ink.TextDim, fontSize = 14.sp)
            }
        }
        ClayButton(
            "Done",
            onClick = { store.finishCapture { store.openComposerWithTranscript(it, fromHandsFree = true) } },
            color = Ink.Signal, ink = Ink.SignalInk,
            enabled = capture == CaptureState.Recording,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
    }
}

// ---------------------------------------------------------------------------- Composer

private fun modeColor(mode: SendMode) = when (mode) {
    SendMode.NORMAL -> Ink.Neutral
    SendMode.URGENT -> Ink.Urgent
    SendMode.SOS -> Ink.Sos
}

private fun modeInk(mode: SendMode) = when (mode) {
    SendMode.NORMAL -> Ink.NeutralInk
    SendMode.URGENT -> Ink.UrgentInk
    SendMode.SOS -> Ink.SosInk
}

private fun modeLabel(mode: SendMode) = when (mode) {
    SendMode.NORMAL -> "Normal"
    SendMode.URGENT -> "Urgent"
    SendMode.SOS -> "SOS"
}

/** Copy derived from the actual protocol lifetimes, so it cannot drift from routing. */
private fun explanation(mode: SendMode): String {
    val config = NetworkingRuntime.config
    return when (mode) {
        SendMode.NORMAL -> "Private to one trusted person. Kept for up to ${config.normalPrivateLifetimeMs / 3_600_000} hours."
        SendMode.URGENT -> "Private to one trusted person. Shared more widely for up to ${config.urgentPrivateLifetimeMs / 3_600_000} hours."
        SendMode.SOS -> "Requests help from available responders nearby. No trusted contact needed."
    }
}

@Composable
fun ComposerScreen(onStartRadio: () -> Unit) {
    val store = AppStore
    val view = LocalView.current
    var confirmLeave by remember { mutableStateOf(false) }
    val requestBack: () -> Unit = { if (store.hasDraft) confirmLeave = true else store.back() }
    BackHandler(onBack = requestBack)
    val mode = store.mode
    val session by NetworkingRuntime.session.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (store.activeDraft.isEmpty()) runCatching { focus.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(if (mode == SendMode.SOS) Ink.Sos.copy(alpha = 0.10f) else Color.Transparent, Color.Transparent)))
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        ScreenHeader("New message", requestBack)
        ModeSlider(mode) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            store.changeMode(it)
        }
        Spacer(Modifier.height(10.dp))
        Text(explanation(mode), color = Ink.TextDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(14.dp))
        if (mode != SendMode.SOS) RecipientLine()
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
            BasicTextField(
                value = store.activeDraft,
                onValueChange = { store.setActiveDraft(it) },
                textStyle = TextStyle(color = Ink.Text, fontSize = 20.sp, lineHeight = 30.sp),
                cursorBrush = SolidColor(if (mode == SendMode.NORMAL) Ink.Signal else modeColor(mode)),
                modifier = Modifier.fillMaxSize().focusRequester(focus).semantics { contentDescription = "Message text" },
                decorationBox = { inner ->
                    if (store.activeDraft.isEmpty()) Text("Write your message…", color = Ink.TextMute, fontSize = 20.sp)
                    inner()
                },
            )
        }
        if (scriptMismatch(store.activeDraft, store.draftLanguage)) {
            Text(
                "This text doesn't look like ${languageName(store.draftLanguage)}. It will still send as written.",
                color = Ink.Signal.copy(alpha = 0.85f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        store.composerError?.let { error ->
            Text(error, color = if (mode == SendMode.SOS) Ink.Sos else Ink.Urgent, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
            if (mode == SendMode.SOS && session == null) QuietButton("Turn on Bluetooth radio", onStartRadio, color = Ink.Text)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                store.draftLanguage.uppercase(), color = Ink.TextMute, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                modifier = Modifier.semantics { contentDescription = "Language ${languageName(store.draftLanguage)}" }.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.width(8.dp))
            ClayButton(
                if (store.sending) "Sending…" else "Send",
                onClick = { store.send() },
                color = modeColor(mode), ink = modeInk(mode),
                enabled = !store.sending,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = Ink.BgRaised,
            title = { Text("Leave this message?", color = Ink.Text) },
            text = { Text("Keep the draft for later, or discard it.", color = Ink.TextDim) },
            confirmButton = { TextButton({ confirmLeave = false; store.back() }) { Text("Keep draft", color = Ink.Text) } },
            dismissButton = { TextButton({ confirmLeave = false; store.discardDrafts(); store.back() }) { Text("Discard", color = Ink.Sos) } },
        )
    }
}

/** One inset track, one raised thumb that travels; only the active mode gains colour and elevation. */
@Composable
private fun ModeSlider(mode: SendMode, onChange: (SendMode) -> Unit) {
    val trackShape = RoundedCornerShape(18.dp)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(trackShape)
            .background(Brush.verticalGradient(listOf(Ink.ClayLo, Ink.Clay)))
            .border(1.dp, Color.Black.copy(alpha = 0.35f), trackShape),
    ) {
        val segment = maxWidth / 3
        val offset by animateDpAsState(segment * mode.ordinal, spring(dampingRatio = 0.75f, stiffness = 500f), label = "thumb")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .padding(4.dp)
                .clay(RoundedCornerShape(14.dp), modeColor(mode), elevation = 6.dp),
        )
        Row(Modifier.fillMaxSize()) {
            SendMode.entries.forEach { m ->
                val active = m == mode
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(m) }
                        .semantics {
                            role = Role.Tab
                            selected = active
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(modeLabel(m), color = if (active) modeInk(m) else Ink.TextDim, fontSize = 15.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun RecipientLine() {
    val store = AppStore
    val recipients = remember(store.logVersion) { store.recipients() }
    val selected = recipients.firstOrNull { it.nodeIdHex == store.recipientHex }
    var picking by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 44.dp)) {
            Text("To", color = Ink.TextMute, fontSize = 15.sp)
            Spacer(Modifier.width(12.dp))
            if (recipients.isEmpty()) {
                QuietButton("Add trusted contact", { store.go(Route.Contacts) }, color = Ink.Signal)
                return@Row
            }
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { picking = true }.padding(vertical = 6.dp, horizontal = 4.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "Recipient ${selected?.name ?: "not chosen"}. Change" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Ink.ClayHi), contentAlignment = Alignment.Center) {
                        Text((selected?.name ?: "?").take(1).uppercase(), color = Ink.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(selected?.name ?: "Choose", color = Ink.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                DropdownMenu(picking, { picking = false }, containerColor = Ink.BgRaised) {
                    recipients.forEach { r ->
                        DropdownMenuItem(text = { Text(r.name, color = Ink.Text) }, onClick = { store.selectRecipient(r.nodeIdHex); picking = false })
                    }
                    DropdownMenuItem(text = { Text("Add trusted contact", color = Ink.Signal) }, onClick = { picking = false; store.go(Route.Contacts) })
                }
            }
            IconButton(onClick = { info = !info }) {
                Icon(Icons.Outlined.Info, "About this recipient", tint = Ink.TextMute, modifier = Modifier.size(18.dp))
            }
        }
        if (info) {
            Text("Only this trusted person can read the message. They do not need to be nearby.", color = Ink.TextDim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

private val SCRIPTS = mapOf(
    "latin" to (0x0041..0x024F),
    "hi" to (0x0900..0x097F), "mr" to (0x0900..0x097F),
    "bn" to (0x0980..0x09FF), "gu" to (0x0A80..0x0AFF), "or" to (0x0B00..0x0B7F),
    "ta" to (0x0B80..0x0BFF), "te" to (0x0C00..0x0C7F), "kn" to (0x0C80..0x0CFF), "ml" to (0x0D00..0x0D7F),
)

/** Conservative: only flags when most letters clearly belong to another script. Mixed text passes. */
fun scriptMismatch(text: String, language: String): Boolean {
    val letters = text.filter { it.isLetter() }
    if (letters.length < 6) return false
    val expected = SCRIPTS[if (language == "en") "latin" else language] ?: return false
    val inExpected = letters.count { it.code in expected }
    return inExpected.toFloat() / letters.length < 0.2f
}
