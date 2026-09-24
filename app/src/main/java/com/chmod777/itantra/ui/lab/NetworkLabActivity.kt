package com.chmod777.itantra.ui.lab

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chmod777.itantra.dtn.DeliveryState
import com.chmod777.itantra.identity.ContactQrCodec
import com.chmod777.itantra.identity.IdentityCapsuleV1
import com.chmod777.itantra.identity.TrustedContact
import com.chmod777.itantra.metrics.BenchmarkParams
import com.chmod777.itantra.protocol.Priority
import com.chmod777.itantra.protocol.SosCategory
import com.chmod777.itantra.protocol.cbor.MalformedInputException
import com.chmod777.itantra.service.EmergencyModeService
import com.chmod777.itantra.service.EmergencyState
import com.chmod777.itantra.service.NetworkingRuntime
import com.chmod777.itantra.sos.IncomingSos
import com.chmod777.itantra.sos.OutgoingSosState
import com.chmod777.itantra.transport.BluetoothPermissions
import com.chmod777.itantra.ui.theme.SIH_iTantraTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DIAGNOSTIC SCREEN (Batch 1). Exercises the v1 networking stack on real phones:
 * Emergency mode, discovery, links, QR trust, trusted DTN messages, SOS and the
 * transport benchmark. It is not product UI.
 */
class NetworkLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NetworkingRuntime.init(this)
        setContent {
            SIH_iTantraTheme {
                Surface(Modifier.fillMaxSize()) { NetworkLabScreen() }
            }
        }
    }
}

private fun runtimePermissions(): Array<String> {
    val base = BluetoothPermissions.requiredRuntimePermissions().toMutableList()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) base += Manifest.permission.POST_NOTIFICATIONS
    return base.toTypedArray()
}

@Composable
private fun NetworkLabScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val identity by NetworkingRuntime.identity.collectAsState()
    val emergency by NetworkingRuntime.emergencyState.collectAsState()
    val session by NetworkingRuntime.session.collectAsState()
    val available by NetworkingRuntime.availableToHelp.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    var permissionNote by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        NetworkingRuntime.dtn.events.collect { refresh++ }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        permissionNote = if (results.values.all { it }) "Permissions granted." else "Denied: " + results.filterValues { !it }.keys.joinToString { it.substringAfterLast('.') }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("iTantra Net Lab (diagnostic)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            Text("Labels: BLE_GATT / RFCOMM links are shown as measured. Nothing here is a product claim.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Section("Emergency mode") {
                Text("State: " + when (val s = emergency) {
                    EmergencyState.Off -> "OFF"
                    EmergencyState.Starting -> "starting…"
                    EmergencyState.On -> "ON"
                    is EmergencyState.Error -> "ERROR — ${s.message}"
                })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (BluetoothPermissions.areGranted(context)) EmergencyModeService.start(context) else permissionLauncher.launch(runtimePermissions())
                    }) { Text("Start") }
                    OutlinedButton(onClick = { EmergencyModeService.stop(context) }) { Text("Stop") }
                    OutlinedButton(onClick = { permissionLauncher.launch(runtimePermissions()) }) { Text("Permissions") }
                }
                if (permissionNote.isNotEmpty()) Text(permissionNote, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = available, onCheckedChange = { NetworkingRuntime.setAvailableToHelp(it) })
                    Text("  Available to help nearby users (SOS responder)")
                }
                session?.let { s ->
                    val status by s.ble.status.collectAsState()
                    Mono("adv=${status.advertising} scan=${status.scanning} err=${status.scanError ?: "-"} gattServer=${status.gattServer}")
                    Mono("my short id=${status.localShortIdHex.take(8)}… links=${status.links}")
                }
            }
        }
        item { IdentitySection(identity?.fingerprint ?: "…", identity?.displayNameHint ?: "") }
        item { ContactsSection(refresh) }
        session?.let { s ->
            item {
                val peers by s.ble.peers.collectAsState()
                val sessions by s.core.sessions.collectAsState()
                Section("Nearby (BLE) and secure sessions") {
                    if (peers.isEmpty()) Text("No iTantra advertisements seen yet.")
                    peers.forEach { Mono("peer ${it.shortIdHex.take(8)} rssi=${it.rssi} seen ${it.lastSeenAgoMs / 1000}s ago linked=${it.linked} sos=${it.sosActive} helper=${it.availableToHelp}") }
                    sessions.forEach { Mono("session ${it.linkId} ${it.transportKind.label} mtu=${it.negotiatedMtu ?: "-"} noise=${"%.0f".format(it.handshakeMs)}ms relay=${it.relaysBundles} helper=${it.availableToHelp}") }
                    RfcommControls(s)
                }
            }
        }
        item { TrustedMessageSection(refresh) }
        item { StoreSection(refresh) }
        session?.let { s -> item { SosSection(s) } }
        session?.let { s -> item { BenchmarkSection(s) } }
        item { Text("Metrics: ${NetworkingRuntime.metrics.file.absolutePath}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 24.dp)) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Mono(text: String) = Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)

@Composable
private fun IdentitySection(fingerprint: String, displayName: String) {
    val context = LocalContext.current
    var showQr by remember { mutableStateOf(false) }
    var name by remember(displayName) { mutableStateOf(displayName) }
    Section("My identity") {
        Mono("Fingerprint: $fingerprint")
        Text("Key protection: ${NetworkingRuntime.keyManager.wrappingKeySecurity}. Reinstalling iTantra creates a new identity; contacts must re-scan your QR.", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(32) }, label = { Text("Display name hint") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedButton(onClick = { NetworkingRuntime.renameSelf(name) }) { Text("Save") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showQr = !showQr }) { Text(if (showQr) "Hide QR" else "Show my QR") }
            OutlinedButton(onClick = {
                val code = NetworkingRuntime.identity.value?.let { ContactQrCodec.encode(it.signedCapsule()) } ?: return@OutlinedButton
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("iTantra contact", code))
            }) { Text("Copy code") }
        }
        if (showQr) {
            val identity by NetworkingRuntime.identity.collectAsState()
            val qr = remember(identity) { identity?.let { qrBitmap(ContactQrCodec.encode(it.signedCapsule())) } }
            qr?.let { Image(it.asImageBitmap(), "My iTantra QR", Modifier.size(260.dp)) }
        }
    }
}

private fun qrBitmap(text: String, size: Int = 600): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) bitmap.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
    return bitmap
}

@Composable
private fun ContactsSection(refresh: Int) {
    var pasted by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<IdentityCapsuleV1?>(null) }
    var localName by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf(emptyList<TrustedContact>()) }
    LaunchedEffect(refresh, pending) { contacts = NetworkingRuntime.database.all() }
    fun parse(text: String) {
        try {
            val capsule = ContactQrCodec.decodeAndVerify(text)
            if (capsule.nodeId.contentEquals(NetworkingRuntime.identity.value?.nodeId)) throw IllegalStateException("That is your own code.")
            pending = capsule
            localName = capsule.displayNameHint
            problem = ""
        } catch (e: MalformedInputException) {
            problem = "Rejected: ${e.message}"
        } catch (e: IllegalStateException) {
            problem = e.message ?: "Rejected"
        }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let { parse(it) } }
    Section("Trusted contacts (QR)") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan an iTantra QR").setBeepEnabled(false)) }) { Text("Scan QR") }
        }
        OutlinedTextField(pasted, { pasted = it }, label = { Text("…or paste ITANTRA1: code") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { parse(pasted) }) { Text("Verify pasted code") }
        if (problem.isNotEmpty()) Text(problem, color = MaterialTheme.colorScheme.error)
        pending?.let { capsule ->
            Text("Signature valid. Compare this fingerprint with the other phone before trusting:")
            Mono(capsule.fingerprint)
            OutlinedTextField(localName, { localName = it.take(32) }, label = { Text("Name on this phone") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    NetworkingRuntime.database.upsert(TrustedContact.fromVerifiedCapsule(capsule, localName, System.currentTimeMillis()))
                    pending = null
                    pasted = ""
                }) { Text("Fingerprint matches — trust") }
                OutlinedButton(onClick = { pending = null }) { Text("Cancel") }
            }
        }
        contacts.forEach { Mono("${it.localName}  ${it.fingerprint}") }
    }
}

@Composable
private fun TrustedMessageSection(refresh: Int) {
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf(emptyList<TrustedContact>()) }
    var selected by remember { mutableStateOf<TrustedContact?>(null) }
    var text by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("en") }
    var urgent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(refresh) { contacts = NetworkingRuntime.database.all() }
    val outgoing = remember(refresh) { NetworkingRuntime.dtn.outgoingMessages() }
    val inbox = remember(refresh) { NetworkingRuntime.dtn.deliveredMessages() }
    Section("Trusted message (DTN)") {
        if (contacts.isEmpty()) Text("Add a trusted contact first. Unknown people cannot be addressed; use SOS instead.")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            contacts.take(4).forEach { c ->
                val isSelected = selected?.nodeId?.contentEquals(c.nodeId) == true
                if (isSelected) Button(onClick = { selected = c }) { Text(c.localName) } else OutlinedButton(onClick = { selected = c }) { Text(c.localName) }
            }
        }
        OutlinedTextField(text, { text = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(language, { language = it.take(8) }, label = { Text("Lang") }, modifier = Modifier.weight(1f), singleLine = true)
            Switch(urgent, { urgent = it })
            Text("Urgent")
        }
        Button(onClick = {
            val contact = selected ?: run { error = "Choose a trusted contact."; return@Button }
            scope.launch {
                error = try {
                    NetworkingRuntime.dtn.createMessage(contact.nodeId, text, language, if (urgent) Priority.URGENT else Priority.NORMAL)
                    text = ""
                    ""
                } catch (e: IllegalArgumentException) {
                    e.message ?: "Cannot send"
                }
            }
        }) { Text("Queue for delivery") }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        Text("Outbox", style = MaterialTheme.typography.labelLarge)
        outgoing.take(10).forEach { m ->
            val name = contacts.firstOrNull { it.nodeId.contentEquals(m.recipientNodeId) }?.localName ?: "?"
            Mono("${time(m.createdWallMs)} → $name [${stateLabel(m.state)}] ${m.text.take(40)}")
        }
        Text("Relayed = another phone is carrying the encrypted message; the recipient has not confirmed it yet.", style = MaterialTheme.typography.bodySmall)
        Text("Inbox (authenticated deliveries only)", style = MaterialTheme.typography.labelLarge)
        inbox.take(10).forEach { m ->
            val name = contacts.firstOrNull { it.nodeId.contentEquals(m.senderNodeId) }?.localName ?: "?"
            Mono("${time(m.receivedWallMs)} ← $name (${m.language}) ${m.text.take(60)}")
        }
    }
}

private fun stateLabel(state: DeliveryState) = when (state) {
    DeliveryState.QUEUED -> "Queued"
    DeliveryState.RELAYED -> "Relayed"
    DeliveryState.DELIVERED -> "Delivered"
    DeliveryState.EXPIRED -> "Expired"
    DeliveryState.UNKNOWN -> "Unknown"
}

private fun time(ms: Long) = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

@Composable
private fun StoreSection(refresh: Int) {
    val bundles = remember(refresh) { NetworkingRuntime.dtn.storedBundles() }
    Section("Carried bundles (ciphertext only)") {
        Mono("count=${bundles.size} bytes=${bundles.sumOf { it.sizeBytes }}")
        bundles.take(12).forEach { b ->
            Mono("${b.storageKey.take(8)} ${b.origin} tokens=${b.copyTokens} hops=${b.hopCount} left=${b.remainingLifetimeMs(NetworkingRuntime.clock) / 60_000}min pending=${b.pendingSplit?.tokens ?: 0}")
        }
        OutlinedButton(onClick = { NetworkingRuntime.dtn.sweep() }) { Text("Run expiry sweep") }
    }
}

@Composable
private fun RfcommControls(s: com.chmod777.itantra.service.EmergencySession) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = !show }) { Text(if (show) "Hide optional RFCOMM" else "Optional RFCOMM (bonded phones)") }
    if (show) {
        s.rfcomm.bondedDevices().forEach { (name, address) ->
            OutlinedButton(onClick = { s.rfcomm.connect(address) }) { Text("RFCOMM → $name") }
        }
    }
}

@Composable
private fun SosSection(s: com.chmod777.itantra.service.EmergencySession) {
    val scope = rememberCoroutineScope()
    val outgoing by s.sos.outgoing.collectAsState()
    val incoming by s.sos.incomingOffers.collectAsState()
    var category by remember { mutableStateOf(SosCategory.MEDICAL) }
    var language by remember { mutableStateOf("en") }
    var chat by remember { mutableStateOf("") }
    Section("SOS") {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SosCategory.entries.forEach { c ->
                if (c == category) Button(onClick = { category = c }) { Text(c.label.take(5)) } else OutlinedButton(onClick = { category = c }) { Text(c.label.take(5)) }
            }
        }
        OutlinedTextField(language, { language = it.take(8) }, label = { Text("Language") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { s.sos.startSos(category, language) }) { Text("I NEED HELP") }
            OutlinedButton(onClick = { s.sos.cancelSos() }) { Text("Cancel SOS") }
        }
        outgoing?.let { o ->
            Text(if (o.phase == OutgoingSosState.Phase.SEARCHING) o.phase.label else "SOS: ${o.phase.label}")
            Mono("wave=${o.wave} offered=${o.offeredCount} declined=${o.declinedCount}")
            o.trust?.let { Text(it.label, style = MaterialTheme.typography.titleSmall) }
            o.shortAuthString?.let {
                Mono("Compare code with helper: $it")
                OutlinedButton(onClick = { s.sos.markNearbyVerified(o.sosIdHex) }) { Text("Codes match in person") }
            }
            o.chat.forEach { Mono((if (it.fromMe) "me: " else "helper: ") + it.text) }
            if (o.phase == OutgoingSosState.Phase.CONNECTED) ChatInput(chat, { chat = it }) { scope.launch { s.sos.sendChat(o.sosIdHex, chat, language); chat = "" } }
        }
        incoming.forEach { offer -> IncomingSosCard(s, offer, language) }
    }
}

@Composable
private fun IncomingSosCard(s: com.chmod777.itantra.service.EmergencySession, offer: IncomingSos, language: String) {
    val scope = rememberCoroutineScope()
    var chat by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (offer.connection.name == "RELAYED") "Relayed emergency request" else "Someone nearby is requesting help", style = MaterialTheme.typography.titleSmall)
            Mono("Category: ${offer.category.label}  Language: ${offer.language}  Age: ${offer.ageMs / 1000}s  Signal: ${offer.proximity ?: "n/a"}")
            Text("${offer.connection.label} — ${offer.trust.label}")
            Mono("Phase: ${offer.phase}")
            offer.shortAuthString?.let { Mono("Compare code: $it") }
            if (offer.phase == IncomingSos.Phase.OFFERED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { s.sos.accept(offer.sosIdHex) } }) { Text("ACCEPT") }
                    OutlinedButton(onClick = { scope.launch { s.sos.decline(offer.sosIdHex) } }) { Text("DECLINE") }
                }
            }
            if (offer.phase == IncomingSos.Phase.RELAYED_NOTICE) Text("No live connection is available for relayed requests in v1.", style = MaterialTheme.typography.bodySmall)
            offer.chat.forEach { Mono((if (it.fromMe) "me: " else "them: ") + it.text) }
            if (offer.phase == IncomingSos.Phase.ACTIVE) {
                ChatInput(chat, { chat = it }) { scope.launch { s.sos.sendChat(offer.sosIdHex, chat, language); chat = "" } }
                OutlinedButton(onClick = { s.sos.markNearbyVerified(offer.sosIdHex) }) { Text("Codes match in person") }
            }
        }
    }
}

@Composable
private fun ChatInput(value: String, onChange: (String) -> Unit, onSend: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value, onChange, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Encrypted chat") })
        Button(onClick = onSend, enabled = value.isNotBlank()) { Text("Send") }
    }
}

@Composable
private fun BenchmarkSection(s: com.chmod777.itantra.service.EmergencySession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by s.core.sessions.collectAsState()
    var target by remember { mutableStateOf<String?>(null) }
    var trials by remember { mutableStateOf("20") }
    var hops by remember { mutableStateOf("1") }
    var payload by remember { mutableStateOf("200") }
    var echo by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf("") }
    Section("Transport benchmark (probes; no STT/TTS)") {
        Text("Hops=2 on phone A measures A→B→C→B→A on A's clock; B reports its own processing time.", style = MaterialTheme.typography.bodySmall)
        sessions.forEach { info ->
            val chosen = target == info.peerSessionKey
            val label = "${info.transportKind.label} ${info.linkId}"
            if (chosen) Button(onClick = { target = info.peerSessionKey }) { Text(label) } else OutlinedButton(onClick = { target = info.peerSessionKey }) { Text(label) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField("Trials", trials, Modifier.weight(1f)) { trials = it }
            NumberField("Hops", hops, Modifier.weight(1f)) { hops = it }
            NumberField("Payload B", payload, Modifier.weight(1f)) { payload = it }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(echo, { echo = it }); Text("  Echo full payload back") }
        Button(enabled = !running, onClick = {
            val handle = target?.let { s.core.session(it) } ?: run { summary = "Pick a session first."; return@Button }
            running = true
            scope.launch {
                summary = try {
                    val params = BenchmarkParams(
                        trials = trials.toIntOrNull()?.coerceIn(1, 500) ?: 20,
                        hops = hops.toIntOrNull()?.coerceIn(1, 4) ?: 1,
                        payloadBytes = payload.toIntOrNull()?.coerceIn(0, NetworkingRuntime.config.maxProbePayloadBytes) ?: 200,
                        echoPayload = echo,
                        timeoutMs = NetworkingRuntime.config.probeTimeoutMs,
                    )
                    NetworkingRuntime.metrics.record("bench_start", mapOf("trials" to params.trials, "hops" to params.hops, "payload" to params.payloadBytes, "link" to handle.linkId))
                    s.benchmark.run(handle, params).describe().also { NetworkingRuntime.metrics.flush() }
                } catch (e: Exception) {
                    "Benchmark aborted: ${e.message}"
                } finally {
                    running = false
                }
            }
        }) { Text(if (running) "Running…" else "Run benchmark") }
        if (summary.isNotEmpty()) {
            Mono(summary)
            OutlinedButton(onClick = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("benchmark", summary))
            }) { Text("Copy summary") }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.filter(Char::isDigit).take(6)) }, label = { Text(label) }, modifier = modifier, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
