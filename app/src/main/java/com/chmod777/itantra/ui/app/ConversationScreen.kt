package com.chmod777.itantra.ui.app

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.sos.IncomingSos
import com.chmod777.itantra.sos.OutgoingSosState
import com.chmod777.itantra.sos.SosChatLine
import com.chmod777.itantra.sos.SosTrustState

private enum class ReplyMethod(val label: String) { HOLD("Hold to talk"), HANDS_FREE("Hands-free"), TYPE("Type") }

/** What the conversation screen may truthfully show, derived from the live SOS state on this phone. */
private data class ConversationView(
    val asRequester: Boolean,
    val live: Boolean,
    val status: String?,
    val chat: List<SosChatLine>,
    val trust: SosTrustState?,
    val code: String?,
)

private fun conversationView(sosIdHex: String): ConversationView? {
    val store = AppStore
    store.outgoingSos?.takeIf { it.sosIdHex == sosIdHex }?.let { out ->
        return ConversationView(
            asRequester = true,
            live = out.phase == OutgoingSosState.Phase.CONNECTED,
            status = when (out.phase) {
                OutgoingSosState.Phase.CONNECTED -> null
                OutgoingSosState.Phase.SEARCHING -> "Connection lost. Looking for nearby responders again…"
                OutgoingSosState.Phase.ENDED -> "This conversation has ended."
            },
            chat = out.chat,
            trust = out.trust,
            code = out.shortAuthString,
        )
    }
    store.incomingSos.firstOrNull { it.sosIdHex == sosIdHex }?.let { inc ->
        return ConversationView(
            asRequester = false,
            live = inc.phase == IncomingSos.Phase.ACTIVE,
            status = when (inc.phase) {
                IncomingSos.Phase.ACTIVE -> null
                IncomingSos.Phase.ACCEPTED_WAITING, IncomingSos.Phase.OFFERED -> "Connecting…"
                IncomingSos.Phase.TAKEN_BY_OTHER -> "Someone else is already helping."
                else -> "This conversation has ended."
            },
            chat = inc.chat,
            trust = inc.trust,
            code = inc.shortAuthString,
        )
    }
    return null
}

@Composable
fun ConversationScreen(sosIdHex: String, onRequestMic: () -> Unit) {
    val store = AppStore
    BackHandler { store.back() }
    val view = conversationView(sosIdHex)
    var confirmEnd by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf(ReplyMethod.HOLD) }
    DisposableEffect(Unit) {
        onDispose { if (store.captureState == CaptureState.Recording) store.cancelCapture() }
    }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        ScreenHeader(
            "Emergency conversation",
            { store.back() },
            leading = {
                if (view?.live == true) PulseDot(Ink.Sos, Modifier.padding(end = 10.dp), size = 9.dp)
            },
            trailing = {
                if (view?.live == true) QuietButton("End", { confirmEnd = true }, color = Ink.Sos)
            },
        )
        if (view == null) {
            Text("This conversation is no longer on this phone.", color = Ink.TextMute, modifier = Modifier.padding(8.dp))
            return@Column
        }
        TrustLine(sosIdHex, view)
        val list = rememberLazyListState()
        LaunchedEffect(view.chat.size) { if (view.chat.isNotEmpty()) list.animateScrollToItem(view.chat.lastIndex) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
            if (view.chat.isEmpty()) {
                item {
                    Text(
                        if (view.asRequester) "Your request will appear here once it reaches them." else "Their request will appear here.",
                        color = Ink.TextMute, fontSize = 14.sp, modifier = Modifier.padding(4.dp),
                    )
                }
            }
            // The requester's first line is the original SOS text, sent automatically once someone accepted.
            val requestIndex = view.chat.indexOfFirst { it.fromMe == view.asRequester }
            itemsIndexed(view.chat) { index, line -> ChatLineRow(line, isRequest = index == requestIndex, asRequester = view.asRequester) }
        }
        view.status?.let {
            Text(it, color = Ink.TextDim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        }
        if (view.live) {
            ReplyArea(sosIdHex, method, onRequestMic)
            MethodSwitch(method) {
                if (store.captureState == CaptureState.Recording) store.cancelCapture()
                store.clearCaptureFailure()
                method = it
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(8.dp))
            ClayButton("Back to Home", { store.home() }, Ink.ClayHi, Ink.Text, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            containerColor = Ink.BgRaised,
            title = { Text("End this emergency conversation?", color = Ink.Text) },
            text = { Text("The other phone will be told it has ended.", color = Ink.TextDim) },
            confirmButton = { TextButton({ confirmEnd = false; store.endConversation(sosIdHex) }) { Text("End", color = Ink.Sos) } },
            dismissButton = { TextButton({ confirmEnd = false }) { Text("Keep talking", color = Ink.Text) } },
        )
    }
}

/**
 * Honest session label. The six-digit comparison is the protocol's optional in-person check: it
 * binds this encrypted session to the phone in front of you, not to a real-world identity.
 */
@Composable
private fun TrustLine(sosIdHex: String, view: ConversationView) {
    var comparing by remember { mutableStateOf(false) }
    val verified = view.trust == SosTrustState.NEARBY_DEVICE_VERIFIED
    Column(Modifier.padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (verified) "Encrypted · nearby device verified" else "Encrypted · identity not verified",
                color = Ink.TextMute, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            if (!verified && view.code != null && view.live) {
                QuietButton(if (comparing) "Hide code" else "Compare code", { comparing = !comparing }, color = Ink.TextDim)
            }
        }
        if (comparing && !verified && view.code != null) {
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(view.code.chunked(3).joinToString(" "), color = Ink.Text, fontSize = 22.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    Text("Same code on both phones?", color = Ink.TextDim, fontSize = 12.sp)
                }
                QuietButton("Codes match", { AppStore.markVerified(sosIdHex); comparing = false }, color = Ink.Signal)
            }
        }
    }
}

@Composable
private fun ChatLineRow(line: SosChatLine, isRequest: Boolean, asRequester: Boolean) {
    val who = when {
        line.fromMe -> "You"
        asRequester -> "Responder"
        else -> "Person asking for help"
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = if (line.fromMe) Alignment.End else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isRequest) {
                Tag("SOS", Ink.Sos)
                Spacer(Modifier.width(6.dp))
            }
            Text("$who · ${timeLabel(line.wallMs)}", color = Ink.TextMute, fontSize = 12.sp)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            line.text,
            color = Ink.Text, fontSize = 17.sp, lineHeight = 25.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (line.fromMe) Ink.ClayHi else Ink.Clay)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ReplyArea(sosIdHex: String, method: ReplyMethod, onRequestMic: () -> Unit) {
    val store = AppStore
    val capture = store.captureState
    val showDraft = method == ReplyMethod.TYPE || store.replyDraft.isNotEmpty()
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (capture is CaptureState.Failed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(capture.message, color = Ink.Urgent, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (capture.needsPermission) QuietButton("Allow", { store.clearCaptureFailure(); onRequestMic() }, color = Ink.Text)
            }
        }
        if (showDraft) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = store.replyDraft,
                    onValueChange = { store.replyDraft = it },
                    textStyle = TextStyle(color = Ink.Text, fontSize = 17.sp, lineHeight = 24.sp),
                    cursorBrush = SolidColor(Ink.Sos),
                    maxLines = 4,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Reply" },
                    decorationBox = { inner ->
                        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Ink.ClayLo).heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 14.dp)) {
                            if (store.replyDraft.isEmpty()) Text("Write a reply…", color = Ink.TextMute, fontSize = 17.sp)
                            inner()
                        }
                    },
                )
                Spacer(Modifier.width(10.dp))
                ClayButton(
                    "Send",
                    { store.sendReply(sosIdHex) },
                    Ink.Sos, Ink.SosInk,
                    enabled = store.replyDraft.isNotBlank(),
                    height = 52.dp,
                )
            }
        }
        when (method) {
            ReplyMethod.HOLD -> HoldToTalkBar()
            ReplyMethod.HANDS_FREE -> HandsFreeBar()
            ReplyMethod.TYPE -> Unit
        }
    }
}

/** Compact hold-to-talk: same capture pipeline as Home, transcript lands in the reply draft. */
@Composable
private fun HoldToTalkBar() {
    val store = AppStore
    val view = LocalView.current
    var holding by remember { mutableStateOf(false) }
    val transcribing = store.captureState == CaptureState.Transcribing
    val scale by animateFloatAsState(if (holding) 0.97f else 1f, tween(90), label = "hold")
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .scale(scale)
            .clay(shape, Ink.Signal, pressed = holding, elevation = 10.dp)
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
                        store.finishCapture { store.appendReply(it) }
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            holding -> LiveWaveform({ store.recorder.level }, Ink.SignalInk, Modifier.width(140.dp), bars = 16, maxHeight = 36.dp)
            transcribing -> TranscribingDots(Ink.SignalInk)
            else -> Text("Hold to talk", color = Ink.SignalInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HandsFreeBar() {
    val store = AppStore
    val capture = store.captureState
    Row(
        Modifier.fillMaxWidth().height(64.dp).clay(RoundedCornerShape(20.dp), Ink.Clay, elevation = 8.dp).padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (capture) {
            CaptureState.Recording -> {
                LiveWaveform({ store.recorder.level }, Ink.Signal, Modifier.weight(1f), bars = 18, maxHeight = 34.dp)
                Spacer(Modifier.width(10.dp))
                ClayButton("Done", { store.finishCapture { store.appendReply(it) } }, Ink.Signal, Ink.SignalInk, height = 48.dp)
            }
            CaptureState.Transcribing -> {
                Text("Transcribing…", color = Ink.TextDim, fontSize = 15.sp, modifier = Modifier.weight(1f))
                TranscribingDots(Ink.Signal)
                Spacer(Modifier.width(10.dp))
            }
            else -> {
                Text("Speak without holding", color = Ink.TextDim, fontSize = 15.sp, modifier = Modifier.weight(1f))
                ClayButton("Start", { store.clearCaptureFailure(); store.startCapture() }, Ink.ClayHi, Ink.Text, height = 48.dp)
            }
        }
    }
}

/** Three small choices; only the active one is raised. */
@Composable
private fun MethodSwitch(method: ReplyMethod, onChange: (ReplyMethod) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.ClayLo)
            .border(1.dp, Ink.Line, RoundedCornerShape(14.dp))
            .padding(3.dp),
    ) {
        ReplyMethod.entries.forEach { m ->
            val active = m == method
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .then(if (active) Modifier.clay(RoundedCornerShape(11.dp), Ink.ClayHi, elevation = 4.dp) else Modifier)
                    .clickable { onChange(m) }
                    .semantics {
                        role = Role.Tab
                        selected = active
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(m.label, color = if (active) Ink.Text else Ink.TextMute, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.alpha(if (active) 1f else 0.9f))
            }
        }
    }
}
