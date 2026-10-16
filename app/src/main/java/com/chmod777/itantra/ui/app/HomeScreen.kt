package com.chmod777.itantra.ui.app

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.network.ItantraNetwork
import com.chmod777.itantra.service.EmergencyState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The demo's Home, on the real engine.
 *
 * The orb drives [onStartRecording]/[onStopRecording] and then a real transcription, so the
 * demo's 950 ms "transcribing" beat is gone — the status line now reflects what the recognizer
 * is actually doing, and it takes as long as it takes.
 */
@Composable
internal fun HomeScreen(
    languageCode: String,
    unreadCount: Int,
    isTranscribing: Boolean,
    emergencyState: EmergencyState,
    onLanguageSelected: (String) -> Unit,
    /** False when the microphone permission is missing: the take never starts. */
    onStartRecording: () -> Boolean,
    onStopRecording: () -> Unit,
    onPttReleased: () -> Unit,
    onHandsFree: () -> Unit,
    onWrite: () -> Unit,
    onLogs: () -> Unit,
    onStartEmergency: () -> Unit,
    onStopEmergency: () -> Unit,
    onSettings: () -> Unit,
    onSos: () -> Unit,
    /** True while this phone has an SOS of its own in the air. */
    sosInProgress: Boolean = false,
    /** How many people nearby are asking for help right now. */
    incomingSosCount: Int = 0,
    onOpenIncomingSos: () -> Unit = {},
    /** Long-press on the wordmark. Null in a release build, where there is nothing to open. */
    onDeveloper: (() -> Unit)? = null,
    /** Why the last take produced nothing, or null. */
    captureError: CaptureError? = null,
    onCaptureErrorAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showLanguages by remember { mutableStateOf(false) }
    val view = LocalView.current
    var held by remember { mutableStateOf(false) }

    val orb = when {
        isTranscribing -> OrbState.TRANSCRIBING
        held -> OrbState.HELD
        else -> OrbState.IDLE
    }
    val idle = orb == OrbState.IDLE

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.night)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandTitle(onLongPress = onDeveloper)
            Spacer(Modifier.weight(1f))
            // Your code, your name and your contacts live behind this; it is the only way in,
            // now that the long-press belongs to the developer screen.
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = idle, onClick = onSettings),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.AccountCircle,
                    contentDescription = "You and your contacts",
                    tint = Ink.muted,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            LanguageChip(
                label = uiLanguage(languageCode).englishName,
                enabled = idle,
                onClick = { showLanguages = true }
            )
        }

        Spacer(Modifier.weight(1f))
        TalkOrb(
            state = orb,
            diameter = 208.dp,
            modifier = Modifier.holdToTalk(
                enabled = !isTranscribing,
                onHoldStart = {
                    // Nothing happens without the microphone; the orb must not pretend otherwise.
                    if (onStartRecording()) {
                        held = true
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                },
                onHoldEnd = { heldMillis ->
                    if (held) {
                        held = false
                        // Always stop: a tap too short to count still opened the mic.
                        onStopRecording()
                        if (heldMillis >= MIN_HOLD_MS) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onPttReleased()
                        }
                    }
                }
            )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = Ink.onGold, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(6.dp))
                Text("Hold to talk", color = Ink.onGold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(40.dp))
        StatusLine(
            text = when (orb) {
                OrbState.HELD -> "Listening…"
                OrbState.TRANSCRIBING -> "Transcribing…"
                else -> ""
            },
            color = if (orb == OrbState.HELD) Ink.gold else Ink.muted
        )
        if (captureError != null) {
            Spacer(Modifier.height(16.dp))
            CaptureErrorStrip(error = captureError, onAction = onCaptureErrorAction)
        }
        Spacer(Modifier.weight(1f))

        if (incomingSosCount > 0) {
            IncomingSosStrip(count = incomingSosCount, onClick = onOpenIncomingSos)
            Spacer(Modifier.height(14.dp))
        }
        EmergencyStrip(
            state = emergencyState,
            onStart = onStartEmergency,
            onStop = onStopEmergency,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        HandsFreeTile(onClick = { if (idle) onHandsFree() }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BentoTile(Icons.Outlined.Create, "Write message", 0, onClick = { if (idle) onWrite() }, modifier = Modifier.weight(1f))
            BentoTile(Icons.AutoMirrored.Outlined.Chat, "Logs", unreadCount, onClick = { if (idle) onLogs() }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        // Visible, because someone needing it will not go hunting; safe, because it opens a
        // category picker and sends nothing until a category is chosen and confirmed.
        SosButton(inProgress = sosInProgress, onClick = { if (idle) onSos() })
        Spacer(Modifier.height(20.dp))
    }

    if (showLanguages) {
        LanguageSheet(
            selectedCode = languageCode,
            onSelect = {
                onLanguageSelected(it)
                showLanguages = false
            },
            onDismiss = { showLanguages = false }
        )
    }
}

@Composable
private fun SosButton(inProgress: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (inProgress) Ink.sos else Ink.sosSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(if (inProgress) Color.White else Ink.sos)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                if (inProgress) "SOS sent" else "SOS",
                color = if (inProgress) Color.White else Ink.sosLight,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (inProgress) "Tap to see who is coming" else "Ask anyone nearby for help",
                color = if (inProgress) Color.White.copy(alpha = 0.85f) else Ink.muted,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun IncomingSosStrip(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Ink.sos)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (count == 1) "Someone nearby needs help" else "$count people nearby need help",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text("Tap to see what they are asking for", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
    }
}

/**
 * Whether this phone is on the mesh, and how many others it can currently see.
 *
 * The count is the honest answer to "is this actually working?", which is the first thing anyone
 * asks of a radio that has no bars. Off is deliberately quiet so it reads as a resting state
 * rather than a fault.
 */
@Composable
private fun EmergencyStrip(
    state: EmergencyState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nearby by ItantraNetwork.nearbyLinkCount.collectAsState(initial = 0)
    val on = state == EmergencyState.On || state == EmergencyState.Starting
    val accent = when (state) {
        is EmergencyState.Error -> Ink.sos
        EmergencyState.On -> Ink.gold
        else -> Ink.quiet
    }
    val label = when (state) {
        EmergencyState.Off -> "Emergency mode off"
        EmergencyState.Starting -> "Starting…"
        EmergencyState.On -> if (nearby == 0) "Emergency mode on" else "Emergency mode on · $nearby nearby"
        is EmergencyState.Error -> state.message
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (state is EmergencyState.Error) Ink.sosSurface else Ink.surface)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(accent))
        Spacer(Modifier.width(11.dp))
        Text(
            label,
            color = if (state == EmergencyState.Off) Ink.muted else Ink.body,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (on) "STOP" else "START",
            color = Ink.gold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = if (on) onStop else onStart)
                .padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/**
 * "iTantra" wordmark. In a debug build a ~1 s long-press opens the old developer screen, which
 * is what keeps the Legacy RFCOMM switch and the Net Lab reachable from the phone until the
 * Settings screen lands. Replaces the demo's long-press onto the director console.
 */
@Composable
private fun BrandTitle(onLongPress: (() -> Unit)?) {
    val view = LocalView.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .then(
                if (onLongPress == null) Modifier else Modifier.pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        val releasedEarly = withTimeoutOrNull(900) { tryAwaitRelease() }
                        if (releasedEarly == null) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onLongPress()
                        }
                    })
                }
            )
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.radialGradient(listOf(Ink.goldLight, Ink.gold, Ink.goldDark)))
        )
        Spacer(Modifier.width(10.dp))
        Text("iTantra", color = Ink.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    }
}

@Composable
private fun LanguageChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Ink.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Language, contentDescription = null, tint = Ink.muted, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = Ink.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Change message language", tint = Ink.muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun BentoTile(
    icon: ImageVector,
    label: String,
    badge: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "bento")
    Row(
        modifier = modifier
            .height(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clayShadow(20.dp, elevation = 7.dp)
            .clip(RoundedCornerShape(20.dp))
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(Ink.surfaceRaised, Ink.surface)))
                drawRoundRect(Color.White.copy(alpha = 0.05f), style = Stroke(1.dp.toPx()), cornerRadius = CornerRadius(20.dp.toPx()))
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Ink.text, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Ink.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
        if (badge > 0) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Ink.gold)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(badge.toString(), color = Ink.onGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Matte, fully opaque "Message language" sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(selectedCode: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Ink.surface,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { SheetHandle() },
    ) {
        Text(
            "Message language",
            color = Ink.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
        )
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            items(UI_LANGUAGES, key = { it.code }) { language ->
                val selected = language.code == selectedCode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                sheetState.hide()
                                onSelect(language.code)
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        language.englishName,
                        color = if (selected) Ink.text else Ink.body,
                        fontSize = 17.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    language.nativeName?.let {
                        Text("  ·  $it", color = Ink.muted, fontSize = 17.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Ink.gold, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun SheetHandle() {
    Box(
        Modifier
            .padding(top = 10.dp, bottom = 14.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(Ink.line)
    )
}
