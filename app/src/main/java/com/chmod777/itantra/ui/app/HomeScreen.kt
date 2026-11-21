package com.chmod777.itantra.ui.app

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.network.ItantraNetwork
import com.chmod777.itantra.service.EmergencyState
import com.chmod777.itantra.service.NetworkingRuntime
import com.chmod777.itantra.sos.IncomingSos
import com.chmod777.itantra.sos.OutgoingSosState

enum class LangStatus { READY, BETA, COMING_SOON }

data class LanguageOption(val code: String, val english: String, val native: String?, val status: LangStatus)

/**
 * Speech coverage in this build: English (Whisper tiny.en) is the verified path. Dolphin STT and
 * a TTS voice exist for the Beta rows but are not physically verified end to end. Kannada and
 * Malayalam have a voice but no speech recognition, so they cannot be offered for speaking.
 */
val LANGUAGES = listOf(
    LanguageOption("en", "English", null, LangStatus.READY),
    LanguageOption("hi", "Hindi", "हिन्दी", LangStatus.BETA),
    LanguageOption("gu", "Gujarati", "ગુજરાતી", LangStatus.BETA),
    LanguageOption("mr", "Marathi", "मराठी", LangStatus.BETA),
    LanguageOption("kn", "Kannada", "ಕನ್ನಡ", LangStatus.COMING_SOON),
    LanguageOption("ml", "Malayalam", "മലയാളം", LangStatus.COMING_SOON),
    LanguageOption("ta", "Tamil", "தமிழ்", LangStatus.BETA),
    LanguageOption("te", "Telugu", "తెలుగు", LangStatus.BETA),
    LanguageOption("or", "Odia", "ଓଡ଼ିଆ", LangStatus.BETA),
    LanguageOption("bn", "Bengali", "বাংলা", LangStatus.BETA),
)

fun languageName(code: String): String = LANGUAGES.firstOrNull { it.code == code }?.english ?: code.uppercase()

@Composable
fun HomeScreen(onRequestMic: () -> Unit, onStartRadio: () -> Unit) {
    val store = AppStore
    var showLanguages by remember { mutableStateOf(false) }
    val unread = remember(store.logVersion, store.readVersion) { store.unreadIncomingCount() }
    val capture = store.captureState
    val busy = capture == CaptureState.Recording || capture == CaptureState.Transcribing

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            val context = LocalContext.current
            Box(Modifier.weight(1f)) {
                // Long-press opens the diagnostic Net Lab for the team; it is not product navigation.
                Text(
                    "iTantra", color = Ink.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = {
                            context.startActivity(android.content.Intent(context, com.chmod777.itantra.ui.lab.NetworkLabActivity::class.java))
                        })
                    },
                )
            }
            LanguageChip(store.languageCode, enabled = !busy) { showLanguages = true }
            HomeMenu(onStartRadio)
        }
        HomePrompts()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // The orb shrinks on short screens so Home stays within one viewport.
            PttOrb(outer = minOf(maxHeight, maxWidth, 300.dp))
        }
        CaptureStatus(capture, onRequestMic)
        Spacer(Modifier.height(12.dp))
        HandsFreeTile(enabled = !busy) {
            store.clearCaptureFailure()
            store.go(Route.HandsFree)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallTile("Write", "Write message", Modifier.weight(1f), onClick = { store.clearCaptureFailure(); store.openComposerTyped() }) {
                ComposeGlyph(Ink.Text)
            }
            SmallTile("Logs", if (unread > 0) "Logs, $unread unread" else "Logs", Modifier.weight(1f), badge = unread, onClick = { store.go(Route.Logs) }) {
                ChatGlyph(Ink.Text)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (showLanguages) LanguageSheet(store.languageCode, onDismiss = { showLanguages = false }) {
        store.selectLanguage(it)
        showLanguages = false
    }
}

@Composable
private fun LanguageChip(code: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Message language: ${languageName(code)}. Change" }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) Ink.Text else Ink.TextMute
        Icon(Icons.Outlined.Language, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(languageName(code), color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Icon(Icons.Filled.KeyboardArrowDown, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun HomeMenu(onStartRadio: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val available by NetworkingRuntime.availableToHelp.collectAsState()
    val radio by ItantraNetwork.emergencyState.collectAsState()
    val nearby by ItantraNetwork.nearbyLinkCount.collectAsState(initial = 0)
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Ink.TextDim) }
        DropdownMenu(open, { open = false }, containerColor = Ink.BgRaised) {
            DropdownMenuItem(
                text = { Text("Available to help nearby", color = Ink.Text) },
                trailingIcon = {
                    Checkbox(available, { NetworkingRuntime.setAvailableToHelp(it) }, colors = CheckboxDefaults.colors(checkedColor = Ink.Signal, checkmarkColor = Ink.SignalInk))
                },
                onClick = { NetworkingRuntime.setAvailableToHelp(!available) },
            )
            DropdownMenuItem(text = { Text("Trusted contacts", color = Ink.Text) }, onClick = { open = false; AppStore.go(Route.Contacts) })
            val radioLabel = when (val r = radio) {
                EmergencyState.On -> "Bluetooth radio on · $nearby nearby"
                EmergencyState.Starting -> "Bluetooth radio starting…"
                EmergencyState.Off -> "Bluetooth radio off · Start"
                is EmergencyState.Error -> "${r.message} · Retry"
            }
            DropdownMenuItem(
                text = { Text(radioLabel, color = Ink.TextDim, fontSize = 13.sp) },
                enabled = radio !is EmergencyState.On && radio != EmergencyState.Starting,
                onClick = { open = false; onStartRadio() },
            )
            HorizontalDivider(color = Ink.Line)
            DropdownMenuItem(text = { Text("Settings", color = Ink.Text) }, onClick = { open = false; AppStore.go(Route.ComingSoon("Settings")) })
            DropdownMenuItem(text = { Text("About iTantra", color = Ink.Text) }, onClick = { open = false; AppStore.go(Route.ComingSoon("About iTantra")) })
        }
    }
}

/** Contextual glass prompts: an SOS response, an active emergency conversation, or an urgent arrival. */
@Composable
private fun HomePrompts() {
    val store = AppStore
    val out = store.outgoingSos
    val activeIncoming = store.incomingSos.firstOrNull { it.phase == IncomingSos.Phase.ACTIVE }
    val outKey = out?.let { it.sosIdHex + it.responderSessionKey }
    when {
        out != null && out.phase == OutgoingSosState.Phase.CONNECTED && store.dismissedPromptKey != outKey ->
            GlassPrompt(
                title = "Someone is responding",
                action = "Open conversation",
                accent = Ink.Sos,
                onAction = { store.go(Route.Conversation(out.sosIdHex)) },
                onDismiss = { store.dismissedPromptKey = outKey },
            )
        activeIncoming != null && store.dismissedPromptKey != "in:" + activeIncoming.sosIdHex ->
            GlassPrompt(
                title = "Emergency conversation active",
                action = "Open conversation",
                accent = Ink.Sos,
                onAction = { store.go(Route.Conversation(activeIncoming.sosIdHex)) },
                onDismiss = { store.dismissedPromptKey = "in:" + activeIncoming.sosIdHex },
            )
    }
    store.arrival?.let { delivery ->
        Spacer(Modifier.height(8.dp))
        GlassPrompt(
            title = "Urgent · from ${delivery.senderName}",
            body = delivery.text,
            action = "Open",
            accent = Ink.Urgent,
            onAction = {
                store.arrival = null
                store.markRead(delivery.bundleIdHex)
                store.go(Route.Detail(delivery.bundleIdHex))
            },
            onDismiss = { store.arrival = null },
        )
    }
}

@Composable
fun GlassPrompt(title: String, action: String, accent: androidx.compose.ui.graphics.Color, onAction: () -> Unit, onDismiss: () -> Unit, body: String? = null) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDot(accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (body != null) Text(body, color = Ink.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        QuietButton(action, onAction, color = accent)
        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Dismiss", tint = Ink.TextMute, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun PttOrb(outer: Dp) {
    val store = AppStore
    val view = LocalView.current
    val still = reducedMotion()
    var holding by remember { mutableStateOf(false) }
    val transcribing = store.captureState == CaptureState.Transcribing
    val failed = store.captureState is CaptureState.Failed
    val pressScale by animateFloatAsState(if (holding) 0.93f else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "orb")
    val idleAlpha by animateFloatAsState(if (holding || transcribing) 0f else 1f, tween(140), label = "idle")
    val waveAlpha by animateFloatAsState(if (holding) 1f else 0f, tween(160), label = "wave")
    val shake = remember { Animatable(0f) }
    LaunchedEffect(failed) {
        if (failed && !still) {
            for (x in listOf(-10f, 9f, -6f, 4f, 0f)) shake.animateTo(x, tween(55))
        }
    }

    val orb = outer * 0.7f
    Box(Modifier.size(outer).offset { IntOffset(shake.value.dp.roundToPx(), 0) }, contentAlignment = Alignment.Center) {
        if (holding && !still) Ripples(outer, orb)
        Box(
            Modifier
                .size(orb)
                .scale(pressScale)
                .clay(CircleShape, if (failed) lerpColor(Ink.Signal, Ink.Urgent, 0.55f) else Ink.Signal, pressed = holding, elevation = 26.dp)
                .semantics {
                    contentDescription = if (transcribing) "Transcribing" else "Hold to talk"
                    role = Role.Button
                }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        if (store.captureState == CaptureState.Transcribing) return@detectTapGestures
                        store.clearCaptureFailure()
                        if (!store.startCapture()) return@detectTapGestures
                        holding = true
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        try {
                            tryAwaitRelease()
                        } finally {
                            holding = false
                            // Released or interrupted, the captured words are always transcribed.
                            store.finishCapture { store.openComposerWithTranscript(it, fromHandsFree = false) }
                        }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.alpha(idleAlpha), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Mic, null, tint = Ink.SignalInk, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(6.dp))
                Text("Hold to talk", color = Ink.SignalInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            if (waveAlpha > 0.01f) {
                LiveWaveform({ store.recorder.level }, Ink.SignalInk, Modifier.width(128.dp).alpha(waveAlpha), bars = 14, maxHeight = 64.dp)
            }
            if (transcribing) TranscribingDots(Ink.SignalInk)
        }
    }
}

private fun lerpColor(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

@Composable
private fun Ripples(outer: Dp, orb: Dp) {
    val t = rememberInfiniteTransition(label = "ripples")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "p")
    Canvas(Modifier.size(outer)) {
        val base = orb.toPx() / 2
        val reach = outer.toPx() / 2 - base
        repeat(3) { i ->
            val phase = (p + i / 3f) % 1f
            drawCircle(Ink.Signal.copy(alpha = (1f - phase) * 0.32f), base + reach * phase, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
fun TranscribingDots(color: androidx.compose.ui.graphics.Color) {
    val t = rememberInfiniteTransition(label = "dots")
    val p by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "d")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val on = p.toInt() == i
            Box(Modifier.size(10.dp).clip(CircleShape).background(color.copy(alpha = if (on) 0.9f else 0.3f)))
        }
    }
}

@Composable
private fun CaptureStatus(capture: CaptureState, onRequestMic: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(capture == CaptureState.Transcribing, enter = fadeIn(), exit = fadeOut()) {
            Text("Transcribing…", color = Ink.TextDim, fontSize = 14.sp)
        }
        if (capture is CaptureState.Failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(capture.message, color = Ink.Urgent, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
                Row {
                    if (capture.needsPermission) {
                        QuietButton("Allow microphone", { AppStore.clearCaptureFailure(); onRequestMic() }, color = Ink.Text)
                    } else {
                        QuietButton("Try again", { AppStore.clearCaptureFailure() }, color = Ink.Text)
                    }
                    QuietButton("Type instead", { AppStore.clearCaptureFailure(); AppStore.openComposerTyped() })
                }
            }
        }
    }
}

@Composable
private fun HandsFreeTile(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clay(shape, Ink.Clay, elevation = 8.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hands-free", color = Ink.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink.TextDim, modifier = Modifier.size(20.dp))
        }
        StaticWaveform(Ink.Signal.copy(alpha = 0.55f))
    }
}

@Composable
private fun SmallTile(label: String, description: String, modifier: Modifier, badge: Int = 0, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(64.dp)
            .clay(shape, Ink.Clay, elevation = 8.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        glyph()
        Spacer(Modifier.width(10.dp))
        Text(label, color = Ink.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (badge > 0) {
            Box(Modifier.clip(CircleShape).background(Ink.Signal).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(badge.toString(), color = Ink.SignalInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Ink.Glass,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text("Message language", color = Ink.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text(
                "Used to recognise your speech and to speak messages aloud. Text you type is sent exactly as written.",
                color = Ink.TextMute, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
            )
            LANGUAGES.forEach { option ->
                val enabled = option.status != LangStatus.COMING_SOON
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(enabled = enabled) { onSelect(option.code) }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = if (enabled) Ink.Text else Ink.TextMute
                    Text(option.english, color = color, fontSize = 16.sp)
                    option.native?.let {
                        Text("  ·  $it", color = if (enabled) Ink.TextDim else Ink.TextMute, fontSize = 16.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    when {
                        option.code == selected -> Icon(Icons.Filled.Check, "Selected", tint = Ink.Signal, modifier = Modifier.size(20.dp))
                        option.status == LangStatus.BETA -> Text("Beta", color = Ink.TextMute, fontSize = 12.sp)
                        option.status == LangStatus.COMING_SOON -> Text("Coming soon", color = Ink.TextMute, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
