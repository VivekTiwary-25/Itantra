package com.chmod777.itantra.ui.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.core.app.ActivityCompat
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
    val transport: BluetoothRfcommTransport,
    val messages: SnapshotStateList<Message>,
    private val requestBluetoothPermissions: () -> Unit,
    private val requestMicPermission: () -> Unit,
    useLegacyTransportState: MutableState<Boolean>,
    selectedRecipientHexState: MutableState<String?>,
    draftState: MutableState<String>,
    draftLanguageCodeState: MutableState<String>,
    selectedLanguageCodeState: MutableState<String>,
    sendErrorState: MutableState<String?>,
    composerModeState: MutableState<MessageMode>,
) {
    /** The v1 BLE/DTN stack is the default. Legacy RFCOMM relays in plaintext and its ACK
     *  proves only the next hop, so it stays opt-in. */
    var useLegacyTransport by useLegacyTransportState
    var selectedRecipientHex by selectedRecipientHexState
    var draft by draftState
    var draftLanguageCode by draftLanguageCodeState
    var selectedLanguageCode by selectedLanguageCodeState
    var sendError by sendErrorState
    var composerMode by composerModeState

    // Owned by the process, so a recreated screen finds the same engine, the same recorder and
    // any take still being decoded. See CaptureSession for the crash that forced this.
    val speech: SpeechEngine get() = CaptureSession.speech
    val recordingFile: File get() = CaptureSession.recordingFile
    private val recorder get() = CaptureSession.recorder

    var recipients by mutableStateOf(emptyList<RecipientOption>())
        private set
    var transportState by mutableStateOf<RfcommConnectionState>(RfcommConnectionState.Idle)
        private set
    var pairedDevices by mutableStateOf(emptyList<PairedBluetoothDevice>())
        private set
    val isTranscribing: Boolean get() = CaptureSession.isTranscribing

    /** The take just finished, waiting to be applied by whatever screen is on top. */
    val pendingTranscript: Transcript? get() = CaptureSession.pending

    var isSending by mutableStateOf(false)
        private set

    /**
     * Counts messages that actually reached the queue. The composer watches it rather than being
     * told "sent": a failure must leave the draft where it is, so nothing but a real queue write
     * is allowed to trigger the send animation and the trip back Home.
     */
    var sentCount by mutableStateOf(0)
        private set

    /** What went wrong with the last capture, or null. Home and Hands-free both read this. */
    var captureError: CaptureError?
        get() = CaptureSession.captureError
        private set(value) { CaptureSession.captureError = value }

    /** Set while a permission prompt is open, so a granted result can start what was asked for. */
    private var pendingEmergencyStart = false

    /**
     * Whether the system has ever been shown the microphone dialog for this install. Android
     * only reports "denied for good" indirectly: once the permission has been asked and the
     * system no longer wants a rationale shown, asking again puts no dialog on screen at all —
     * which is precisely the silent nothing the user was getting.
     */
    private val permissionPrefs = context.getSharedPreferences("ui_permissions", Context.MODE_PRIVATE)
    private var micEverAsked: Boolean
        get() = runCatching { permissionPrefs.getBoolean(KEY_MIC_ASKED, false) }.getOrDefault(false)
        set(value) { runCatching { permissionPrefs.edit().putBoolean(KEY_MIC_ASKED, value).apply() } }

    private val canAskForMic: Boolean
        get() {
            if (!micEverAsked) return true
            val activity = context.findActivity() ?: return false
            return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        }

    fun dismissCaptureError() { CaptureSession.captureError = null }

    val unreadCount: Int
        get() = messages.count { it.direction == MessageDirection.RECEIVED && !it.isRead }

    // ---- microphone ------------------------------------------------------------------

    val micGranted: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Returns false when nothing is being recorded, and the caller must not animate as if it
     * were. The first refusal asks the system once; after that it sets [CaptureError.MIC_BLOCKED],
     * because Android stops showing the dialog and only the app's settings page can undo it.
     *
     * The WAV is emptied on the way out of the failing path. Without that, the next Done would
     * transcribe whatever the *last* successful take left on disk and present it as something
     * just spoken — which is how a denied microphone looked like a working one.
     */
    fun startRecording(): Boolean {
        if (!requestCapture()) {
            discardRecording()
            return false
        }
        recorder.start()
        return true
    }

    /**
     * Whether a capture may begin at all, asking for the microphone the first time and setting
     * [CaptureError.MIC_BLOCKED] after that. Hands-free calls this before opening, so it cannot
     * sit there animating at a microphone it was never given.
     */
    fun requestCapture(): Boolean {
        if (micGranted) {
            captureError = null
            return true
        }
        if (canAskForMic) {
            micEverAsked = true
            requestMicPermission()
        } else {
            captureError = CaptureError.MIC_BLOCKED
        }
        return false
    }

    /** Called from the permission result so a refusal is visible rather than silent. */
    internal fun onMicPermissionResult(granted: Boolean) {
        captureError = if (granted) null else CaptureError.MIC_BLOCKED
    }

    private fun discardRecording() = CaptureSession.discardRecording()

    fun stopRecording() = recorder.stop()

    /**
     * Shared by PTT-release and Hands-free Done. The work runs in the session, outside this
     * composition, so a rotation cannot cancel it or free the recognizer underneath it. The
     * result arrives in [pendingTranscript] and is applied by [applyTranscript].
     */
    fun startTranscription(source: String) {
        CaptureSession.transcribe(source = source, languageCode = selectedLanguageCode, micGranted = micGranted)
    }

    /**
     * Moves a finished take into the draft. Called by the navigation once, whichever screen is
     * on top by then — which may not be the one the take started on.
     */
    fun applyTranscript(transcript: Transcript) {
        draft = transcript.text
        draftLanguageCode = transcript.languageCode
        // Urgent is never inherited by the next take: a sticky flag is how someone sends an
        // ordinary message as Urgent without meaning to.
        composerMode = MessageMode.NORMAL
        sendError = null
        recipients = ItantraNetwork.recipients()
    }

    fun takePendingTranscript(): Transcript? = CaptureSession.takePending()

    // ---- composing and sending -------------------------------------------------------

    /** Opens an empty draft; refreshes the trusted contacts the picker offers. */
    fun beginTypedMessage() {
        draft = ""
        draftLanguageCode = "en"
        composerMode = MessageMode.NORMAL
        sendError = null
        recipients = ItantraNetwork.recipients()
    }

    fun discardDraft() {
        draft = ""
        sendError = null
    }

    /**
     * Told about every message that reached the queue, so whatever is showing Logs can remember
     * the one thing the engine does not store: that the sender chose Urgent. The DTN `outgoing`
     * table keeps no priority column, so without this the tag would vanish on a restart.
     */
    var onMessageQueued: ((bundleIdHex: String, urgent: Boolean) -> Unit)? = null

    /** [onSent] runs only when the message actually left; a failure sets [sendError] instead. */
    fun sendMessage(text: String, urgent: Boolean = false, onSent: () -> Unit) {
        if (!useLegacyTransport) {
            val recipient = recipients.firstOrNull { it.nodeIdHex == selectedRecipientHex }
            if (recipient == null) {
                sendError = "Choose a trusted contact. Unknown people cannot be addressed; use SOS instead."
                return
            }
            ItantraNetwork.queueTrustedMessage(recipient.nodeIdHex, text, draftLanguageCode, urgent)
                .onSuccess { bundleIdHex ->
                    onMessageQueued?.invoke(bundleIdHex, urgent)
                    sentCount++
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
            sentCount++
            onSent()
        }
    }

    /**
     * Speaks a message again. A received message is text — there is no recording of it, which is
     * the point of the project — so replay is a fresh TTS pass, not a player. It runs off the main
     * thread and swallows its own failures: a mute phone must not take the screen down with it.
     */
    fun speakAgain(text: String, languageCode: String) {
        scope.launch(Dispatchers.Default) {
            try {
                Log.d(LATENCY_TAG, "[TTS] replay lang=$languageCode")
                speech.speak(text, languageCode)
            } catch (e: Throwable) {
                Log.e(LATENCY_TAG, "[TTS] replay failed lang=$languageCode", e)
            }
        }
    }

    /** The contact sheet calls this after a contact is added, so the new one shows up at once. */
    fun refreshRecipients() {
        recipients = runCatching { ItantraNetwork.recipients() }.getOrDefault(recipients)
    }

    init {
        // A recreated screen starts with an empty contact list, and a remembered recipient that
        // matches nothing in it reads as "Select trusted contact" -- the draft kept its person
        // but the composer could not show who. Loading them up front is a handful of rows.
        refreshRecipients()
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

    private companion object {
        const val KEY_MIC_ASKED = "mic_ever_asked"
    }
}

/** LocalContext is usually the Activity, but it arrives wrapped often enough to be worth this. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Builds the [AppState] and starts every engine listener it needs. The saveable values stay
 * saveable, so a rotation still keeps the draft and the chosen language.
 */
@Composable
internal fun rememberAppState(): AppState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    CaptureSession.attach(context)
    val transport = remember { BluetoothRfcommTransport(context) }
    // The three placeholder rows are gone: Logs reads the database now, so a fake row would be a
    // lie about what the engine holds. This list survives only for the legacy screen behind the
    // debug long-press, which is deleted at the last step.
    val messages = rememberSaveable(saver = MessageListSaver) { mutableStateListOf<Message>() }

    val useLegacyTransport = rememberSaveable { mutableStateOf(false) }
    val selectedRecipientHex = rememberSaveable { mutableStateOf<String?>(null) }
    val draft = rememberSaveable { mutableStateOf("") }
    val draftLanguageCode = rememberSaveable { mutableStateOf("en") }
    val selectedLanguageCode = rememberSaveable { mutableStateOf("en") }
    val sendError = rememberSaveable { mutableStateOf<String?>(null) }
    // An enum is Serializable, so Urgent survives a rotation the same way the draft text does.
    val composerMode = rememberSaveable { mutableStateOf(MessageMode.NORMAL) }

    // The state needs the permission launcher and the launcher's result needs the state, so the
    // launcher reads it through a holder that is filled in the moment the state exists.
    val stateHolder = remember { mutableStateOf<AppState?>(null) }
    // Asked on the first hold of the orb rather than on launch, so the one line explaining why
    // it is needed is the orb the user has just pressed.
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> stateHolder.value?.onMicPermissionResult(granted) }
    val requestMicPermission = remember {
        { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO); Unit }
    }
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
            transport = transport,
            messages = messages,
            requestBluetoothPermissions = requestBluetoothPermissions,
            requestMicPermission = requestMicPermission,
            useLegacyTransportState = useLegacyTransport,
            selectedRecipientHexState = selectedRecipientHex,
            draftState = draft,
            draftLanguageCodeState = draftLanguageCode,
            selectedLanguageCodeState = selectedLanguageCode,
            sendErrorState = sendError,
            composerModeState = composerMode,
        ).also { stateHolder.value = it }
    }

    DisposableEffect(transport) { onDispose { transport.close() } }
    // The recognizer is deliberately not released here. Releasing it from a composition is what
    // crashed the app: a rotation during a decode freed it while the decoder was inside it.

    LaunchedEffect(Unit) {
        if (!BluetoothPermissions.areGranted(context)) requestBluetoothPermissions()
    }

    LaunchedEffect(state.useLegacyTransport) {
        if (state.useLegacyTransport && BluetoothPermissions.areGranted(context)) {
            state.refreshPairedDevices()
            state.listenForBluetooth()
        }
    }

    val speech = CaptureSession.speech
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
