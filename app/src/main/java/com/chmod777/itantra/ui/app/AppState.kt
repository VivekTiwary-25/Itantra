package com.chmod777.itantra.ui.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.chmod777.itantra.DATE_FORMAT
import com.chmod777.itantra.LATENCY_TAG
import com.chmod777.itantra.Message
import com.chmod777.itantra.MessageDeliveryState
import com.chmod777.itantra.MessageDirection
import com.chmod777.itantra.MessageListSaver
import com.chmod777.itantra.PcmRecorder
import com.chmod777.itantra.TIME_FORMAT
import com.chmod777.itantra.WAV_HEADER_SIZE
import com.chmod777.itantra.failureMessage
import com.chmod777.itantra.legacyAcknowledgedState
import com.chmod777.itantra.matchesAcknowledgement
import com.chmod777.itantra.network.ItantraNetwork
import com.chmod777.itantra.network.RecipientOption
import com.chmod777.itantra.service.EmergencyState
import com.chmod777.itantra.toMessageDeliveryState
import com.chmod777.itantra.transport.BluetoothPermissions
import com.chmod777.itantra.transport.BluetoothRfcommTransport
import com.chmod777.itantra.transport.PairedBluetoothDevice
import com.chmod777.itantra.transport.RfcommConnectionState
import com.chmod777.itantra.transport.SendMessageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import speech.SpeechEngine
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Everything the screens need from the engine, in one place.
 *
 * The screens above this are pure UI: they read values and call the functions here, and never
 * touch [ItantraNetwork], the recorder or the transport themselves. That is what lets each screen
 * be replaced one at a time without disturbing the parts that are physically proven.
 *
 * Nothing in here changes how the engine behaves; it was lifted out of MainActivity unchanged.
 */
internal class AppState(
    private val context: Context,
    private val scope: CoroutineScope,
    val speech: SpeechEngine,
    val transport: BluetoothRfcommTransport,
    val recordingFile: File,
    private val recorder: PcmRecorder,
    val messages: SnapshotStateList<Message>,
    private val requestBluetoothPermissions: () -> Unit,
    useLegacyTransportState: MutableState<Boolean>,
    selectedRecipientHexState: MutableState<String?>,
    draftState: MutableState<String>,
    draftLanguageCodeState: MutableState<String>,
    selectedLanguageCodeState: MutableState<String>,
    sendErrorState: MutableState<String?>,
) {
    /** The v1 BLE/DTN stack is the default. Legacy RFCOMM relays in plaintext and its ACK
     *  proves only the next hop, so it stays opt-in. */
    var useLegacyTransport by useLegacyTransportState
    var selectedRecipientHex by selectedRecipientHexState
    var draft by draftState
    var draftLanguageCode by draftLanguageCodeState
    var selectedLanguageCode by selectedLanguageCodeState
    var sendError by sendErrorState

    var recipients by mutableStateOf(emptyList<RecipientOption>())
        private set
    var transportState by mutableStateOf<RfcommConnectionState>(RfcommConnectionState.Idle)
        private set
    var pairedDevices by mutableStateOf(emptyList<PairedBluetoothDevice>())
        private set
    var isTranscribing by mutableStateOf(false)
        private set
    var isSending by mutableStateOf(false)
        private set

    /** Set while a permission prompt is open, so a granted result can start what was asked for. */
    private var pendingEmergencyStart = false

    val unreadCount: Int
        get() = messages.count { it.direction == MessageDirection.RECEIVED && !it.isRead }

    // ---- microphone ------------------------------------------------------------------

    /** Returns false when the microphone permission is missing; the caller shows the error. */
    fun startRecording(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) recorder.start()
        return granted
    }

    fun stopRecording() = recorder.stop()

    /**
     * Shared by PTT-release and Hands-free Done: both route the same WAV through
     * [SpeechEngine.transcribe] off the main thread, then hand the text back through [onReady].
     */
    fun startTranscription(source: String, onReady: () -> Unit) {
        if (isTranscribing || recordingFile.length() <= WAV_HEADER_SIZE) return
        isTranscribing = true
        scope.launch {
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
            onReady()
        }
    }

    // ---- composing and sending -------------------------------------------------------

    /** Opens an empty draft; refreshes the trusted contacts the picker offers. */
    fun beginTypedMessage() {
        draft = ""
        draftLanguageCode = "en"
        sendError = null
        recipients = ItantraNetwork.recipients()
    }

    fun discardDraft() {
        draft = ""
        sendError = null
    }

    /** [onSent] runs only when the message actually left; a failure sets [sendError] instead. */
    fun sendMessage(text: String, onSent: () -> Unit) {
        if (!useLegacyTransport) {
            val recipient = recipients.firstOrNull { it.nodeIdHex == selectedRecipientHex }
            if (recipient == null) {
                sendError = "Choose a trusted contact. Unknown people cannot be addressed; use SOS instead."
                return
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
                    onSent()
                }
                .onFailure { sendError = it.message ?: "Could not queue the message." }
            return
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
            onSent()
        }
    }

    fun markRead(index: Int) {
        val message = messages[index]
        if (message.direction == MessageDirection.RECEIVED && !message.isRead) {
            messages[index] = message.copy(isRead = true)
        }
    }

    // ---- radio -----------------------------------------------------------------------

    /** Asks for the nearby-devices permissions first when they are missing. */
    fun startEmergencyMode() {
        if (BluetoothPermissions.areGranted(context)) {
            ItantraNetwork.startEmergencyMode(context)
        } else {
            pendingEmergencyStart = true
            requestBluetoothPermissions()
        }
    }

    fun stopEmergencyMode() = ItantraNetwork.stopEmergencyMode(context)

    fun listenForBluetooth() = transport.listen { transportState = it }

    fun connectTo(address: String) = transport.connect(address) { transportState = it }

    /** Called from the permission result; finishes whatever the prompt was opened for. */
    internal fun onBluetoothPermissionResult() {
        if (BluetoothPermissions.areGranted(context)) {
            if (useLegacyTransport) {
                pairedDevices = BluetoothPermissions.pairedDevices(context)
                listenForBluetooth()
            }
            if (pendingEmergencyStart) ItantraNetwork.startEmergencyMode(context)
        }
        pendingEmergencyStart = false
    }

    internal fun refreshPairedDevices() {
        pairedDevices = BluetoothPermissions.pairedDevices(context)
    }
}

/**
 * Builds the [AppState] and starts every engine listener it needs. The saveable values stay
 * saveable, so a rotation still keeps the draft and the chosen language.
 */
@Composable
internal fun rememberAppState(): AppState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val transport = remember { BluetoothRfcommTransport(context) }
    val speech = remember { SpeechEngine(context) }
    val recordingFile = remember { File(context.filesDir, "recording.wav") }
    val recorder = remember { PcmRecorder(recordingFile) }
    // Still the three placeholder rows from before this refactor. They are deleted with the rest
    // of the demo leftovers at the last step, not here: step 0a must change nothing visible.
    val messages = rememberSaveable(saver = MessageListSaver) {
        val today = LocalDate.now().format(DATE_FORMAT)
        mutableStateListOf(
            Message("Water rising near the school", today, "10:42", MessageDirection.RECEIVED, isRead = false, languageCode = "en"),
            Message("Six people at the temple", today, "10:43", MessageDirection.RECEIVED, isRead = false, languageCode = "en"),
            Message("Need medical supplies", today, "10:44", MessageDirection.RECEIVED, isRead = false, languageCode = "en")
        )
    }

    val useLegacyTransport = rememberSaveable { mutableStateOf(false) }
    val selectedRecipientHex = rememberSaveable { mutableStateOf<String?>(null) }
    val draft = rememberSaveable { mutableStateOf("") }
    val draftLanguageCode = rememberSaveable { mutableStateOf("en") }
    val selectedLanguageCode = rememberSaveable { mutableStateOf("en") }
    val sendError = rememberSaveable { mutableStateOf<String?>(null) }

    // The state needs the permission launcher and the launcher's result needs the state, so the
    // launcher reads it through a holder that is filled in the moment the state exists.
    val stateHolder = remember { mutableStateOf<AppState?>(null) }
    val requestBluetoothPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { stateHolder.value?.onBluetoothPermissionResult() }

    val requestBluetoothPermissions = remember {
        {
            val permissions = BluetoothPermissions.requiredRuntimePermissions().toMutableList()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissions += Manifest.permission.POST_NOTIFICATIONS
            }
            requestBluetoothPermission.launch(permissions.toTypedArray())
        }
    }

    val state = remember {
        AppState(
            context = context,
            scope = scope,
            speech = speech,
            transport = transport,
            recordingFile = recordingFile,
            recorder = recorder,
            messages = messages,
            requestBluetoothPermissions = requestBluetoothPermissions,
            useLegacyTransportState = useLegacyTransport,
            selectedRecipientHexState = selectedRecipientHex,
            draftState = draft,
            draftLanguageCodeState = draftLanguageCode,
            selectedLanguageCodeState = selectedLanguageCode,
            sendErrorState = sendError,
        ).also { stateHolder.value = it }
    }

    DisposableEffect(transport) { onDispose { transport.close() } }
    DisposableEffect(speech) { onDispose { speech.release() } }

    val requestMicPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        if (!BluetoothPermissions.areGranted(context)) requestBluetoothPermissions()
    }

    LaunchedEffect(state.useLegacyTransport) {
        if (state.useLegacyTransport && BluetoothPermissions.areGranted(context)) {
            state.refreshPairedDevices()
            state.listenForBluetooth()
        }
    }

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
            // Speak only once the text is in Logs and the ACK is away. Model loading takes
            // seconds, so it runs off the RFCOMM reader thread, and every failure stays inside
            // this block: transport must survive a mute phone.
            scope.launch(Dispatchers.Default) {
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

    // v1 stack receive path: only authenticated end-recipient deliveries arrive here, so a relay
    // phone never shows or speaks messages it merely carries (execution spec §8.4).
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
                scope.launch(Dispatchers.Default) {
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
            known[message.networkBundleId]?.let {
                messages[index] = message.copy(deliveryState = it.toMessageDeliveryState())
            }
        }
        ItantraNetwork.outgoingStateChanges.collect { (bundleIdHex, deliveryState) ->
            val index = messages.indexOfFirst { it.networkBundleId == bundleIdHex }
            if (index >= 0) {
                messages[index] = messages[index].copy(deliveryState = deliveryState.toMessageDeliveryState())
            }
        }
    }

    return state
}

/** Convenience for screens that only need the radio's on/off state. */
@Composable
fun rememberEmergencyState(): EmergencyState = ItantraNetwork.emergencyState.collectAsState().value
