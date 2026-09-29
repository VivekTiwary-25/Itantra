package com.chmod777.itantra.demo.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.chmod777.itantra.demo.DemoMessage
import com.chmod777.itantra.demo.MessageMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * State for one open composer. It lives in the navigation entry (not in the
 * composable) so detours to the contact QR flow and back keep the draft.
 */
class ComposerDraft(
    initialText: String,
    initialMode: MessageMode = MessageMode.NORMAL,
    initialRecipient: String? = null,
    /** Reply inside an active SOS: no mode selector, recipient fixed, sent as SOS_REPLY. */
    val sosReply: Boolean = false,
    /** Replies return to Logs after Send; top-level sends return Home. */
    val isReply: Boolean = false,
    val focusOnOpen: Boolean = false,
) {
    var text by mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
    var mode by mutableStateOf(initialMode)
    var recipient by mutableStateOf(initialRecipient)
    var pickerOpen by mutableStateOf(false)

    val outgoingMode: MessageMode get() = if (sosReply) MessageMode.SOS_REPLY else mode
    val outgoingRecipient: String? get() = if (mode == MessageMode.SOS && !sosReply) null else recipient

    val sentLabel: String get() = when {
        outgoingMode == MessageMode.SOS -> "SOS sent"
        else -> "Sent to ${recipient ?: "contact"}"
    }

    companion object {
        fun reply(to: DemoMessage, text: String, focus: Boolean = false) = ComposerDraft(
            initialText = text,
            initialMode = if (to.mode.isSos) MessageMode.SOS_REPLY else to.mode,
            initialRecipient = to.peer,
            sosReply = to.mode.isSos,
            isReply = true,
            focusOnOpen = focus,
        )
    }
}

@Composable
fun ComposerScreen(
    draft: ComposerDraft,
    contacts: List<String>,
    onBack: () -> Unit,
    onAddContact: () -> Unit,
    onSent: () -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val accent = draft.outgoingMode.accent()
    val privateMode = !draft.sosReply && draft.mode != MessageMode.SOS
    val canSend = draft.text.text.isNotBlank() && !sending

    val attemptBack: () -> Unit = {
        when {
            sending -> {}
            draft.text.text.isNotBlank() -> confirmDiscard = true
            else -> onBack()
        }
        Unit
    }
    BackHandler(onBack = attemptBack)

    LaunchedEffect(Unit) {
        if (draft.focusOnOpen) {
            delay(280)
            focus.requestFocus()
            keyboard?.show()
        }
    }

    Box(Modifier.fillMaxSize().background(Ink.night)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            TopBar(title = "New message", onBack = attemptBack)

            Column(Modifier.padding(horizontal = 20.dp)) {
                if (draft.sosReply) {
                    SosContextLine(draft.recipient ?: "")
                } else {
                    ModeSelector(selected = draft.mode, onSelect = { draft.mode = it })
                    Spacer(Modifier.height(10.dp))
                    Text(modeCaption(draft.mode), color = Ink.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                }
                AnimatedVisibility(
                    visible = privateMode,
                    enter = fadeIn(tween(160)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(220))
                ) {
                    RecipientLine(recipient = draft.recipient, onClick = { keyboard?.hide(); draft.pickerOpen = true })
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                BasicTextField(
                    value = draft.text,
                    onValueChange = { draft.text = it },
                    textStyle = TextStyle(color = Ink.text, fontSize = 24.sp, lineHeight = 34.sp),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    decorationBox = { inner ->
                        if (draft.text.text.isEmpty()) {
                            Text("Write your message…", color = Ink.quiet, fontSize = 24.sp, lineHeight = 34.sp)
                        }
                        inner()
                    }
                )
            }

            val sendColor by animateColorAsState(accent, tween(220), label = "send color")
            PrimaryPill(
                label = "Send",
                icon = Icons.AutoMirrored.Rounded.Send,
                color = sendColor,
                contentColor = if (draft.outgoingMode.isSos) Color.White else Ink.onGold,
                enabled = canSend,
                onClick = {
                    if (privateMode && draft.recipient == null) {
                        keyboard?.hide()
                        draft.pickerOpen = true
                    } else {
                        keyboard?.hide()
                        sending = true
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }

        if (sending) {
            SendOverlay(mode = draft.outgoingMode, label = draft.sentLabel, onFinished = onSent)
        }
    }

    if (draft.pickerOpen) {
        TrustedContactSheet(
            contacts = contacts,
            selected = draft.recipient,
            onSelect = {
                draft.recipient = it
                draft.pickerOpen = false
            },
            onAdd = {
                draft.pickerOpen = false
                onAddContact()
            },
            onDismiss = { draft.pickerOpen = false }
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

private fun modeCaption(mode: MessageMode) = when (mode) {
    MessageMode.NORMAL -> "Private · only the person you choose can read it"
    MessageMode.URGENT -> "Private · travels further and faster"
    MessageMode.SOS, MessageMode.SOS_REPLY -> "Reaches anyone nearby who can help"
}

private val SELECTABLE_MODES = listOf(MessageMode.NORMAL, MessageMode.URGENT, MessageMode.SOS)

/** One inset track, one raised thumb: Normal | Urgent | SOS. */
@Composable
fun ModeSelector(selected: MessageMode, onSelect: (MessageMode) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val index = SELECTABLE_MODES.indexOf(selected).coerceAtLeast(0)
    val thumbColor by animateColorAsState(
        when (selected) {
            MessageMode.URGENT -> Ink.amberSurface
            MessageMode.SOS, MessageMode.SOS_REPLY -> Ink.sosSurface
            MessageMode.NORMAL -> Color(0xFF223549)
        },
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
                        when (mode) {
                            MessageMode.NORMAL -> "Normal"
                            MessageMode.URGENT -> "Urgent"
                            else -> "SOS"
                        },
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
private fun RecipientLine(recipient: String?, onClick: () -> Unit) {
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
            recipient ?: "Select trusted contact",
            color = if (recipient == null) Ink.body else Ink.text,
            fontSize = 17.sp,
            fontWeight = if (recipient == null) FontWeight.Normal else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Change recipient", tint = Ink.muted)
    }
}

/** The fixed context shown instead of the mode selector when replying inside an SOS. */
@Composable
private fun SosContextLine(peer: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.sosSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Ink.sos))
        Spacer(Modifier.width(10.dp))
        Text("SOS", color = Ink.sosLight, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("  ·  ", color = Ink.quiet, fontSize = 16.sp)
        Text(
            "Reply to $peer",
            color = Ink.text,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Names-only trusted list with a decorative search affordance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustedContactSheet(
    contacts: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide(); action() }
    }
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
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Row(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Ink.inset)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Ink.quiet, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Search trusted contacts", color = Ink.quiet, fontSize = 16.sp)
            }
            contacts.forEach { name ->
                val isSelected = name == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { closeThen { onSelect(name) } }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        name,
                        color = Ink.text,
                        fontSize = 18.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Ink.gold, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { closeThen(onAdd) }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Ink.gold, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Add trusted contact", color = Ink.gold, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
