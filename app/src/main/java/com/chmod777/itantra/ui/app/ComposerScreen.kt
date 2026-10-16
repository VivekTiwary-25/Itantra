package com.chmod777.itantra.ui.app

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.network.RecipientOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The demo's composer, on the real engine.
 *
 * It holds no message state of its own. The draft, the mode, the chosen contact and the sending
 * flags belong to AppState, so a transcription arriving from Home, a rotation, or a detour to add
 * a contact all leave the message where it was. What is local here is only what nobody else
 * needs: the discard dialog, the contact sheet and the send overlay.
 *
 * Send hands the text to the engine and waits. Nothing navigates until [sentToken] moves, which
 * the caller does once per bundle that really reached the queue; a failure arrives as
 * [sendError] and leaves the person on the draft they wrote.
 */
@Composable
internal fun ComposerScreen(
    draft: String,
    onDraftChange: (String) -> Unit,
    mode: MessageMode,
    onModeChange: (MessageMode) -> Unit,
    recipients: List<RecipientOption>,
    selectedRecipientHex: String?,
    onSelectRecipient: (String) -> Unit,
    emergencyOn: Boolean,
    onStartEmergency: () -> Unit,
    isSending: Boolean,
    sendError: String?,
    onDismissError: () -> Unit,
    sentToken: Int,
    onSend: () -> Unit,
    onSent: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    // Saveable, so a rotation mid-overlay cannot swallow onSent and strand the person here.
    var playing by rememberSaveable { mutableStateOf(false) }
    var seenToken by rememberSaveable { mutableStateOf(sentToken) }
    var sentLabel by rememberSaveable { mutableStateOf("Sent") }

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val accent = mode.accent()
    // A contact removed since it was chosen should read as "none chosen", not as a stale name.
    val recipient = recipients.firstOrNull { it.nodeIdHex == selectedRecipientHex }
    val canSend = draft.isNotBlank() && !isSending && !playing

    // The cursor lives here and the text lives in the caller, so the two are kept in step both
    // ways. When the caller replaces the draft (a transcription lands) the cursor goes to the
    // end; when the person types, the caller's value is already equal and nothing is reset.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(draft, TextRange(draft.length)))
    }
    if (field.text != draft) field = TextFieldValue(draft, TextRange(draft.length))

    LaunchedEffect(sentToken) {
        if (sentToken != seenToken) {
            seenToken = sentToken
            playing = true
        }
    }

    val attemptBack: () -> Unit = {
        when {
            isSending || playing -> {}
            draft.isNotBlank() -> confirmDiscard = true
            else -> onBack()
        }
        Unit
    }
    BackHandler(onBack = attemptBack)

    LaunchedEffect(Unit) {
        // Opened from Write with nothing in it: the keyboard is the next thing the person wants.
        if (draft.isBlank()) {
            delay(280)
            focus.requestFocus()
            keyboard?.show()
        }
    }

    Box(modifier.fillMaxSize().background(Ink.night)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            TopBar(title = "New message", onBack = attemptBack)

            Column(Modifier.padding(horizontal = 20.dp)) {
                AnimatedVisibility(
                    visible = !emergencyOn,
                    enter = fadeIn(tween(160)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(220))
                ) {
                    NoticeStrip(
                        text = "Emergency mode is off. This will wait in the queue until you start it.",
                        action = "START EMERGENCY MODE",
                        onAction = onStartEmergency,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }
                ModeSelector(selected = mode, onSelect = onModeChange)
                Spacer(Modifier.height(10.dp))
                Text(modeCaption(mode), color = Ink.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                RecipientLine(
                    name = recipient?.name,
                    onClick = { keyboard?.hide(); pickerOpen = true }
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                BasicTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        if (it.text != draft) onDraftChange(it.text)
                    },
                    textStyle = TextStyle(color = Ink.text, fontSize = 24.sp, lineHeight = 34.sp),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    decorationBox = { inner ->
                        if (field.text.isEmpty()) {
                            Text("Write your message…", color = Ink.quiet, fontSize = 24.sp, lineHeight = 34.sp)
                        }
                        inner()
                    }
                )
            }

            // The last message is kept while the strip animates out, so it does not collapse empty.
            val shownError = rememberLastNonNull(sendError)
            AnimatedVisibility(
                visible = sendError != null,
                enter = fadeIn(tween(160)) + expandVertically(tween(220)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(220))
            ) {
                NoticeStrip(
                    text = shownError.orEmpty(),
                    action = "TRY AGAIN",
                    onAction = onDismissError,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)
                )
            }

            val sendColor by animateColorAsState(accent, tween(220), label = "send color")
            PrimaryPill(
                label = "Send",
                icon = Icons.AutoMirrored.Rounded.Send,
                color = sendColor,
                contentColor = Ink.onGold,
                enabled = canSend,
                onClick = {
                    keyboard?.hide()
                    if (recipient == null) {
                        pickerOpen = true
                    } else {
                        sentLabel = "Sent to ${recipient.name}"
                        onSend()
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }

        if (playing) {
            SendOverlay(mode = mode, label = sentLabel, onFinished = onSent)
        }
    }

    if (pickerOpen) {
        TrustedContactSheet(
            contacts = recipients,
            selectedHex = recipient?.nodeIdHex,
            onSelect = {
                onSelectRecipient(it)
                // The usual error here is "choose a contact", which this answers.
                if (sendError != null) onDismissError()
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false }
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = Ink.surfaceRaised,
            title = { Text("Discard this message?", color = Ink.text) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("Discard", color = Ink.sosLight) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing", color = Ink.text) }
            }
        )
    }
}

@Composable
private fun rememberLastNonNull(value: String?): String? {
    var last by remember { mutableStateOf(value) }
    if (value != null && value != last) last = value
    return value ?: last
}

private fun modeCaption(mode: MessageMode) = when (mode) {
    MessageMode.NORMAL -> "Private · only the person you choose can read it"
    MessageMode.URGENT -> "Private · travels further and faster"
    // Not reachable from here; SOS is category-first from Home.
    MessageMode.SOS, MessageMode.SOS_REPLY -> "Reaches anyone nearby who can help"
}

/** SOS is deliberately absent: it is chosen from Home and has its own screens, not a segment here. */
private val SELECTABLE_MODES = listOf(MessageMode.NORMAL, MessageMode.URGENT)

/** One inset track, one raised thumb: Normal | Urgent. */
@Composable
private fun ModeSelector(selected: MessageMode, onSelect: (MessageMode) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val index = SELECTABLE_MODES.indexOf(selected).coerceAtLeast(0)
    val thumbColor by animateColorAsState(
        if (selected == MessageMode.URGENT) Ink.amberSurface else Ink.thumb,
        tween(200), label = "thumb"
    )
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(Ink.inset)
            .padding(4.dp)
    ) {
        val segment = maxWidth / SELECTABLE_MODES.size
        val offset by animateDpAsState(segment * index, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "thumb x")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clayShadow(22.dp, elevation = 4.dp, color = Color.Black.copy(alpha = 0.5f))
                .clip(RoundedCornerShape(50))
                .background(thumbColor)
        )
        Row(Modifier.fillMaxSize()) {
            SELECTABLE_MODES.forEach { mode ->
                val active = mode == selected
                val labelColor by animateColorAsState(
                    when {
                        !active -> Ink.muted
                        mode == MessageMode.NORMAL -> Ink.text
                        else -> mode.accent()
                    },
                    tween(200), label = "label"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            if (mode != selected) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onSelect(mode)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (mode == MessageMode.NORMAL) "Normal" else "Urgent",
                        color = labelColor,
                        fontSize = 16.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipientLine(name: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("To", color = Ink.muted, fontSize = 16.sp)
        Text("  ·  ", color = Ink.quiet, fontSize = 16.sp)
        Text(
            name ?: "Select trusted contact",
            color = if (name == null) Ink.body else Ink.text,
            fontSize = 17.sp,
            fontWeight = if (name == null) FontWeight.Normal else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Change recipient", tint = Ink.muted)
    }
}

/**
 * Warm-red inline notice with one action. Both states this screen has to be honest about use it,
 * so neither is a system dialog that interrupts typing.
 */
@Composable
private fun NoticeStrip(text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.sosSurface)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 5.dp).size(9.dp).clip(RoundedCornerShape(50)).background(Ink.sos))
            Spacer(Modifier.width(11.dp))
            Text(text, color = Ink.body, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            action,
            color = Ink.gold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier
                .align(Alignment.End)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onAction)
                .padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/**
 * The trusted contacts, by name. The first two fingerprint groups are there only to tell two
 * people with the same name apart; the full comparison belongs to the moment a contact is added.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrustedContactSheet(
    contacts: List<RecipientOption>,
    selectedHex: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Ink.surface,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(
                "Select trusted contact",
                color = Ink.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
            if (contacts.isEmpty()) {
                Text(
                    "No trusted contacts yet. You add one by scanning their code, face to face.",
                    color = Ink.muted,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
            contacts.forEach { contact ->
                val isSelected = contact.nodeIdHex == selectedHex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { sheetState.hide(); onSelect(contact.nodeIdHex) } }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            contact.name,
                            color = Ink.text,
                            fontSize = 18.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            contact.fingerprint.split(' ').take(2).joinToString(" "),
                            color = Ink.quiet,
                            fontSize = 13.sp,
                            letterSpacing = 0.4.sp,
                            maxLines = 1
                        )
                    }
                    if (isSelected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Ink.gold, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
