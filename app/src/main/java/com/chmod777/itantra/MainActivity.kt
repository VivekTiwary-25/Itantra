package com.chmod777.itantra

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chmod777.itantra.ui.theme.SIH_iTantraTheme
import com.chmod777.itantra.network.ItantraNetwork
import com.chmod777.itantra.network.RecipientOption
import com.chmod777.itantra.service.EmergencyState
import com.chmod777.itantra.transport.BluetoothPermissions
import com.chmod777.itantra.ui.lab.NetworkLabActivity
import com.chmod777.itantra.transport.BluetoothRfcommTransport
import com.chmod777.itantra.transport.RfcommConnectionState
import com.chmod777.itantra.transport.SendMessageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import speech.SpeechEngine
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opens the networking store and identity before any screen reads them.
        ItantraNetwork.init(this)
        enableEdgeToEdge()
        setContent {
            SIH_iTantraTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF07111C)
                ) { innerPadding ->
                    ITantraApp(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

private enum class Screen { MAIN, HANDS_FREE, NEW_MESSAGE, LOGS, MESSAGE_DETAIL }

private enum class MessageDirection { SENT, RECEIVED }

private data class Message(
    val text: String,
    val date: String,
    val timestamp: String,
    val direction: MessageDirection,
    val isRead: Boolean,
    val languageCode: String,
    val transportMessageId: Long? = null,
    val deliveryState: MessageDeliveryState? = null,
    /** v1 networking bundle handle for sent messages; null for legacy RFCOMM rows. */
    val networkBundleId: String? = null,
    /** Trusted-contact name on this phone (recipient for sent, sender for received). */
    val peerName: String? = null,
)

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private const val LATENCY_TAG = "ITANTRA_LATENCY"

// Spoken-language options for transcription/TTS. Codes and display names
// per docs/CONTRACTS.md's language table.
private val SUPPORTED_LANGUAGES = listOf(
    "en" to "English",
    "hi" to "Hindi",
    "gu" to "Gujarati",
    "mr" to "Marathi",
    "ta" to "Tamil",
    "te" to "Telugu",
    "or" to "Odia",
    "bn" to "Bengali"
)

// Sample text per language for the temporary Test TTS control. Typing Devanagari
// or Bengali on the test phone needs a matching keyboard, so the control offers a
// ready sentence whenever the operator has not typed their own.
private val TTS_SAMPLE_TEXT = mapOf(
    "en" to "this is a test message",
    "hi" to "यह एक परीक्षण संदेश है",
    "gu" to "આ એક પરીક્ષણ સંદેશ છે",
    "mr" to "हा एक चाचणी संदेश आहे",
    "ta" to "இது ஒரு சோதனை செய்தி",
    "te" to "ఇది ఒక పరీక్ష సందేశం",
    "or" to "ଏହା ଏକ ପରୀକ୍ଷା ବାର୍ତ୍ତା",
    "bn" to "এটি একটি পরীক্ষা বার্তা"
)

private val MessageListSaver = Saver<SnapshotStateList<Message>, ArrayList<Bundle>>(
    save = { messages ->
        ArrayList(messages.map { message ->
            Bundle().apply {
                putString("text", message.text)
                putString("date", message.date)
                putString("timestamp", message.timestamp)
                putString("direction", message.direction.name)
                putBoolean("isRead", message.isRead)
                putString("languageCode", message.languageCode)
                message.transportMessageId?.let { putLong("transportMessageId", it) }
                putString("deliveryState", message.deliveryState?.name)
                putString("networkBundleId", message.networkBundleId)
                putString("peerName", message.peerName)
            }
        })
    },
    restore = { savedMessages ->
        mutableStateListOf<Message>().apply {
            savedMessages.forEach { saved ->
                add(
                    Message(
                        text = checkNotNull(saved.getString("text")),
                        date = checkNotNull(saved.getString("date")),
                        timestamp = checkNotNull(saved.getString("timestamp")),
                        direction = MessageDirection.valueOf(checkNotNull(saved.getString("direction"))),
                        isRead = saved.getBoolean("isRead"),
                        languageCode = checkNotNull(saved.getString("languageCode")),
                        transportMessageId = if (saved.containsKey("transportMessageId")) {
                            saved.getLong("transportMessageId")
                        } else {
                            null
                        },
                        deliveryState = saved.getString("deliveryState")?.let(MessageDeliveryState::valueOf),
                        networkBundleId = saved.getString("networkBundleId"),
                        peerName = saved.getString("peerName"),
                    )
                )
            }
        }
    }
)

@Composable
fun ITantraApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val transport = remember { BluetoothRfcommTransport(context) }
    // The v1 BLE/DTN stack is the default. The legacy RFCOMM demo is opt-in and kept only
    // as a compatibility path: it relays in plaintext and its ACK proves only the next hop.
    var useLegacyTransport by rememberSaveable { mutableStateOf(false) }
    var pendingEmergencyStart by remember { mutableStateOf(false) }
    val emergencyState by ItantraNetwork.emergencyState.collectAsState()
    var recipients by remember { mutableStateOf(emptyList<RecipientOption>()) }
    var selectedRecipientHex by rememberSaveable { mutableStateOf<String?>(null) }
    var transportState by remember { mutableStateOf<RfcommConnectionState>(RfcommConnectionState.Idle) }
    var pairedDevices by remember { mutableStateOf(emptyList<com.chmod777.itantra.transport.PairedBluetoothDevice>()) }
    val requestBluetoothPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (BluetoothPermissions.areGranted(context)) {
            if (useLegacyTransport) {
                pairedDevices = BluetoothPermissions.pairedDevices(context)
                transport.listen { transportState = it }
            }
            if (pendingEmergencyStart) ItantraNetwork.startEmergencyMode(context)
        }
        pendingEmergencyStart = false
    }
    val bluetoothPermissionRequest = {
        val permissions = BluetoothPermissions.requiredRuntimePermissions().toMutableList()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        requestBluetoothPermission.launch(permissions.toTypedArray())
    }
    DisposableEffect(transport) {
        onDispose { transport.close() }
    }
    val recordingFile = remember { File(context.filesDir, "recording.wav") }
    val recorder = remember { PcmRecorder(recordingFile) }
    val speech = remember { SpeechEngine(context) }
    DisposableEffect(Unit) {
        onDispose { speech.release() }
    }
    val startRecording = {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMicPermission) recorder.start()
        hasMicPermission
    }
    val stopRecording = { recorder.stop() }
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
    var draft by rememberSaveable { mutableStateOf("") }
    var draftLanguageCode by rememberSaveable { mutableStateOf("en") }
    var selectedLanguageCode by rememberSaveable { mutableStateOf("en") }
    var isTranscribing by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var sendError by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMessageIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val messages = rememberSaveable(saver = MessageListSaver) {
        val today = LocalDate.now().format(DATE_FORMAT)
        mutableStateListOf(
            Message("Water rising near the school", today, "10:42", MessageDirection.RECEIVED, isRead = false, languageCode = "en"),
            Message("Six people at the temple", today, "10:43", MessageDirection.RECEIVED, isRead = false, languageCode = "en"),
            Message("Need medical supplies", today, "10:44", MessageDirection.RECEIVED, isRead = false, languageCode = "en")
        )
    }
    val unreadCount = messages.count { it.direction == MessageDirection.RECEIVED && !it.isRead }

    val requestMicPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        if (!BluetoothPermissions.areGranted(context)) bluetoothPermissionRequest()
    }
    LaunchedEffect(useLegacyTransport) {
        if (useLegacyTransport && BluetoothPermissions.areGranted(context)) {
            pairedDevices = BluetoothPermissions.pairedDevices(context)
            transport.listen { transportState = it }
        }
    }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(transport) {
        transport.onMessageReceived { received ->
            val text = received.message.text
            val languageCode = received.message.languageCode
            val now = LocalDateTime.now()
            messages.add(
                Message(
                    text = text,
                    date = now.format(DATE_FORMAT),
                    timestamp = now.format(TIME_FORMAT),
                    direction = MessageDirection.RECEIVED,
                    isRead = false,
                    languageCode = languageCode
                )
            )
            transport.sendAcknowledgement(received.message.messageId, received.sourcePeerAddress)
            // Speak only once the text is in Logs and the ACK is away. Model loading
            // takes seconds, so it runs off the RFCOMM reader thread, and every
            // failure stays inside this block: transport must survive a mute phone.
            coroutineScope.launch(Dispatchers.Default) {
                try {
                    Log.d(LATENCY_TAG, "[TTS] received-message speak lang=$languageCode")
                    speech.speak(text, languageCode)
                } catch (e: Throwable) {
                    Log.e(LATENCY_TAG, "[TTS] received-message speak failed lang=$languageCode", e)
                }
            }
        }
        transport.onAcknowledgementReceived { acknowledgedMessageId ->
            val index = messages.indexOfFirst {
                matchesAcknowledgement(it.transportMessageId, acknowledgedMessageId)
            }
            if (index >= 0) {
                messages[index] = messages[index].copy(deliveryState = legacyAcknowledgedState())
            }
        }
    }

    // v1 stack receive path: only authenticated end-recipient deliveries arrive here, so a
    // relay phone never shows or speaks messages it merely carries (execution spec §8.4).
    LaunchedEffect(Unit) {
        ItantraNetwork.pendingDeliveries.collect { deliveries ->
            deliveries.forEach { delivery ->
                ItantraNetwork.acknowledgeDelivery(delivery.bundleIdHex)
                val now = LocalDateTime.now()
                messages.add(
                    Message(
                        text = delivery.text,
                        date = now.format(DATE_FORMAT),
                        timestamp = now.format(TIME_FORMAT),
                        direction = MessageDirection.RECEIVED,
                        isRead = false,
                        languageCode = delivery.languageCode,
                        peerName = delivery.senderName,
                    )
                )
                coroutineScope.launch(Dispatchers.Default) {
                    try {
                        ItantraNetwork.recordProductTiming("product_tts_start", mapOf("lang" to delivery.languageCode))
                        speech.speak(delivery.text, delivery.languageCode)
                        ItantraNetwork.recordProductTiming("product_tts_returned", mapOf("lang" to delivery.languageCode))
                    } catch (e: Throwable) {
                        Log.e(LATENCY_TAG, "[TTS] delivered-message speak failed lang=${delivery.languageCode}", e)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        // Catch up on state changes that happened while this screen was not composed.
        val known = ItantraNetwork.currentStates()
        messages.forEachIndexed { index, message ->
            known[message.networkBundleId]?.let { messages[index] = message.copy(deliveryState = it.toMessageDeliveryState()) }
        }
        ItantraNetwork.outgoingStateChanges.collect { (bundleIdHex, state) ->
            val index = messages.indexOfFirst { it.networkBundleId == bundleIdHex }
            if (index >= 0) messages[index] = messages[index].copy(deliveryState = state.toMessageDeliveryState())
        }
    }

    // Shared by PTT-release and Hands-free Done: both route the same WAV
    // through speech.transcribe() off the main thread, then open the editor.
    val startTranscription: (String) -> Unit = { source ->
        if (!isTranscribing && recordingFile.length() > WAV_HEADER_SIZE) {
            isTranscribing = true
            coroutineScope.launch {
                val releaseElapsedMs = SystemClock.elapsedRealtime()
                Log.d(LATENCY_TAG, "[$source] release t=$releaseElapsedMs")
                ItantraNetwork.recordProductTiming("product_speech_end", mapOf("source" to source))
                val text = withContext(Dispatchers.Default) {
                    speech.transcribe(recordingFile.absolutePath, selectedLanguageCode)
                }
                val readyElapsedMs = SystemClock.elapsedRealtime()
                Log.d(
                    LATENCY_TAG,
                    "[$source] transcript ready deltaMs=${readyElapsedMs - releaseElapsedMs} " +
                        "wavBytes=${recordingFile.length()}"
                )
                ItantraNetwork.recordProductTiming("product_transcript_ready", mapOf("source" to source))
                draft = text
                draftLanguageCode = selectedLanguageCode
                sendError = null
                recipients = ItantraNetwork.recipients()
                isTranscribing = false
                screen = Screen.NEW_MESSAGE
            }
        }
    }

    when (screen) {
        Screen.MAIN -> MainScreen(
            unreadCount = unreadCount,
            recordingFile = recordingFile,
            onStartRecording = startRecording,
            onStopRecording = stopRecording,
            onOpenHandsFree = { screen = Screen.HANDS_FREE },
            onOpenText = {
                draft = ""
                draftLanguageCode = "en"
                sendError = null
                recipients = ItantraNetwork.recipients()
                screen = Screen.NEW_MESSAGE
            },
            onOpenLogs = { screen = Screen.LOGS },
            onPttReleased = { startTranscription("PTT") },
            selectedLanguageCode = selectedLanguageCode,
            onLanguageChange = { selectedLanguageCode = it },
            isTranscribing = isTranscribing,
            speech = speech,
            transportState = transportState,
            pairedDevices = pairedDevices,
            onConnect = { address -> transport.connect(address) { transportState = it } },
            onListen = { transport.listen { transportState = it } },
            emergencyState = emergencyState,
            onStartEmergency = {
                if (BluetoothPermissions.areGranted(context)) {
                    ItantraNetwork.startEmergencyMode(context)
                } else {
                    pendingEmergencyStart = true
                    bluetoothPermissionRequest()
                }
            },
            onStopEmergency = { ItantraNetwork.stopEmergencyMode(context) },
            onOpenNetwork = { context.startActivity(android.content.Intent(context, NetworkLabActivity::class.java)) },
            useLegacyTransport = useLegacyTransport,
            onLegacyTransportChange = { useLegacyTransport = it },
            modifier = modifier
        )

        Screen.HANDS_FREE -> HandsFreeScreen(
            onStartRecording = startRecording,
            onStopRecording = stopRecording,
            onBack = { screen = Screen.MAIN },
            onDone = { startTranscription("HANDSFREE") },
            isTranscribing = isTranscribing,
            modifier = modifier
        )

        Screen.LOGS -> LogsScreen(
            messages = messages,
            onBack = { screen = Screen.MAIN },
            onOpenMessage = { index ->
                val message = messages[index]
                if (message.direction == MessageDirection.RECEIVED && !message.isRead) {
                    messages[index] = message.copy(isRead = true)
                }
                selectedMessageIndex = index
                screen = Screen.MESSAGE_DETAIL
            },
            modifier = modifier
        )

        Screen.NEW_MESSAGE -> NewMessageScreen(
            draft = draft,
            onDraftChange = { draft = it },
            isSending = isSending,
            sendError = sendError,
            onBack = {
                draft = ""
                sendError = null
                screen = Screen.MAIN
            },
            recipients = recipients,
            selectedRecipientHex = selectedRecipientHex,
            onSelectRecipient = { selectedRecipientHex = it },
            useLegacyTransport = useLegacyTransport,
            onSend = sendNew@{ text ->
                if (!useLegacyTransport) {
                    val recipient = recipients.firstOrNull { it.nodeIdHex == selectedRecipientHex }
                    if (recipient == null) {
                        sendError = "Choose a trusted contact. Unknown people cannot be addressed; use SOS instead."
                        return@sendNew
                    }
                    ItantraNetwork.queueTrustedMessage(recipient.nodeIdHex, text, draftLanguageCode)
                        .onSuccess { bundleIdHex ->
                            val now = LocalDateTime.now()
                            messages.add(
                                Message(
                                    text = text,
                                    date = now.format(DATE_FORMAT),
                                    timestamp = now.format(TIME_FORMAT),
                                    direction = MessageDirection.SENT,
                                    isRead = true,
                                    languageCode = draftLanguageCode,
                                    deliveryState = MessageDeliveryState.QUEUED,
                                    networkBundleId = bundleIdHex,
                                    peerName = recipient.name,
                                )
                            )
                            draft = ""
                            draftLanguageCode = "en"
                            screen = Screen.MAIN
                        }
                        .onFailure { sendError = it.message ?: "Could not queue the message." }
                    return@sendNew
                }
                isSending = true
                sendError = null
                transport.sendMessage(text, draftLanguageCode) { result ->
                    val failure = result.failureMessage()
                    if (failure != null) {
                        if (result is SendMessageResult.Error) Log.e("ITANTRA_TRANSPORT", result.message)
                        sendError = failure
                        isSending = false
                        return@sendMessage
                    }
                    result as SendMessageResult.Sent
                    val now = LocalDateTime.now()
                    messages.add(
                        Message(
                            text = text,
                            date = now.format(DATE_FORMAT),
                            timestamp = now.format(TIME_FORMAT),
                            direction = MessageDirection.SENT,
                            isRead = true,
                            languageCode = draftLanguageCode,
                            transportMessageId = result.message.messageId,
                            deliveryState = MessageDeliveryState.LEGACY_SENT,
                        )
                    )
                    draft = ""
                    draftLanguageCode = "en"
                    isSending = false
                    screen = Screen.MAIN
                }
            },
            modifier = modifier
        )

        Screen.MESSAGE_DETAIL -> MessageDetailScreen(
            message = messages[checkNotNull(selectedMessageIndex)],
            onBack = { screen = Screen.LOGS },
            modifier = modifier
        )
    }
}

@Composable
private fun TransportControls(
    state: RfcommConnectionState,
    pairedDevices: List<com.chmod777.itantra.transport.PairedBluetoothDevice>,
    onListen: () -> Unit,
    onConnect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = when (state) {
                RfcommConnectionState.Idle -> "Bluetooth: idle"
                RfcommConnectionState.Listening -> "Bluetooth: listening"
                is RfcommConnectionState.Connections -> "Bluetooth: ${state.peers.size} connected"
                is RfcommConnectionState.Connecting -> "Bluetooth: connecting to ${state.peerName}"
                is RfcommConnectionState.Disconnected -> "Bluetooth: disconnected"
                is RfcommConnectionState.Error -> "Bluetooth error: ${state.message}"
                is RfcommConnectionState.Connected -> "Bluetooth: connected to ${state.peerName}"
            },
            color = Color(0xFF91A2B4)
        )
        Button(onClick = onListen) { Text("LISTEN FOR BLUETOOTH") }
        pairedDevices.forEach { device ->
            Button(onClick = { onConnect(device.address) }) {
                Text("CONNECT: ${device.name}")
            }
        }
    }
}

@Composable
private fun MainScreen(
    unreadCount: Int,
    recordingFile: File,
    onStartRecording: () -> Boolean,
    onStopRecording: () -> Unit,
    onOpenHandsFree: () -> Unit,
    onOpenText: () -> Unit,
    onOpenLogs: () -> Unit,
    onPttReleased: () -> Unit,
    selectedLanguageCode: String,
    onLanguageChange: (String) -> Unit,
    isTranscribing: Boolean,
    speech: SpeechEngine,
    transportState: RfcommConnectionState,
    pairedDevices: List<com.chmod777.itantra.transport.PairedBluetoothDevice>,
    onConnect: (String) -> Unit,
    onListen: () -> Unit,
    emergencyState: EmergencyState,
    onStartEmergency: () -> Unit,
    onStopEmergency: () -> Unit,
    onOpenNetwork: () -> Unit,
    useLegacyTransport: Boolean,
    onLegacyTransportChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isHolding by remember { mutableStateOf(false) }
    val view = LocalView.current
    val idleAlpha by animateFloatAsState(
        targetValue = if (isHolding) 0f else 1f,
        animationSpec = tween(180),
        label = "idle alpha"
    )
    val activeAlpha by animateFloatAsState(
        targetValue = if (isHolding) 1f else 0f,
        animationSpec = tween(180),
        label = "active alpha"
    )
    val pulseTransition = rememberInfiniteTransition(label = "recording pulse")
    val pulseProgress by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "pulse progress"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "iTantra",
            color = Color(0xFFF4F7FB),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 64.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier.size(184.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = idleAlpha }
                        .clip(CircleShape)
                        .background(Color(0xFFF8AD3C))
                        .pointerInput(view) {
                            detectTapGestures(
                                onPress = {
                                    isHolding = true
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    val recordingStarted = onStartRecording()
                                    val released = try {
                                        tryAwaitRelease()
                                    } finally {
                                        isHolding = false
                                        if (recordingStarted) onStopRecording()
                                    }
                                    if (released && recordingStarted) {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onPttReleased()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {}
                RecordingPulse(activeAlpha, pulseProgress)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (isHolding) Color(0xFFD32F2F) else Color(0xFF2C1800),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        "HOLD TO TALK",
                        color = Color(0xFF2C1800),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer { alpha = idleAlpha }
                    )
                }
            }
            Text(
                text = when {
                    isHolding -> "Recording..."
                    isTranscribing -> "Transcribing..."
                    else -> "Idle"
                },
                color = Color(0xFF91A2B4)
            )
            LanguageSelector(
                selectedCode = selectedLanguageCode,
                onSelect = onLanguageChange,
                enabled = !isHolding && !isTranscribing
            )
            Button(
                onClick = {
                    if (recordingFile.exists()) {
                        try {
                            MediaPlayer().apply {
                                setDataSource(recordingFile.absolutePath)
                                setOnCompletionListener { release() }
                                prepare()
                                start()
                            }
                        } catch (e: IOException) {
                            // no valid recording to play yet
                        }
                    }
                }
            ) {
                Text("PLAY LAST RECORDING")
            }
            NetworkControls(
                emergencyState = emergencyState,
                onStart = onStartEmergency,
                onStop = onStopEmergency,
                onOpenNetwork = onOpenNetwork,
                useLegacyTransport = useLegacyTransport,
                onLegacyTransportChange = onLegacyTransportChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            if (useLegacyTransport) {
                TransportControls(
                    state = transportState,
                    pairedDevices = pairedDevices,
                    onListen = onListen,
                    onConnect = onConnect,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
            }
            BentoControls(
                unreadCount = unreadCount,
                onOpenHandsFree = onOpenHandsFree,
                onOpenText = onOpenText,
                onOpenLogs = onOpenLogs,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
            // TEMPORARY: exercises SpeechEngine.speak() directly. speak() has
            // never been executed at the SpeechEngine layer, so this stands in
            // for real usage. Remove once Transport integration exercises
            // speak() for real (see docs/CONTRACTS.md received-message path).
            TestTtsSection(
                speech = speech,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun BentoControls(
    unreadCount: Int,
    onOpenHandsFree: () -> Unit,
    onOpenText: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HandsFreeTile(onClick = onOpenHandsFree)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BentoTile(
                icon = Icons.Filled.Edit,
                label = "Text",
                badgeCount = 0,
                onClick = onOpenText,
                modifier = Modifier.weight(1f)
            )
            BentoTile(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = "Logs",
                badgeCount = unreadCount,
                onClick = onOpenLogs,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HandsFreeTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xBA0D1D2B))
            .border(1.dp, Color(0x21B6CFE7), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Hands-free",
                color = Color(0xFFF4F7FB),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF91A2B4),
                modifier = Modifier.size(20.dp)
            )
        }
        WaveformBars()
    }
}

private val WAVEFORM_BAR_HEIGHTS = listOf(8, 16, 26, 13, 22, 10, 18, 28, 12, 20, 9, 24, 15, 11, 19, 7)

@Composable
private fun WaveformBars(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WAVEFORM_BAR_HEIGHTS.forEach { barHeight ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x8AF8AD3C))
            )
        }
    }
}

@Composable
private fun BentoTile(
    icon: ImageVector,
    label: String,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color(0xBA0D1D2B))
            .border(1.dp, Color(0x21B6CFE7), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFF4F7FB),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = Color(0xFFF4F7FB),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFF8AD3C))
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badgeCount.toString(),
                    color = Color(0xFF2C1800),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF91A2B4),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun LanguageSelector(
    selectedCode: String,
    onSelect: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(Color(0xBA0D1D2B))
                .border(1.dp, Color(0x21B6CFE7), shape)
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedCode.uppercase(),
                color = if (enabled) Color(0xFFF4F7FB) else Color(0xFF637487),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SUPPORTED_LANGUAGES.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

// TEMPORARY: exercises SpeechEngine.speak() directly. speak() has never been
// executed at the SpeechEngine layer, so this stands in for real usage.
// Remove once Transport integration exercises speak() for real (see
// docs/CONTRACTS.md received-message path).
@Composable
private fun TestTtsSection(
    speech: SpeechEngine,
    modifier: Modifier = Modifier
) {
    var languageCode by rememberSaveable { mutableStateOf("en") }
    var text by rememberSaveable { mutableStateOf(TTS_SAMPLE_TEXT.getValue("en")) }
    var isSpeaking by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val hasVoice = speech.canSpeak(languageCode)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xBA0D1D2B))
            .border(1.dp, Color(0x21B6CFE7), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Test TTS (temporary)",
            color = Color(0xFF91A2B4),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFFDCE6EF),
                unfocusedTextColor = Color(0xFFDCE6EF),
                focusedContainerColor = Color(0xBA0D1D2B),
                unfocusedContainerColor = Color(0xBA0D1D2B),
                focusedBorderColor = Color(0xFFF8AD3C),
                unfocusedBorderColor = Color(0x21B6CFE7),
                cursorColor = Color(0xFFF8AD3C)
            )
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LanguageSelector(
                selectedCode = languageCode,
                onSelect = { selected ->
                    if (text.isBlank() || TTS_SAMPLE_TEXT.containsValue(text)) {
                        text = TTS_SAMPLE_TEXT[selected].orEmpty()
                    }
                    languageCode = selected
                },
                enabled = !isSpeaking,
                modifier = Modifier.weight(1f)
            )
            Button(
                enabled = !isSpeaking && hasVoice && text.isNotBlank(),
                onClick = {
                    isSpeaking = true
                    coroutineScope.launch {
                        val startElapsedMs = SystemClock.elapsedRealtime()
                        Log.d(LATENCY_TAG, "[TTS] speak start")
                        withContext(Dispatchers.Default) {
                            speech.speak(text, languageCode)
                        }
                        Log.d(
                            LATENCY_TAG,
                            "[TTS] speak end deltaMs=${SystemClock.elapsedRealtime() - startElapsedMs}"
                        )
                        isSpeaking = false
                    }
                }
            ) {
                Text(if (isSpeaking) "Speaking..." else "Test TTS")
            }
        }
        if (!hasVoice) {
            Text(
                text = "No verified TTS voice for this language yet.",
                color = Color(0xFF91A2B4),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun HandsFreeScreen(
    onStartRecording: () -> Boolean,
    onStopRecording: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    isTranscribing: Boolean,
    modifier: Modifier = Modifier
) {
    DisposableEffect(Unit) {
        onStartRecording()
        onDispose { onStopRecording() }
    }
    val stopAndBack = {
        onStopRecording()
        onBack()
    }
    val stopAndDone = {
        onStopRecording()
        onDone()
    }
    BackHandler(onBack = stopAndBack)
    val pulseTransition = rememberInfiniteTransition(label = "hands-free pulse")
    val pulseProgress by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "hands-free pulse progress"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = stopAndBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFFF4F7FB)
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Hands-free",
                color = Color(0xFFF4F7FB),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(184.dp),
                contentAlignment = Alignment.Center
            ) {
                RecordingPulse(alpha = 1f, progress = pulseProgress)
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = Color(0xFF2C1800),
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = if (isTranscribing) "Transcribing..." else "Listening...",
                color = Color(0xFFF4F7FB),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Speak normally",
                color = Color(0xFF91A2B4),
                fontSize = 14.sp
            )
        }
        Button(
            onClick = stopAndDone,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF8AD3C),
                contentColor = Color(0xFF2C1800)
            )
        ) {
            Text("Done", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NewMessageScreen(
    draft: String,
    onDraftChange: (String) -> Unit,
    isSending: Boolean,
    sendError: String?,
    recipients: List<RecipientOption>,
    selectedRecipientHex: String?,
    onSelectRecipient: (String) -> Unit,
    useLegacyTransport: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val requestBack = {
        if (draft.isEmpty()) onBack() else showDiscardDialog = true
    }
    BackHandler(onBack = requestBack)

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard this message?") },
            text = { Text("Your draft will be lost if you go back now.") },
            confirmButton = {
                TextButton(onClick = onBack) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = requestBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFFF4F7FB)
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "New message",
                color = Color(0xFFF4F7FB),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))
        RecipientPicker(
            recipients = recipients,
            selectedRecipientHex = selectedRecipientHex,
            onSelect = onSelectRecipient,
            useLegacyTransport = useLegacyTransport,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = {
                Text(
                    text = "Type your message",
                    color = Color(0xFF637487)
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFFDCE6EF),
                unfocusedTextColor = Color(0xFFDCE6EF),
                focusedContainerColor = Color(0xBA0D1D2B),
                unfocusedContainerColor = Color(0xBA0D1D2B),
                focusedBorderColor = Color(0xFFF8AD3C),
                unfocusedBorderColor = Color(0x21B6CFE7),
                cursorColor = Color(0xFFF8AD3C)
            )
        )
        Spacer(Modifier.height(20.dp))
        if (sendError != null) {
            Text(
                text = sendError,
                color = Color(0xFFFF8A80),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Button(
            enabled = !isSending,
            onClick = {
                val text = draft.trim()
                if (text.isNotEmpty()) {
                    onSend(text)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF8AD3C),
                contentColor = Color(0xFF2C1800)
            )
        ) {
            Text(if (isSending) "SENDING..." else "SEND", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LogsScreen(
    messages: List<Message>,
    onBack: () -> Unit,
    onOpenMessage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFFF4F7FB)
                )
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    text = "Logs",
                    color = Color(0xFFF4F7FB),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Conversation History",
                    color = Color(0xFF91A2B4),
                    fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages.size) { position ->
                val index = messages.lastIndex - position
                MessageCard(
                    message = messages[index],
                    onClick = { onOpenMessage(index) }
                )
            }
        }
    }
}

@Composable
private fun MessageCard(message: Message, onClick: () -> Unit) {
    val cardShape = RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 15.dp,
        bottomEnd = 15.dp,
        bottomStart = 4.dp
    )
    val isSent = message.direction == MessageDirection.SENT
    val isUnreadIncoming = !isSent && !message.isRead
    val stripeColor = when {
        isSent -> Color(0xFFF8AD3C)
        isUnreadIncoming -> Color(0xFF6FD4DF)
        else -> Color(0x336FD4DF)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(cardShape)
            .background(Color(0xBA0D1D2B))
            .border(1.dp, Color(0x21B6CFE7), cardShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(stripeColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.timestamp,
                    color = Color(0xFF91A2B4),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = directionLabel(message),
                    color = if (isSent) Color(0xFFF8AD3C) else Color(0xFF6FD4DF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = message.text,
                color = Color(0xFFDCE6EF),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF91A2B4),
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(end = 10.dp)
                .size(18.dp)
        )
    }
}

private fun directionLabel(message: Message): String =
    if (message.direction == MessageDirection.SENT) {
        listOfNotNull("↑ Sent", message.peerName?.let { "to $it" }, message.deliveryState?.label).joinToString(" • ")
    } else {
        listOfNotNull("↓ Received", message.peerName?.let { "from $it" }).joinToString(" • ")
    }

@Composable
private fun NetworkControls(
    emergencyState: EmergencyState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenNetwork: () -> Unit,
    useLegacyTransport: Boolean,
    onLegacyTransportChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nearbyLinks by ItantraNetwork.nearbyLinkCount.collectAsState(initial = 0)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = when (emergencyState) {
                EmergencyState.Off -> "Emergency mode: off"
                EmergencyState.Starting -> "Emergency mode: starting…"
                EmergencyState.On -> "Emergency mode: on • $nearbyLinks nearby link(s)"
                is EmergencyState.Error -> "Emergency mode: ${emergencyState.message}"
            },
            color = Color(0xFF91A2B4)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (emergencyState == EmergencyState.On || emergencyState == EmergencyState.Starting) {
                Button(onClick = onStop) { Text("STOP EMERGENCY MODE") }
            } else {
                Button(onClick = onStart) { Text("START EMERGENCY MODE") }
            }
            TextButton(onClick = onOpenNetwork) { Text("Contacts & network") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Switch(checked = useLegacyTransport, onCheckedChange = onLegacyTransportChange)
            Spacer(Modifier.width(8.dp))
            Text("Legacy RFCOMM demo (paired phones, not end-to-end)", color = Color(0xFF637487), fontSize = 12.sp)
        }
    }
}

@Composable
private fun RecipientPicker(
    recipients: List<RecipientOption>,
    selectedRecipientHex: String?,
    onSelect: (String) -> Unit,
    useLegacyTransport: Boolean,
) {
    if (useLegacyTransport) {
        Text("Legacy RFCOMM: sent to every connected phone, unencrypted.", color = Color(0xFF91A2B4), fontSize = 13.sp)
        return
    }
    if (recipients.isEmpty()) {
        Text(
            "No trusted contacts yet. Open \"Contacts & network\" and scan the other person's QR.",
            color = Color(0xFF91A2B4),
            fontSize = 13.sp
        )
        return
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        recipients.forEach { recipient ->
            val selected = recipient.nodeIdHex == selectedRecipientHex
            Button(
                onClick = { onSelect(recipient.nodeIdHex) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) Color(0xFFF8AD3C) else Color(0x33F8AD3C),
                    contentColor = if (selected) Color(0xFF2C1800) else Color(0xFFDCE6EF)
                )
            ) { Text(recipient.name) }
        }
    }
}

@Composable
private fun MessageDetailScreen(
    message: Message,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val isSent = message.direction == MessageDirection.SENT

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFFF4F7FB)
                )
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    text = "Message",
                    color = Color(0xFFF4F7FB),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Conversation Detail",
                    color = Color(0xFF91A2B4),
                    fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${message.date} • ${message.timestamp}",
                color = Color(0xFF91A2B4),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = directionLabel(message),
                color = if (isSent) Color(0xFFF8AD3C) else Color(0xFF6FD4DF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = message.text,
            color = Color(0xFFDCE6EF),
            fontSize = 18.sp,
            lineHeight = 28.sp
        )
    }
}

private const val SAMPLE_RATE_HZ = 16000
private const val WAV_HEADER_SIZE = 44

private class PcmRecorder(private val outputFile: File) {
    @Volatile private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize
        )
        audioRecord = record
        isRecording = true
        record.startRecording()
        recordingThread = Thread {
            val buffer = ByteArray(minBufferSize)
            RandomAccessFile(outputFile, "rw").use { file ->
                file.setLength(0)
                file.seek(WAV_HEADER_SIZE.toLong())
                while (isRecording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        file.write(buffer, 0, read)
                    }
                }
                val dataLength = file.length() - WAV_HEADER_SIZE
                file.seek(0)
                file.write(buildWavHeader(dataLength))
            }
        }.also { it.start() }
    }

    fun stop() {
        isRecording = false
        recordingThread?.join()
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}

private fun buildWavHeader(dataLength: Long): ByteArray {
    val bitsPerSample = 16
    val channels = 1
    val byteRate = SAMPLE_RATE_HZ * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val buffer = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
    buffer.putInt((36 + dataLength).toInt())
    buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
    buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
    buffer.putInt(16)
    buffer.putShort(1.toShort()) // PCM
    buffer.putShort(channels.toShort())
    buffer.putInt(SAMPLE_RATE_HZ)
    buffer.putInt(byteRate)
    buffer.putShort(blockAlign.toShort())
    buffer.putShort(bitsPerSample.toShort())
    buffer.put("data".toByteArray(Charsets.US_ASCII))
    buffer.putInt(dataLength.toInt())
    return buffer.array()
}

private const val RING_COUNT = 3

@Composable
private fun RecordingPulse(alpha: Float, progress: Float) {
    Box(
        modifier = Modifier
            .size(184.dp)
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center
    ) {
        for (i in 0 until RING_COUNT) {
            val phase = (progress + i.toFloat() / RING_COUNT) % 1f
            val ringSize = 100.dp + 80.dp * phase
            val ringAlpha = phase * 0.9f
            Box(
                modifier = Modifier
                    .size(ringSize)
                    .alpha(ringAlpha)
                    .border(4.dp, Color(0xFFF8AD3C), CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(Color(0xFFF8AD3C))
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ITantraAppPreview() {
    SIH_iTantraTheme {
        ITantraApp()
    }
}
