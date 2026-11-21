package com.chmod777.itantra.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.dtn.DeliveryState
import com.chmod777.itantra.sos.OutgoingSosState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME = SimpleDateFormat("HH:mm", Locale.getDefault())
private val DATE_TIME = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault())

fun timeLabel(ms: Long): String {
    val now = System.currentTimeMillis()
    return if (now - ms < 20 * 3_600_000L) TIME.format(Date(ms)) else DATE_TIME.format(Date(ms))
}

/** Human progression; Delivered only after a verified end-to-end receipt, never on enqueue or hop handoff. */
fun deliveryLabel(state: DeliveryState): String = when (state) {
    DeliveryState.QUEUED -> "Waiting to send"
    DeliveryState.RELAYED -> "On its way"
    DeliveryState.DELIVERED -> "Delivered"
    DeliveryState.EXPIRED -> "Expired"
    DeliveryState.UNKNOWN -> "Status unknown"
}

fun sosStatus(record: SosRecord): String = when (record.phase) {
    OutgoingSosState.Phase.SEARCHING -> "Searching for nearby responders…"
    OutgoingSosState.Phase.CONNECTED -> "Someone is responding"
    OutgoingSosState.Phase.ENDED -> record.endNote ?: "Ended"
}

@Composable
fun LogsScreen() {
    val store = AppStore
    BackHandler { store.back() }
    val entries = remember(store.logVersion, store.readVersion, store.sosRecords.toList()) { store.logEntries() }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("Logs", { store.back() })
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No messages yet.", color = Ink.TextMute, fontSize = 15.sp)
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { it.key }) { entry ->
                LogRow(entry) {
                    when (entry) {
                        is LogEntry.Sos -> store.go(Route.SosDetail(entry.key))
                        is LogEntry.Incoming -> {
                            store.markRead(entry.key)
                            store.go(Route.Detail(entry.key))
                        }
                        is LogEntry.Outgoing -> store.go(Route.Detail(entry.key))
                    }
                }
                HorizontalDivider(color = Ink.Line.copy(alpha = 0.07f), modifier = Modifier.padding(start = 26.dp))
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry, onClick: () -> Unit) {
    val urgent = (entry as? LogEntry.Outgoing)?.urgent == true || (entry as? LogEntry.Incoming)?.urgent == true
    val title = when (entry) {
        is LogEntry.Outgoing -> "To ${entry.peer}"
        is LogEntry.Incoming -> "From ${entry.peer}"
        is LogEntry.Sos -> "SOS"
    }
    val text = when (entry) {
        is LogEntry.Outgoing -> entry.text
        is LogEntry.Incoming -> entry.text
        is LogEntry.Sos -> entry.record.text
    }
    val status = when (entry) {
        is LogEntry.Outgoing -> deliveryLabel(entry.state)
        is LogEntry.Incoming -> if (entry.unread) "New" else "Read"
        is LogEntry.Sos -> sosStatus(entry.record)
    }
    val unread = entry is LogEntry.Incoming && entry.unread
    val activeSos = entry is LogEntry.Sos && entry.record.phase != OutgoingSosState.Phase.ENDED
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(26.dp).padding(top = 6.dp), contentAlignment = Alignment.CenterStart) {
            when {
                activeSos -> PulseDot(Ink.Sos)
                unread -> Box(Modifier.size(8.dp).clip(CircleShape).background(Ink.Signal))
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry is LogEntry.Sos) Tag("SOS", Ink.Sos) else Text(title, color = Ink.Text, fontSize = 15.sp, fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold)
                if (urgent) {
                    Spacer(Modifier.width(8.dp))
                    Tag("URGENT", Ink.Urgent)
                }
                Spacer(Modifier.weight(1f))
                Text(timeLabel(entry.wallMs), color = Ink.TextMute, fontSize = 12.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(text, color = if (unread) Ink.Text else Ink.TextDim, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(status, color = statusColor(entry), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun statusColor(entry: LogEntry): Color = when (entry) {
    is LogEntry.Outgoing -> when (entry.state) {
        DeliveryState.DELIVERED -> Ink.Ok
        DeliveryState.EXPIRED, DeliveryState.UNKNOWN -> Ink.Urgent
        else -> Ink.TextMute
    }
    is LogEntry.Incoming -> if (entry.unread) Ink.Signal else Ink.TextMute
    is LogEntry.Sos -> if (entry.record.phase == OutgoingSosState.Phase.ENDED) Ink.TextMute else Ink.Sos
}

// ---------------------------------------------------------------------------- Message detail

@Composable
fun MessageDetailScreen(bundleIdHex: String) {
    val store = AppStore
    BackHandler { store.back() }
    val entry = remember(store.logVersion, store.readVersion) { store.findEntry(bundleIdHex) }
    LaunchedEffect(bundleIdHex) { if (entry is LogEntry.Incoming) store.markRead(bundleIdHex) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("Message", { store.back() })
        when (entry) {
            is LogEntry.Incoming -> DetailBody("From ${entry.peer}", entry.urgent, entry.wallMs, entry.language, entry.text) {
                Playback(entry.key, entry.text, entry.language)
            }
            is LogEntry.Outgoing -> DetailBody("To ${entry.peer}", entry.urgent, entry.wallMs, entry.language, entry.text) {
                Text(deliveryLabel(entry.state), color = statusColor(entry), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(deliveryExplanation(entry.state, entry.peer), color = Ink.TextDim, fontSize = 13.sp)
            }
            else -> Text("This message is no longer stored on this phone.", color = Ink.TextMute, fontSize = 15.sp, modifier = Modifier.padding(8.dp))
        }
    }
}

private fun deliveryExplanation(state: DeliveryState, peer: String) = when (state) {
    DeliveryState.QUEUED -> "Encrypted and stored. It leaves when another iTantra phone comes in range."
    DeliveryState.RELAYED -> "Another phone is carrying it. $peer hasn't confirmed it yet."
    DeliveryState.DELIVERED -> "$peer's phone confirmed it received the message."
    DeliveryState.EXPIRED -> "It wasn't confirmed before its lifetime ended."
    DeliveryState.UNKNOWN -> "This phone can't tell what happened to it."
}

@Composable
private fun DetailBody(who: String, urgent: Boolean, wallMs: Long, language: String, text: String, footer: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(who, color = Ink.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (urgent) {
                Spacer(Modifier.width(8.dp))
                Tag("URGENT", Ink.Urgent)
            }
        }
        Text("${DATE_TIME.format(Date(wallMs))} · ${languageName(language)}", color = Ink.TextMute, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        Text(text, color = Ink.Text, fontSize = 22.sp, lineHeight = 32.sp)
        Spacer(Modifier.height(32.dp))
        footer()
        Spacer(Modifier.height(24.dp))
    }
}

/** Reflects the real TTS call: speaking while it runs, replay afterwards. Only for languages with a voice. */
@Composable
private fun Playback(key: String, text: String, language: String) {
    val store = AppStore
    val speaking = store.speakingKey == key
    if (!store.speech.canSpeak(language)) {
        Text("No voice for ${languageName(language)} in this build.", color = Ink.TextMute, fontSize = 13.sp)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (speaking) {
            SpeakingBars()
            Spacer(Modifier.width(12.dp))
            Text("Speaking…", color = Ink.TextDim, fontSize = 14.sp)
        } else {
            QuietButton("Replay aloud", { store.speak(key, text, language) }, color = Ink.Signal)
        }
    }
}

/** Decorative bars while the TTS call is running (the engine exposes no output level). */
@Composable
private fun SpeakingBars() {
    LiveWaveform(
        { 0.3f + 0.45f * kotlin.math.abs(kotlin.math.sin(System.currentTimeMillis() / 170.0)).toFloat() },
        Ink.Signal, Modifier.width(80.dp), bars = 10, maxHeight = 28.dp,
    )
}

// ---------------------------------------------------------------------------- SOS request detail

@Composable
fun SosDetailScreen(sosIdHex: String) {
    val store = AppStore
    BackHandler { store.back() }
    val record = store.sosRecords.firstOrNull { it.sosIdHex == sosIdHex }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("SOS request", { store.back() })
        if (record == null) {
            Text("This SOS is no longer on this phone.", color = Ink.TextMute, modifier = Modifier.padding(8.dp))
            return@Column
        }
        val live = record.phase != OutgoingSosState.Phase.ENDED
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag("SOS", Ink.Sos)
                Spacer(Modifier.width(10.dp))
                Text("${DATE_TIME.format(Date(record.createdWallMs))} · ${languageName(record.language)}", color = Ink.TextMute, fontSize = 13.sp)
            }
            Spacer(Modifier.height(24.dp))
            Text(record.text, color = Ink.Text, fontSize = 22.sp, lineHeight = 32.sp)
            Spacer(Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    PulseDot(Ink.Sos, size = 12.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(sosStatus(record), color = if (live) Ink.Text else Ink.TextDim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (record.phase == OutgoingSosState.Phase.SEARCHING) {
                Spacer(Modifier.height(6.dp))
                val progress = when {
                    record.offered == 0 -> "No available responders in range yet. Still looking."
                    record.declined > 0 -> "Asked ${record.offered} nearby · ${record.declined} declined"
                    else -> "Asked ${record.offered} nearby · waiting for an answer"
                }
                Text(progress, color = Ink.TextDim, fontSize = 13.sp)
            }
        }
        Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (record.phase == OutgoingSosState.Phase.CONNECTED) {
                ClayButton("Open conversation", { store.go(Route.Conversation(sosIdHex)) }, Ink.Sos, Ink.SosInk, Modifier.fillMaxWidth())
            }
            if (live) {
                QuietButton("Cancel SOS", { store.cancelSos(sosIdHex) }, Modifier.fillMaxWidth(), color = Ink.Sos)
            }
        }
    }
}
