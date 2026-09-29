package com.chmod777.itantra.demo

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Demo-only local state: Logs, trusted names, mock transcripts, the director
 * console form and the armed timer. Persisted in its own SharedPreferences file
 * (each actor APK has its own applicationId, so its own file) and exposed as
 * StateFlows so a timer firing in the background updates a visible Logs live.
 *
 * Writes use commit() because they can happen inside a BroadcastReceiver just
 * before the process is backgrounded or killed.
 */
object DemoStore {

    private const val PREFS = "itantra_film_demo"
    private const val K_MESSAGES = "messages"
    private const val K_CONTACTS = "contacts"
    private const val K_LANGUAGE = "language"
    private const val K_TRANSCRIPTS = "transcripts"
    private const val K_FORM = "incoming_form"
    private const val K_ARMED = "armed"

    val actor: DemoActor = DemoActor.current

    private lateinit var prefs: SharedPreferences

    private val _messages = MutableStateFlow<List<DemoMessage>>(emptyList())
    val messages: StateFlow<List<DemoMessage>> = _messages.asStateFlow()

    private val _contacts = MutableStateFlow(RoleDefaults.trustedContacts(actor))
    val contacts: StateFlow<List<String>> = _contacts.asStateFlow()

    private val _language = MutableStateFlow("en")
    val language: StateFlow<String> = _language.asStateFlow()

    /** Overrides only, keyed "<kind>:<code>"; missing keys fall back to [RoleDefaults]. */
    private val _transcripts = MutableStateFlow<Map<String, String>>(emptyMap())
    val transcripts: StateFlow<Map<String, String>> = _transcripts.asStateFlow()

    private val _form = MutableStateFlow(RoleDefaults.initialIncoming(actor))
    val incomingForm: StateFlow<IncomingSpec> = _form.asStateFlow()

    private val _armed = MutableStateFlow<ArmedEvent?>(null)
    val armed: StateFlow<ArmedEvent?> = _armed.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _messages.value = prefs.getString(K_MESSAGES, null)?.let(::decodeMessages).orEmpty().sortedNewestFirst()
        prefs.getString(K_CONTACTS, null)?.let { _contacts.value = decodeStrings(it) }
        _language.value = prefs.getString(K_LANGUAGE, "en") ?: "en"
        prefs.getString(K_TRANSCRIPTS, null)?.let { _transcripts.value = decodeMap(it) }
        prefs.getString(K_FORM, null)?.let { runCatching { _form.value = decodeSpec(JSONObject(it)) } }
        prefs.getString(K_ARMED, null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                _armed.value = ArmedEvent(o.getString("id"), o.getLong("fireAt"), decodeSpec(o.getJSONObject("spec")))
            }
        }
    }

    // ---- language + transcripts -------------------------------------------------

    fun setLanguage(code: String) {
        _language.value = code
        prefs.edit().putString(K_LANGUAGE, code).commit()
    }

    fun transcript(kind: CaptureKind, code: String = _language.value): String =
        _transcripts.value[key(kind, code)] ?: RoleDefaults.transcript(actor, kind, code)

    fun setTranscript(kind: CaptureKind, code: String, text: String) {
        val next = _transcripts.value + (key(kind, code) to text)
        _transcripts.value = next
        prefs.edit().putString(K_TRANSCRIPTS, JSONObject(next).toString()).commit()
    }

    fun resetTranscripts() {
        _transcripts.value = emptyMap()
        prefs.edit().remove(K_TRANSCRIPTS).commit()
    }

    private fun key(kind: CaptureKind, code: String) = "${kind.name}:$code"

    // ---- Logs --------------------------------------------------------------------

    /** Inserts a simulated incoming message. Idempotent on [id] so a timer can never double-post. */
    @Synchronized
    fun addIncoming(spec: IncomingSpec, id: String, now: Long = System.currentTimeMillis()): DemoMessage {
        _messages.value.firstOrNull { it.id == id }?.let { return it }
        val message = DemoMessage(
            id = id,
            direction = Direction.INCOMING,
            peer = spec.sender.trim().ifEmpty { "Unknown" },
            body = spec.body,
            mode = spec.mode,
            timestamp = now,
            unread = true,
            audioUri = spec.audioUri,
            languageCode = _language.value,
        )
        saveMessages(listOf(message) + _messages.value)
        return message
    }

    @Synchronized
    fun addOutgoing(peer: String?, body: String, mode: MessageMode): DemoMessage {
        val message = DemoMessage(
            id = UUID.randomUUID().toString(),
            direction = Direction.OUTGOING,
            peer = peer,
            body = body,
            mode = mode,
            timestamp = System.currentTimeMillis(),
            unread = false,
            languageCode = _language.value,
        )
        saveMessages(listOf(message) + _messages.value)
        return message
    }

    fun message(id: String): DemoMessage? = _messages.value.firstOrNull { it.id == id }

    @Synchronized
    fun markRead(id: String) {
        if (_messages.value.none { it.id == id && it.unread }) return
        saveMessages(_messages.value.map { if (it.id == id) it.copy(unread = false) else it })
    }

    @Synchronized
    fun setSosResponse(id: String, response: SosResponse) {
        saveMessages(_messages.value.map { if (it.id == id) it.copy(sosResponse = response) else it })
    }

    @Synchronized
    fun clearLogs() = saveMessages(emptyList())

    private fun saveMessages(list: List<DemoMessage>) {
        val sorted = list.sortedNewestFirst()
        _messages.value = sorted
        prefs.edit().putString(K_MESSAGES, encodeMessages(sorted)).commit()
    }

    private fun List<DemoMessage>.sortedNewestFirst() = sortedByDescending { it.timestamp }

    // ---- trusted names -----------------------------------------------------------

    @Synchronized
    fun addContact(name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || _contacts.value.any { it.equals(clean, ignoreCase = true) }) return
        val next = _contacts.value + clean
        _contacts.value = next
        prefs.edit().putString(K_CONTACTS, JSONArray(next).toString()).commit()
    }

    // ---- director console form + armed timer -------------------------------------

    fun setIncomingForm(spec: IncomingSpec) {
        _form.value = spec
        prefs.edit().putString(K_FORM, encodeSpec(spec).toString()).commit()
    }

    @Synchronized
    fun setArmed(event: ArmedEvent?) {
        _armed.value = event
        val editor = prefs.edit()
        if (event == null) {
            editor.remove(K_ARMED)
        } else {
            editor.putString(
                K_ARMED,
                JSONObject()
                    .put("id", event.id)
                    .put("fireAt", event.fireAtMillis)
                    .put("spec", encodeSpec(event.spec))
                    .toString()
            )
        }
        editor.commit()
    }

    /** Atomically claims the armed event if it is still [id]; the caller then delivers it. */
    @Synchronized
    fun claimArmed(id: String): ArmedEvent? {
        val event = _armed.value?.takeIf { it.id == id } ?: return null
        setArmed(null)
        return event
    }

    /** Back to this actor's defaults: empty Logs, default names/transcripts/form, English, nothing armed. */
    @Synchronized
    fun resetAll() {
        _messages.value = emptyList()
        _contacts.value = RoleDefaults.trustedContacts(actor)
        _language.value = "en"
        _transcripts.value = emptyMap()
        _form.value = RoleDefaults.initialIncoming(actor)
        _armed.value = null
        prefs.edit().clear().commit()
    }

    // ---- JSON ----------------------------------------------------------------------

    private fun encodeMessages(list: List<DemoMessage>): String = JSONArray().apply {
        list.forEach { m ->
            put(
                JSONObject()
                    .put("id", m.id)
                    .put("direction", m.direction.name)
                    .put("peer", m.peer ?: JSONObject.NULL)
                    .put("body", m.body)
                    .put("mode", m.mode.name)
                    .put("ts", m.timestamp)
                    .put("unread", m.unread)
                    .put("audio", m.audioUri ?: JSONObject.NULL)
                    .put("lang", m.languageCode)
                    .put("sos", m.sosResponse.name)
            )
        }
    }.toString()

    private fun decodeMessages(raw: String): List<DemoMessage> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            DemoMessage(
                id = o.getString("id"),
                direction = Direction.valueOf(o.getString("direction")),
                peer = o.optStringOrNull("peer"),
                body = o.getString("body"),
                mode = MessageMode.valueOf(o.getString("mode")),
                timestamp = o.getLong("ts"),
                unread = o.getBoolean("unread"),
                audioUri = o.optStringOrNull("audio"),
                languageCode = o.optString("lang", "en"),
                sosResponse = SosResponse.valueOf(o.optString("sos", SosResponse.NONE.name)),
            )
        }
    }.getOrDefault(emptyList())

    private fun encodeSpec(s: IncomingSpec): JSONObject = JSONObject()
        .put("sender", s.sender)
        .put("body", s.body)
        .put("mode", s.mode.name)
        .put("audio", s.audioUri ?: JSONObject.NULL)
        .put("delay", s.delaySeconds)
        .put("notify", s.notify)

    private fun decodeSpec(o: JSONObject) = IncomingSpec(
        sender = o.getString("sender"),
        body = o.getString("body"),
        mode = MessageMode.valueOf(o.getString("mode")),
        audioUri = o.optStringOrNull("audio"),
        delaySeconds = o.optInt("delay", 5),
        notify = o.optBoolean("notify", true),
    )

    private fun decodeStrings(raw: String): List<String> = runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(RoleDefaults.trustedContacts(actor))

    private fun decodeMap(raw: String): Map<String, String> = runCatching {
        val o = JSONObject(raw)
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).ifEmpty { null }
}
