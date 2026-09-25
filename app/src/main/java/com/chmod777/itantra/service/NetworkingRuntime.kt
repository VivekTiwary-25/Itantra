package com.chmod777.itantra.service

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.chmod777.itantra.crypto.Primitives
import com.chmod777.itantra.crypto.toHex
import com.chmod777.itantra.dtn.DtnClock
import com.chmod777.itantra.dtn.DtnNode
import com.chmod777.itantra.dtn.sqlite.NetDatabase
import com.chmod777.itantra.identity.KeyManager
import com.chmod777.itantra.identity.LocalIdentity
import com.chmod777.itantra.metrics.BenchmarkRunner
import com.chmod777.itantra.metrics.JsonlMetricsRecorder
import com.chmod777.itantra.metrics.ProbeService
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.session.NetworkingCore
import com.chmod777.itantra.session.PeerCapabilities
import com.chmod777.itantra.sos.OutgoingSosState
import com.chmod777.itantra.sos.SosManager
import com.chmod777.itantra.transport.ble.BleLinkManager
import com.chmod777.itantra.transport.rfcomm.RfcommLinkConnector
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Android clocks (spec §30, audit #8). */
class AndroidDtnClock(private val context: Context) : DtnClock {
    override fun elapsedMs() = SystemClock.elapsedRealtime()
    override fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    override fun wallMs() = System.currentTimeMillis()
    override fun monotonicNs() = SystemClock.elapsedRealtimeNanos()
}

sealed interface EmergencyState {
    data object Off : EmergencyState
    data object Starting : EmergencyState
    data object On : EmergencyState
    data class Error(val message: String) : EmergencyState
}

/**
 * One Emergency-mode session: fresh Noise static key and advertised ID, BLE +
 * optional RFCOMM links, the hop-secure core, SOS and probes. Discarded on stop.
 */
class EmergencySession(
    context: Context,
    parentScope: CoroutineScope,
    val config: ProtocolConfig,
    clock: DtnClock,
    metrics: JsonlMetricsRecorder,
    dtn: DtnNode,
    availableToHelp: Boolean,
) {
    // Radio-facing background work (inventory rounds, SOS waves) must never take the whole
    // app down: a send on a dying link crashed the process in the two-phone test.
    val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]) +
            CoroutineExceptionHandler { _, e -> metrics.record("uncaught_coroutine_exception", mapOf("type" to e.javaClass.simpleName, "cause" to e.message)) },
    )
    lateinit var core: NetworkingCore
        private set
    val probes = ProbeService(clock, metrics, config) { core.sessionHandles() }
    val ble = BleLinkManager(context, scope, config, clock, metrics) { link -> core.attach(link) }
    val rfcomm = RfcommLinkConnector(context, scope, config, metrics) { link -> core.attach(link) }
    val sos = SosManager(
        scope, clock, config, metrics,
        sessions = { core.sessionHandles() },
        rssiSamples = ble::rssiSamples,
        helpersInRange = ble::helpersInRange,
        prioritizeConnections = ble::prioritize,
    ).also { it.availableToHelp = availableToHelp }
    val benchmark = BenchmarkRunner(probes, metrics.file.parentFile!!, metrics.deviceLabel, metrics.runId)

    init {
        core = NetworkingCore(scope, dtn, config, clock, metrics, listOf(probes, sos), capabilities(availableToHelp))
    }

    suspend fun start() {
        core.start()
        ble.start()
        rfcomm.listen()
        // Advertise SOS_ACTIVE while an incident is live.
        scope.launch {
            sos.outgoing.collect { state ->
                val active = state != null && state.phase != OutgoingSosState.Phase.ENDED
                ble.setFlags(sosActive = active, availableToHelp = sos.availableToHelp)
            }
        }
    }

    fun setAvailableToHelp(available: Boolean) {
        sos.availableToHelp = available
        core.localCapabilities = capabilities(available)
        val active = sos.outgoing.value?.let { it.phase != OutgoingSosState.Phase.ENDED } ?: false
        ble.setFlags(sosActive = active, availableToHelp = available)
    }

    fun stop() {
        sos.cancelSos()
        core.stop()
        ble.stop()
        rfcomm.stop()
        scope.cancel()
    }

    private fun capabilities(available: Boolean) = PeerCapabilities(
        config.protocolMajor,
        PeerCapabilities.FLAG_DTN_RELAY or (if (available) PeerCapabilities.FLAG_SOS_RESPONDER else 0),
    )
}

/**
 * Process-scoped networking state. The DTN store, identity and contacts live for
 * the whole process so messages can be queued while Emergency mode is off; radio
 * work only happens inside an [EmergencySession] owned by [EmergencyModeService].
 */
object NetworkingRuntime {
    val config: ProtocolConfig = ProtocolConfig.DEFAULT
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e ->
                if (initialized) metrics.record("uncaught_coroutine_exception", mapOf("type" to e.javaClass.simpleName, "cause" to e.message))
            },
    )

    private var initialized = false
    lateinit var clock: AndroidDtnClock
        private set
    lateinit var keyManager: KeyManager
        private set
    lateinit var database: NetDatabase
        private set
    lateinit var dtn: DtnNode
        private set
    lateinit var metrics: JsonlMetricsRecorder
        private set
    lateinit var benchDirectory: File
        private set

    private val mutableIdentity = MutableStateFlow<LocalIdentity?>(null)
    val identity: StateFlow<LocalIdentity?> = mutableIdentity

    private val mutableState = MutableStateFlow<EmergencyState>(EmergencyState.Off)
    val emergencyState: StateFlow<EmergencyState> = mutableState

    private val mutableSession = MutableStateFlow<EmergencySession?>(null)
    val session: StateFlow<EmergencySession?> = mutableSession

    private val mutableAvailableToHelp = MutableStateFlow(false)
    val availableToHelp: StateFlow<Boolean> = mutableAvailableToHelp

    private var sweepJob: Job? = null
    private lateinit var prefs: android.content.SharedPreferences

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        clock = AndroidDtnClock(app)
        prefs = app.getSharedPreferences("itantra_networking", Context.MODE_PRIVATE)
        mutableAvailableToHelp.value = prefs.getBoolean(KEY_AVAILABLE, false)
        keyManager = KeyManager(app)
        val loaded = keyManager.loadOrCreate(defaultDisplayName = "iTantra user")
        mutableIdentity.value = loaded
        database = NetDatabase(app, config)
        dtn = DtnNode(loaded, database, database, clock, config)
        benchDirectory = File(app.getExternalFilesDir(null) ?: app.filesDir, "bench")
        val runId = "${System.currentTimeMillis()}-${Primitives.randomBytes(2).toHex()}"
        metrics = JsonlMetricsRecorder(benchDirectory, deviceLabel(), runId, clock)
        metrics.record("runtime_init", mapOf("sdk" to Build.VERSION.SDK_INT, "key_protection" to keyManager.wrappingKeySecurity))
        initialized = true
        // Startup sweep re-bases bundle ages onto the current boot before anything routes (audit #8).
        sweepJob = scope.launch {
            while (isActive) {
                runCatching { dtn.sweep() }.onFailure { metrics.record("sweep_failed", mapOf("cause" to it.message)) }
                delay(config.expirySweepIntervalMs)
            }
        }
    }

    fun renameSelf(displayName: String) {
        val current = mutableIdentity.value ?: return
        mutableIdentity.value = keyManager.rename(current, displayName)
    }

    fun setAvailableToHelp(available: Boolean) {
        mutableAvailableToHelp.value = available
        prefs.edit().putBoolean(KEY_AVAILABLE, available).apply()
        mutableSession.value?.setAvailableToHelp(available)
    }

    internal fun wasEmergencyEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    /** Called only by [EmergencyModeService] after it is in the foreground. */
    internal suspend fun startEmergency(context: Context) {
        if (mutableSession.value != null) return
        mutableState.value = EmergencyState.Starting
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        val session = EmergencySession(context.applicationContext, scope, config, clock, metrics, dtn, availableToHelp.value)
        mutableSession.value = session
        try {
            session.start()
            mutableState.value = EmergencyState.On
            metrics.record("emergency_on", emptyMap())
        } catch (e: Exception) {
            mutableState.value = EmergencyState.Error("Could not start: ${e.message}")
            stopEmergency(userRequested = false)
        }
    }

    internal fun stopEmergency(userRequested: Boolean) {
        if (userRequested) prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        mutableSession.value?.stop()
        mutableSession.value = null
        if (mutableState.value !is EmergencyState.Error) mutableState.value = EmergencyState.Off
        metrics.record("emergency_off", mapOf("user" to userRequested))
    }

    internal fun reportError(message: String) {
        mutableState.value = EmergencyState.Error(message)
    }

    private fun deviceLabel(): String = "${Build.MODEL.replace(' ', '_')}-${Primitives.randomBytes(2).toHex()}"

    private const val KEY_AVAILABLE = "available_to_help"
    private const val KEY_ENABLED = "emergency_enabled"
}
