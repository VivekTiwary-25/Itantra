package com.chmod777.itantra.ui.app

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.chmod777.itantra.audio.PcmRecorder
import com.chmod777.itantra.crypto.toHex
import com.chmod777.itantra.dtn.DeliveryState
import com.chmod777.itantra.network.ItantraNetwork
import com.chmod777.itantra.network.ProductDelivery
import com.chmod777.itantra.network.RecipientOption
import com.chmod777.itantra.protocol.SosCategory
import com.chmod777.itantra.service.EmergencySession
import com.chmod777.itantra.service.NetworkingRuntime
import com.chmod777.itantra.sos.IncomingSos
import com.chmod777.itantra.sos.OutgoingSosState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import speech.SpeechEngine
import java.io.File

sealed interface Route {
    data object Home : Route
    data object HandsFree : Route
    data object Composer : Route
    data object Logs : Route
    data class Detail(val bundleIdHex: String) : Route
    data class SosDetail(val sosIdHex: String) : Route
    data class Conversation(val sosIdHex: String) : Route
    data object Contacts : Route
    data class ComingSoon(val title: String) : Route
}

enum class SendMode { NORMAL, URGENT, SOS }

sealed interface CaptureState {
    data object Idle : CaptureState
    data object Recording : CaptureState
    data object Transcribing : CaptureState
    data class Failed(val message: String, val needsPermission: Boolean = false) : CaptureState
}

/** The sender's own record of an SOS it raised. The Log entry is the record; it never becomes the chat. */
data class SosRecord(
    val sosIdHex: String,
    val text: String,
    val language: String,
    val createdWallMs: Long,
    val phase: OutgoingSosState.Phase,
    val everConnected: Boolean = false,
    val endNote: String? = null,
    val offered: Int = 0,
    val declined: Int = 0,
)

sealed interface LogEntry {
    val key: String
    val wallMs: Long

    data class Outgoing(
        override val key: String, override val wallMs: Long, val peer: String, val text: String,
        val language: String, val urgent: Boolean, val state: DeliveryState,
    ) : LogEntry

    data class Incoming(
        override val key: String, override val wallMs: Long, val peer: String, val text: String,
        val language: String, val urgent: Boolean, val unread: Boolean,
    ) : LogEntry

    data class Sos(val record: SosRecord) : LogEntry {
        override val key get() = record.sosIdHex
        override val wallMs get() = record.createdWallMs
    }
}

/**
 * Process-scoped UI state for the demo product shell. It survives activity recreation and
 * holds drafts across navigation. All protocol truth comes from [NetworkingRuntime] and
 * [ItantraNetwork]; nothing here fabricates delivery, acceptance or identity.
 */
object AppStore {
    private const val TAG = "ITANTRA_UI"
    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private var wired = false
    val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val speech: SpeechEngine by lazy { SpeechEngine(app) }
    private val recordingFile: File by lazy { File(app.filesDir, "recording.wav") }
    val recorder: PcmRecorder by lazy { PcmRecorder(recordingFile) }

    // ------------------------------------------------------------------ navigation
    val stack = mutableStateListOf<Route>(Route.Home)
    val route: Route get() = stack.last()
    fun go(route: Route) { stack.add(route) }
    fun replaceTop(route: Route) { stack[stack.lastIndex] = route }
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
    fun home() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }

    // ------------------------------------------------------------------ language
    var languageCode by mutableStateOf("en")
        private set
    fun selectLanguage(code: String) {
        if (captureState == CaptureState.Recording || captureState == CaptureState.Transcribing) return
        languageCode = code
        prefs.edit().putString("language", code).apply()
    }

    // ------------------------------------------------------------------ composer
    var mode by mutableStateOf(SendMode.NORMAL)
        private set
    var privateDraft by mutableStateOf("")
    var sosDraft by mutableStateOf("")
    var draftLanguage by mutableStateOf("en")
    var recipientHex by mutableStateOf<String?>(null)
        private set
    var composerError by mutableStateOf<String?>(null)
    var sending by mutableStateOf(false)
        private set

    val activeDraft: String get() = if (mode == SendMode.SOS) sosDraft else privateDraft
    fun setActiveDraft(text: String) {
        if (mode == SendMode.SOS) sosDraft = text else privateDraft = text
        composerError = null
    }

    /**
     * Normal and Urgent share the private draft and recipient; SOS keeps its own. A fresh private
     * draft may seed an empty SOS draft (the speaker chose SOS for what they just said); an SOS
     * request is never copied back into private mode.
     */
    fun changeMode(next: SendMode) {
        if (next == mode) return
        if (next == SendMode.SOS && sosDraft.isBlank()) sosDraft = privateDraft
        mode = next
        composerError = null
    }

    fun selectRecipient(hex: String) {
        recipientHex = hex
        prefs.edit().putString("recipient", hex).apply()
        composerError = null
    }

    fun recipients(): List<RecipientOption> = ItantraNetwork.recipients()

    private fun ensureRecipient() {
        val all = recipients()
        if (all.none { it.nodeIdHex == recipientHex }) recipientHex = all.firstOrNull()?.nodeIdHex
    }

    val hasDraft: Boolean get() = privateDraft.isNotBlank() || sosDraft.isNotBlank()

    fun discardDrafts() {
        privateDraft = ""
        sosDraft = ""
        mode = SendMode.NORMAL
        composerError = null
    }

    /** PTT / Hands-free: words are appended to any kept draft, never replace it. */
    fun openComposerWithTranscript(text: String, fromHandsFree: Boolean) {
        if (!hasDraft) {
            mode = SendMode.NORMAL
        }
        val current = activeDraft
        setActiveDraft(if (current.isBlank()) text else current.trimEnd() + " " + text)
        draftLanguage = languageCode
        ensureRecipient()
        if (fromHandsFree && route == Route.HandsFree) replaceTop(Route.Composer) else go(Route.Composer)
    }

    fun openComposerTyped() {
        if (!hasDraft) {
            mode = SendMode.NORMAL
            draftLanguage = languageCode
        }
        composerError = null
        ensureRecipient()
        go(Route.Composer)
    }

    fun send() {
        if (sending) return
        val text = activeDraft.trim()
        if (text.isEmpty()) {
            composerError = "Write or say something first."
            return
        }
        when (mode) {
            SendMode.NORMAL, SendMode.URGENT -> sendPrivate(text, urgent = mode == SendMode.URGENT)
            SendMode.SOS -> sendSos(text)
        }
    }

    private fun sendPrivate(text: String, urgent: Boolean) {
        val recipient = recipients().firstOrNull { it.nodeIdHex == recipientHex }
        if (recipient == null) {
            composerError = "Choose a trusted contact first."
            return
        }
        sending = true
        composerError = null
        val language = draftLanguage
        main.launch {
            val started = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) { ItantraNetwork.queueTrustedMessage(recipient.nodeIdHex, text, language, urgent) }
            // Let the pressed "Sending…" state register before leaving the screen.
            delay((350 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            sending = false
            result.onSuccess {
                selectRecipient(recipient.nodeIdHex)
                discardDrafts()
                logVersion++
                home()
            }.onFailure { composerError = it.message ?: "Could not queue the message." }
        }
    }

    private fun sendSos(text: String) {
        val session = NetworkingRuntime.session.value
        if (session == null) {
            composerError = "The Bluetooth radio isn't running, so nearby responders can't be reached. Turn on Bluetooth, then try again."
            return
        }
        sending = true
        val language = draftLanguage
        main.launch {
            delay(250)
            sosRecords.indexOfFirst { it.phase != OutgoingSosState.Phase.ENDED }.takeIf { it >= 0 }?.let {
                sosRecords[it] = sosRecords[it].copy(phase = OutgoingSosState.Phase.ENDED, endNote = "Replaced by a new SOS")
            }
            session.sos.startSos(SosCategory.OTHER, language)
            val id = session.sos.outgoing.value?.sosIdHex
            sending = false
            if (id == null) {
                composerError = "Could not start the SOS. Try again."
                return@launch
            }
            sosRecords.add(SosRecord(id, text, language, System.currentTimeMillis(), OutgoingSosState.Phase.SEARCHING))
            discardDrafts()
            logVersion++
            home()
        }
    }

    // ------------------------------------------------------------------ capture (PTT / Hands-free)
    var captureState by mutableStateOf<CaptureState>(CaptureState.Idle)

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun startCapture(): Boolean {
        if (captureState == CaptureState.Transcribing) return false
        if (!hasMicPermission()) {
            captureState = CaptureState.Failed("Microphone access is off. Allow it to speak a message.", needsPermission = true)
            return false
        }
        if (!recorder.start()) {
            captureState = CaptureState.Failed("The microphone is busy. Close other recording apps and try again.")
            return false
        }
        captureState = CaptureState.Recording
        return true
    }

    fun cancelCapture() {
        recorder.stop()
        captureState = CaptureState.Idle
    }

    /** Stops recording and transcribes; [onText] receives recognised words only. Nothing is sent. */
    fun finishCapture(onText: (String) -> Unit) {
        recorder.stop()
        if (recorder.durationMs() < 400) {
            captureState = CaptureState.Failed("That was too short. Hold while you speak.")
            return
        }
        captureState = CaptureState.Transcribing
        val language = languageCode
        main.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { speech.transcribe(recordingFile.absolutePath, language) }
            }
            result.onSuccess { text ->
                val clean = text.trim()
                if (clean.isEmpty()) {
                    captureState = CaptureState.Failed("No words were recognised. Speak closer to the phone and try again.")
                } else {
                    captureState = CaptureState.Idle
                    onText(clean)
                }
            }.onFailure {
                Log.e(TAG, "transcribe failed", it)
                captureState = CaptureState.Failed("Speech recognition failed on this phone. You can type instead.")
            }
        }
    }

    fun clearCaptureFailure() {
        if (captureState is CaptureState.Failed) captureState = CaptureState.Idle
    }

    // ------------------------------------------------------------------ logs
    var logVersion by mutableIntStateOf(0)
    var readVersion by mutableIntStateOf(0)
        private set
    val sosRecords = mutableStateListOf<SosRecord>()

    /**
     * The SOS incident itself lives only in the radio session (its keys are discarded when it ends),
     * so after a restart a stored record can only be shown as ended — never as still searching.
     */
    private fun saveSosRecords() {
        val array = org.json.JSONArray()
        sosRecords.takeLast(20).forEach { r ->
            array.put(
                org.json.JSONObject()
                    .put("id", r.sosIdHex).put("text", r.text).put("lang", r.language).put("at", r.createdWallMs)
                    .put("phase", r.phase.name).put("connected", r.everConnected).put("note", r.endNote ?: ""),
            )
        }
        prefs.edit().putString("sos_records", array.toString()).apply()
    }

    private fun loadSosRecords() {
        val raw = prefs.getString("sos_records", null) ?: return
        runCatching {
            val array = org.json.JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val phase = OutgoingSosState.Phase.valueOf(o.getString("phase"))
                val note = o.optString("note").ifEmpty { null }
                sosRecords.add(
                    SosRecord(
                        sosIdHex = o.getString("id"), text = o.getString("text"), language = o.getString("lang"),
                        createdWallMs = o.getLong("at"), phase = OutgoingSosState.Phase.ENDED,
                        everConnected = o.optBoolean("connected"),
                        endNote = if (phase == OutgoingSosState.Phase.ENDED) note else "Ended when iTantra closed",
                    ),
                )
            }
        }
    }

    private fun readSet(): Set<String> = prefs.getStringSet("read_incoming", emptySet())!!

    fun markRead(bundleIdHex: String) {
        val current = readSet()
        if (bundleIdHex in current) return
        prefs.edit().putStringSet("read_incoming", current + bundleIdHex).apply()
        readVersion++
    }

    fun contactNames(): Map<String, String> = recipients().associate { it.nodeIdHex to it.name }

    fun unreadIncomingCount(): Int {
        val read = readSet()
        return NetworkingRuntime.dtn.deliveredMessages().count { it.bundleId.toHex() !in read }
    }

    fun logEntries(): List<LogEntry> {
        val names = contactNames()
        val read = readSet()
        val outgoing = NetworkingRuntime.dtn.outgoingMessages().map {
            val key = it.bundleId.toHex()
            LogEntry.Outgoing(key, it.createdWallMs, names[it.recipientNodeId.toHex()] ?: "Unknown contact", it.text, it.language, ItantraNetwork.isUrgent(key), it.state)
        }
        val incoming = NetworkingRuntime.dtn.deliveredMessages().map {
            val key = it.bundleId.toHex()
            LogEntry.Incoming(key, it.receivedWallMs, names[it.senderNodeId.toHex()] ?: "Unknown contact", it.text, it.language, ItantraNetwork.isUrgent(key), key !in read)
        }
        val sos = sosRecords.map { LogEntry.Sos(it) }
        val activeSos = sos.filter { it.record.phase != OutgoingSosState.Phase.ENDED }
        return activeSos.sortedByDescending { it.wallMs } +
            (outgoing + incoming + sos.filter { it.record.phase == OutgoingSosState.Phase.ENDED }).sortedByDescending { it.wallMs }
    }

    fun findEntry(key: String): LogEntry? = logEntries().firstOrNull { it.key == key }

    // ------------------------------------------------------------------ speech playback
    var speakingKey by mutableStateOf<String?>(null)
        private set

    fun speak(key: String, text: String, language: String) {
        if (!speech.canSpeak(language)) return
        speakingKey = key
        main.launch {
            try {
                withContext(Dispatchers.Default) { speech.speak(text, language) }
            } catch (e: Throwable) {
                Log.e(TAG, "speak failed lang=$language", e)
            } finally {
                if (speakingKey == key) speakingKey = null
            }
        }
    }

    // ------------------------------------------------------------------ arrivals and SOS prompts
    var arrival by mutableStateOf<ProductDelivery?>(null)
    var dismissedPromptKey by mutableStateOf<String?>(null)
    val dismissedOffers = mutableStateListOf<String>()
    var pendingAcceptId by mutableStateOf<String?>(null)
    var outgoingSos by mutableStateOf<OutgoingSosState?>(null)
        private set
    var incomingSos by mutableStateOf<List<IncomingSos>>(emptyList())
        private set
    private val autoSentRequest = HashSet<String>()
    private val spokenChatCount = HashMap<String, Int>()
    private val cancelled = HashSet<String>()

    fun session(): EmergencySession? = NetworkingRuntime.session.value

    fun acceptSos(id: String) {
        val s = session() ?: return
        pendingAcceptId = id
        main.launch { runCatching { s.sos.accept(id) } }
    }

    fun declineSos(id: String) {
        val s = session() ?: return
        dismissedOffers.add(id)
        main.launch { runCatching { s.sos.decline(id) } }
    }

    fun cancelSos(id: String) {
        cancelled += id
        // Only the live incident this record describes; never an unrelated newer one.
        if (outgoingSos?.sosIdHex == id) session()?.sos?.cancelSos()
        val index = sosRecords.indexOfFirst { it.sosIdHex == id }
        if (index >= 0 && sosRecords[index].phase != OutgoingSosState.Phase.ENDED) {
            sosRecords[index] = sosRecords[index].copy(phase = OutgoingSosState.Phase.ENDED, endNote = "Cancelled")
        }
    }

    fun endConversation(id: String) {
        val s = session() ?: return
        cancelled += id
        main.launch { runCatching { s.sos.endSession(id) } }
    }

    fun sendChat(id: String, text: String, language: String) {
        val s = session() ?: return
        main.launch { runCatching { s.sos.sendChat(id, text, language) } }
    }

    /** Emergency-conversation reply: typed or transcribed, always sent explicitly by the user. */
    var replyDraft by mutableStateOf("")

    fun appendReply(text: String) {
        replyDraft = if (replyDraft.isBlank()) text else replyDraft.trimEnd() + " " + text
    }

    fun sendReply(id: String) {
        val text = replyDraft.trim()
        if (text.isEmpty()) return
        sendChat(id, text, languageCode)
        replyDraft = ""
    }

    /** The optional in-person code check (spec §37 State 2). Never automatic. */
    fun markVerified(id: String) {
        session()?.sos?.markNearbyVerified(id)
    }

    // ------------------------------------------------------------------ wiring
    fun init(context: Context) {
        if (wired) return
        wired = true
        app = context.applicationContext
        prefs = app.getSharedPreferences("itantra_ui", Context.MODE_PRIVATE)
        languageCode = prefs.getString("language", "en") ?: "en"
        recipientHex = prefs.getString("recipient", null)
        draftLanguage = languageCode
        if (!prefs.getBoolean("read_seeded", false)) {
            // Messages delivered before this UI existed were already shown by the previous screen.
            val existing = NetworkingRuntime.dtn.deliveredMessages().map { it.bundleId.toHex() }.toSet()
            prefs.edit().putStringSet("read_incoming", existing).putBoolean("read_seeded", true).apply()
        }

        loadSosRecords()
        main.launch { androidx.compose.runtime.snapshotFlow { sosRecords.toList() }.collect { saveSosRecords() } }
        main.launch { NetworkingRuntime.dtn.events.collect { logVersion++ } }

        // Authenticated end-recipient deliveries only: relay copies never reach this flow.
        main.launch {
            ItantraNetwork.pendingDeliveries.collect { deliveries ->
                deliveries.forEach { delivery ->
                    ItantraNetwork.acknowledgeDelivery(delivery.bundleIdHex)
                    logVersion++
                    if (delivery.urgent) arrival = delivery
                    ItantraNetwork.recordProductTiming("product_tts_start", mapOf("lang" to delivery.languageCode))
                    speak(delivery.bundleIdHex, delivery.text, delivery.languageCode)
                }
            }
        }

        main.launch {
            NetworkingRuntime.session.collectLatest { s ->
                if (s == null) {
                    outgoingSos = null
                    incomingSos = emptyList()
                    sosRecords.forEachIndexed { i, r ->
                        if (r.phase != OutgoingSosState.Phase.ENDED) {
                            sosRecords[i] = r.copy(phase = OutgoingSosState.Phase.ENDED, endNote = "Stopped: the Bluetooth radio went off")
                        }
                    }
                    return@collectLatest
                }
                coroutineScope {
                    launch { s.sos.outgoing.collect { onOutgoing(s, it) } }
                    launch { s.sos.incomingOffers.collect { onIncoming(it) } }
                }
            }
        }
    }

    private fun onOutgoing(s: EmergencySession, state: OutgoingSosState?) {
        outgoingSos = state
        state ?: return
        val index = sosRecords.indexOfFirst { it.sosIdHex == state.sosIdHex }
        if (index < 0) return
        val record = sosRecords[index]
        if (record.phase == OutgoingSosState.Phase.ENDED) return
        val connected = state.phase == OutgoingSosState.Phase.CONNECTED
        sosRecords[index] = record.copy(
            phase = state.phase,
            everConnected = record.everConnected || connected,
            offered = state.offeredCount,
            declined = state.declinedCount,
            endNote = when {
                state.phase != OutgoingSosState.Phase.ENDED -> null
                state.sosIdHex in cancelled -> if (record.everConnected || connected) "Ended" else "Cancelled"
                record.everConnected -> "Ended by the responder"
                else -> "No responder accepted in time"
            },
        )
        // The offer frame deliberately carries no text (minimal pre-accept disclosure), so the
        // requester's own words go to the responder over the encrypted chat once they accept.
        val responder = state.responderSessionKey
        if (connected && responder != null && autoSentRequest.add(state.sosIdHex + responder)) {
            main.launch {
                // CONNECTED is published just before SosManager sends SOS_CONFIRM, and the responder
                // drops chat until it has that confirm. Give the confirm a head start on the link.
                delay(900)
                runCatching { s.sos.sendChat(state.sosIdHex, record.text, record.language) }
            }
        }
        speakNewLines("out:" + state.sosIdHex, state.chat.map { Triple(it.fromMe, it.text, it.language) })
        logVersion++
    }

    private fun onIncoming(offers: List<IncomingSos>) {
        incomingSos = offers
        offers.forEach { offer ->
            speakNewLines("in:" + offer.sosIdHex, offer.chat.map { Triple(it.fromMe, it.text, it.language) })
            if (pendingAcceptId == offer.sosIdHex && offer.phase == IncomingSos.Phase.ACTIVE) {
                pendingAcceptId = null
                dismissedOffers.add(offer.sosIdHex)
                go(Route.Conversation(offer.sosIdHex))
            }
        }
    }

    private fun speakNewLines(key: String, lines: List<Triple<Boolean, String, String>>) {
        val done = spokenChatCount[key] ?: 0
        if (lines.size <= done) return
        lines.drop(done).filter { !it.first }.forEachIndexed { i, (_, text, language) ->
            speak("$key:${done + i}", text, language)
        }
        spokenChatCount[key] = lines.size
    }
}
