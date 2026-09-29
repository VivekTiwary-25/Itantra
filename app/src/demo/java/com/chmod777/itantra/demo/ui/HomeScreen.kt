package com.chmod777.itantra.demo.ui

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
import androidx.compose.runtime.LaunchedEffect
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
import com.chmod777.itantra.demo.DEMO_LANGUAGES
import com.chmod777.itantra.demo.demoLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Pause between releasing the orb and opening the composer, so the edit reads "it transcribed". */
const val TRANSCRIBE_BEAT_MS = 950L

@Composable
fun HomeScreen(
    languageCode: String,
    unreadCount: Int,
    onLanguageSelected: (String) -> Unit,
    onPttCaptured: () -> Unit,
    onHandsFree: () -> Unit,
    onWrite: () -> Unit,
    onLogs: () -> Unit,
    onDirector: () -> Unit,
) {
    var showLanguages by remember { mutableStateOf(false) }
    val ptt = rememberHoldToTalk()
    LaunchedEffect(ptt.orb) {
        if (ptt.orb == OrbState.TRANSCRIBING) {
            delay(TRANSCRIBE_BEAT_MS)
            onPttCaptured()
            ptt.reset()
        }
    }
    val idle = ptt.orb == OrbState.IDLE

    Column(
        modifier = Modifier
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
            BrandTitle(onLongPress = onDirector)
            Spacer(Modifier.weight(1f))
            LanguageChip(
                label = demoLanguage(languageCode).englishName,
                enabled = idle,
                onClick = { showLanguages = true }
            )
        }

        Spacer(Modifier.weight(1f))
        TalkOrb(
            state = ptt.orb,
            diameter = 208.dp,
            modifier = Modifier.holdToTalk(ptt, enabled = ptt.orb != OrbState.TRANSCRIBING)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = Ink.onGold, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(6.dp))
                Text("Hold to talk", color = Ink.onGold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(40.dp))
        StatusLine(
            text = when (ptt.orb) {
                OrbState.HELD -> "Listening…"
                OrbState.TRANSCRIBING -> "Transcribing…"
                else -> ""
            },
            color = if (ptt.orb == OrbState.HELD) Ink.gold else Ink.muted
        )
        Spacer(Modifier.weight(1f))

        HandsFreeTile(onClick = { if (idle) onHandsFree() }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BentoTile(Icons.Outlined.Create, "Write message", 0, onClick = { if (idle) onWrite() }, modifier = Modifier.weight(1f))
            BentoTile(Icons.AutoMirrored.Outlined.Chat, "Logs", unreadCount, onClick = { if (idle) onLogs() }, modifier = Modifier.weight(1f))
        }
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

/** "iTantra" wordmark. A ~1 s long-press opens the hidden director console. */
@Composable
private fun BrandTitle(onLongPress: () -> Unit) {
    val view = LocalView.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val releasedEarly = withTimeoutOrNull(900) { tryAwaitRelease() }
                    if (releasedEarly == null) {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongPress()
                    }
                })
            }
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

/** Matte, fully opaque "Message language" sheet. No support labels by design (film build). */
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
            items(DEMO_LANGUAGES, key = { it.code }) { language ->
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
