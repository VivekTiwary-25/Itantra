package com.chmod777.itantra.demo.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chmod777.itantra.demo.CaptureKind
import com.chmod777.itantra.demo.DEMO_LANGUAGES
import com.chmod777.itantra.demo.DemoAudio
import com.chmod777.itantra.demo.DemoEvents
import com.chmod777.itantra.demo.DemoNotifications
import com.chmod777.itantra.demo.DemoStore
import com.chmod777.itantra.demo.IncomingSpec
import com.chmod777.itantra.demo.MessageMode
import com.chmod777.itantra.demo.RoleDefaults
import com.chmod777.itantra.demo.demoLanguage
import kotlinx.coroutines.delay
import java.io.File

/**
 * Hidden director console (long-press the iTantra wordmark on Home). Utilitarian
 * on purpose: it is never filmed. Everything here persists across restarts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DirectorConsole(onClose: () -> Unit) {
    val context = LocalContext.current
    val language by DemoStore.language.collectAsState()
    val transcripts by DemoStore.transcripts.collectAsState()
    val form by DemoStore.incomingForm.collectAsState()
    val armed by DemoStore.armed.collectAsState()
    val messages by DemoStore.messages.collectAsState()
    val contacts by DemoStore.contacts.collectAsState()
    var toast by remember { mutableStateOf<String?>(null) }

    // Readiness is re-read every second so returning from system settings updates it.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val notifAllowed = remember(tick) { DemoNotifications.permissionGranted(context) }
    val notifOn = remember(tick) { DemoNotifications.enabledInSettings(context) }
    val exactOk = remember(tick) { DemoEvents.canScheduleExact(context) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    LaunchedEffect(Unit) {
        if (!notifAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(toast) { if (toast != null) { delay(2500); toast = null } }

    fun update(block: IncomingSpec.() -> IncomingSpec) = DemoStore.setIncomingForm(form.block())

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F14))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopBar(title = "Director console", onBack = onClose)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Demo-only controls · actor: ${DemoStore.actor.displayName} · ${context.packageName}",
                color = Ink.quiet,
                fontSize = 12.sp
            )
            toast?.let { Text(it, color = Ink.gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }

            Section("Before filming") {
                StatusRow(
                    label = "Notification permission",
                    ok = notifAllowed,
                    okText = "Allowed",
                    badText = "Not allowed",
                    action = if (!notifAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Allow" else null,
                    onAction = { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
                StatusRow(
                    label = "Notifications switched on",
                    ok = notifOn,
                    okText = "On",
                    badText = "Off in Settings",
                    action = "Settings",
                    onAction = { context.startActivity(DemoNotifications.settingsIntent(context)) }
                )
                StatusRow(
                    label = "Exact short timers",
                    ok = exactOk,
                    okText = "OK",
                    badText = "Limited",
                    action = if (!exactOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Allow" else null,
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
                Hint(
                    "If the notification arrives silently without dropping down over other apps, open Settings " +
                        "and turn on banner / floating notifications for iTantra (both Messages and SOS)."
                )
            }

            Section("Language & mock transcripts") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DEMO_LANGUAGES.forEach { l ->
                        Chip(l.label, selected = l.code == language) { DemoStore.setLanguage(l.code) }
                    }
                }
                val name = demoLanguage(language).englishName
                val pttText = remember(transcripts, language) { DemoStore.transcript(CaptureKind.PTT, language) }
                val hfText = remember(transcripts, language) { DemoStore.transcript(CaptureKind.HANDS_FREE, language) }
                ConsoleField(
                    label = "Push-to-talk transcript · $name",
                    value = pttText,
                    onChange = { DemoStore.setTranscript(CaptureKind.PTT, language, it) },
                    minLines = 2
                )
                ConsoleField(
                    label = "Hands-free transcript · $name",
                    value = hfText,
                    onChange = { DemoStore.setTranscript(CaptureKind.HANDS_FREE, language, it) },
                    minLines = 2
                )
                SmallButton("Reset all transcripts to defaults") { DemoStore.resetTranscripts() }
            }

            Section("Simulate incoming message") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleDefaults.presets(DemoStore.actor).forEach { p ->
                        Chip("Preset: ${p.label}", selected = form.sender == p.sender && form.body == p.body && form.mode == p.mode) {
                            update { copy(sender = p.sender, body = p.body, mode = p.mode) }
                        }
                    }
                }
                ConsoleField("From", form.sender, { v -> update { copy(sender = v) } })
                ConsoleField("Message", form.body, { v -> update { copy(body = v) } }, minLines = 2)
                Text("Mode", color = Ink.muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        MessageMode.NORMAL to "Normal",
                        MessageMode.URGENT to "Urgent",
                        MessageMode.SOS to "SOS request (Accept/Decline)",
                        MessageMode.SOS_REPLY to "SOS reply",
                    ).forEach { (m, label) -> Chip(label, selected = form.mode == m) { update { copy(mode = m) } } }
                }
                AudioClipControls(
                    uri = form.audioUri,
                    onUri = { u -> update { copy(audioUri = u) } },
                    onMessage = { toast = it }
                )
                Text("Delay", color = Ink.muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 10, 15, 30).forEach { s ->
                        Chip("$s s", selected = form.delaySeconds == s) { update { copy(delaySeconds = s) } }
                    }
                }
                var delayText by remember(form.delaySeconds) { mutableStateOf(form.delaySeconds.toString()) }
                ConsoleField(
                    label = "Custom delay (seconds)",
                    value = delayText,
                    onChange = { v ->
                        delayText = v.filter(Char::isDigit).take(4)
                        delayText.toIntOrNull()?.let { s -> update { copy(delaySeconds = s) } }
                    },
                    numeric = true
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Post Android notification", color = Ink.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = form.notify,
                        onCheckedChange = { v -> update { copy(notify = v) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Ink.gold, checkedThumbColor = Ink.onGold)
                    )
                }
                if (form.notify && !notifAllowed) {
                    Text("Notifications are not allowed yet — grant them above first.", color = Ink.sosLight, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Arm · ${form.delaySeconds} s", Ink.gold, Ink.onGold, Modifier.weight(1f)) {
                        DemoEvents.arm(context, form)
                        toast = "Armed. Leave the app now — fires in ${form.delaySeconds} s."
                    }
                    BigButton("Trigger now", Ink.surfaceRaised, Ink.text, Modifier.weight(1f)) {
                        if (DemoEvents.triggerNow(context, form)) toast = "Delivered."
                    }
                }
                ArmedStatus(armed?.fireAtMillis, armed?.spec?.sender) { DemoEvents.cancel(context, clearShownNotifications = false) }
            }

            Section("Reset for another take") {
                Text("${messages.size} message(s) in Logs · trusted: ${contacts.joinToString()}", color = Ink.muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Clear Logs", Ink.surfaceRaised, Ink.text, Modifier.weight(1f)) {
                        DemoStore.clearLogs()
                        DemoNotifications.cancelAll(context)
                        toast = "Logs cleared."
                    }
                    BigButton("Cancel pending", Ink.surfaceRaised, Ink.text, Modifier.weight(1f)) {
                        DemoEvents.cancel(context, clearShownNotifications = true)
                        toast = "Pending timer and notifications cancelled."
                    }
                }
                BigButton("Reset everything to ${DemoStore.actor.displayName} defaults", Ink.sosSurface, Ink.sosLight, Modifier.fillMaxWidth()) {
                    DemoEvents.cancel(context, clearShownNotifications = true)
                    DemoAudio.release()
                    DemoStore.resetAll()
                    toast = "Reset to defaults."
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ArmedStatus(fireAt: Long?, sender: String?, onCancel: () -> Unit) {
    if (fireAt == null) {
        Text("Nothing armed.", color = Ink.quiet, fontSize = 13.sp)
        return
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(fireAt) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    val left = ((fireAt - now) / 1000.0).coerceAtLeast(0.0)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            "Armed: message from ${sender ?: "?"} in %.0f s".format(left),
            color = Ink.gold,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        SmallButton("Cancel", onCancel)
    }
}

/** Pick an existing audio file or record one right here (e.g. Vachana saying her line). */
@Composable
private fun AudioClipControls(uri: String?, onUri: (String?) -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onUri(picked.toString())
        }
    }
    var recorder by remember { mutableStateOf<ClipRecorder?>(null) }
    DisposableEffect(Unit) { onDispose { recorder?.stop() } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) recorder = ClipRecorder.start(context) ?: run { onMessage("Could not start recording."); null }
    }
    val playing by DemoAudio.playingUri.collectAsState()

    Text("Audio clip (optional)", color = Ink.muted, fontSize = 13.sp)
    Text(
        when {
            recorder != null -> "● Recording…"
            uri == null -> "No clip — detail screen shows no Play button."
            DemoAudio.isAvailable(context, uri) -> "Clip: ${Uri.parse(uri).lastPathSegment ?: uri}"
            else -> "Clip missing — it will be hidden on the detail screen."
        },
        color = if (recorder != null) Ink.sosLight else Ink.body,
        fontSize = 14.sp
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallButton("Choose file") { picker.launch(arrayOf("audio/*")) }
        if (recorder == null) {
            SmallButton("Record") {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    recorder = ClipRecorder.start(context) ?: run { onMessage("Could not start recording."); null }
                } else {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        } else {
            SmallButton("Stop") {
                val file = recorder?.stop()
                recorder = null
                if (file != null) onUri(Uri.fromFile(file).toString()) else onMessage("Recording failed.")
            }
        }
        if (uri != null && recorder == null) {
            SmallButton(if (playing == uri) "Pause test" else "Play test") {
                if (!DemoAudio.toggle(context, uri)) onMessage("Could not play this clip.")
            }
            SmallButton("Remove") { DemoAudio.release(); onUri(null) }
        }
    }
}

private class ClipRecorder(private val recorder: MediaRecorder, private val file: File) {
    fun stop(): File? = runCatching {
        recorder.stop()
        recorder.release()
        file
    }.getOrElse {
        runCatching { recorder.release() }
        null
    }

    companion object {
        fun start(context: Context): ClipRecorder? {
            val dir = File(context.filesDir, "clips").apply { mkdirs() }
            val file = File(dir, "clip-${System.currentTimeMillis()}.m4a")
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            return runCatching {
                r.setAudioSource(MediaRecorder.AudioSource.MIC)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioSamplingRate(44_100)
                r.setAudioEncodingBitRate(128_000)
                r.setOutputFile(file.absolutePath)
                r.prepare()
                r.start()
                ClipRecorder(r, file)
            }.getOrElse {
                runCatching { r.release() }
                null
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = Ink.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, okText: String, badText: String, action: String?, onAction: () -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Ink.body, fontSize = 14.sp)
            Text(if (ok) "✓ $okText" else "✕ $badText", color = if (ok) Color(0xFF5EE0A0) else Ink.sosLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (action != null) SmallButton(action, onAction)
    }
}

@Composable
private fun Hint(text: String) = Text(text, color = Ink.quiet, fontSize = 12.sp, lineHeight = 16.sp)

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Ink.onGold else Ink.body,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Ink.gold else Ink.inset)
            .border(1.dp, if (selected) Ink.gold else Ink.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Ink.gold,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.inset)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun BigButton(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun ConsoleField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    minLines: Int = 1,
    numeric: Boolean = false,
) {
    // Local state keeps typing synchronous; external changes (presets, language switch) are pulled in.
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != text) text = value }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text(label) },
        minLines = minLines,
        singleLine = minLines == 1,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Ink.text,
            unfocusedTextColor = Ink.body,
            focusedBorderColor = Ink.gold,
            unfocusedBorderColor = Ink.line,
            focusedLabelColor = Ink.gold,
            unfocusedLabelColor = Ink.muted,
            cursorColor = Ink.gold,
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
