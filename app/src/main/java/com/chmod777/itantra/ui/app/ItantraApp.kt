package com.chmod777.itantra.ui.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.chmod777.itantra.MainScreen
import com.chmod777.itantra.service.EmergencyState
import com.chmod777.itantra.sos.IncomingSos.Phase as IncomingSosPhase
import com.chmod777.itantra.sos.OutgoingSosState.Phase as OutgoingSosPhase
import com.chmod777.itantra.ui.lab.NetworkLabActivity

/**
 * In-memory back stack. A handful of screens, so no navigation library.
 *
 * Routes carry what the screen needs rather than an index into some shared state, so a screen
 * can be swapped out without touching the ones around it.
 */
sealed interface Route {
    data object Main : Route
    data object HandsFree : Route
    data object NewMessage : Route
    data object Logs : Route

    /** By id, not by position: Logs is re-read from the database and rows move. */
    data class MessageDetail(val id: String) : Route

    /**
     * The pre-redesign main screen, kept only behind the debug long-press on the wordmark.
     * It is the one place the Legacy RFCOMM switch and the "Contacts & network" door into the
     * Net Lab still live; both get a permanent home before this route is deleted.
     */
    data object Settings : Route
    data object QrScan : Route

    /**
     * The two steps of adding a contact. Both depend on a scanned capsule that is deliberately
     * not saved anywhere, so if the activity is recreated here the flow restarts rather than
     * resuming half-trusted.
     */
    data object FingerprintCheck : Route
    data object AddContactName : Route

    /** Choosing what kind of help is needed. Nothing is sent from here. */
    data object SosCategory : Route

    /** The SOS this phone is asking for: searching, then the conversation. */
    data object SosActive : Route

    /** SOS calls from other people. */
    data object IncomingSos : Route

    /** The conversation with one person this phone is helping. */
    data class HelpingSos(val sosIdHex: String) : Route

    data object ReportProblem : Route

    /** The four intro cards: once on a fresh install, and again from Settings on request. */
    data object Onboarding : Route

    data object LegacyMain : Route
}

/**
 * Opens the panel that holds Wi-Fi and mobile data, so the fourth intro card can invite someone
 * to switch them off. It only opens it; the app never changes a radio setting itself.
 */
private fun openQuickSettings(context: android.content.Context) {
    val panel = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    } else {
        Intent(Settings.ACTION_WIRELESS_SETTINGS)
    }
    runCatching { context.startActivity(panel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
}

/**
 * The back stack has to survive the activity being recreated, or a rotation mid-sentence throws
 * the writer back to Home. Routes are written as short strings rather than made Parcelable: one
 * of them carries an id and the rest carry nothing, so a saver is less machinery than it looks.
 *
 * Anything unreadable restores as Home rather than throwing. A saved stack is not input to
 * trust anything with.
 */
private fun encodeRoute(route: Route): String = when (route) {
    Route.Main -> "main"
    Route.HandsFree -> "handsfree"
    Route.NewMessage -> "new"
    Route.Logs -> "logs"
    Route.LegacyMain -> "legacy"
    Route.Settings -> "settings"
    Route.QrScan -> "scan"
    Route.FingerprintCheck -> "fingerprint"
    Route.AddContactName -> "name"
    Route.SosCategory -> "sos"
    Route.SosActive -> "sosactive"
    Route.IncomingSos -> "sosincoming"
    Route.ReportProblem -> "report"
    Route.Onboarding -> "intro"
    is Route.HelpingSos -> "helping:" + route.sosIdHex
    is Route.MessageDetail -> "detail:" + route.id
}

private fun decodeRoute(value: String): Route = when {
    value == "handsfree" -> Route.HandsFree
    value == "new" -> Route.NewMessage
    value == "logs" -> Route.Logs
    value == "legacy" -> Route.LegacyMain
    value == "settings" -> Route.Settings
    value == "scan" -> Route.QrScan
    value == "fingerprint" -> Route.FingerprintCheck
    value == "name" -> Route.AddContactName
    value == "sos" -> Route.SosCategory
    value == "sosactive" -> Route.SosActive
    value == "sosincoming" -> Route.IncomingSos
    value == "report" -> Route.ReportProblem
    value == "intro" -> Route.Onboarding
    value.startsWith("helping:") -> Route.HelpingSos(value.removePrefix("helping:"))
    value.startsWith("detail:") -> Route.MessageDetail(value.removePrefix("detail:"))
    else -> Route.Main
}

private val RouteStackSaver: Saver<SnapshotStateList<Route>, Any> =
    listSaver<SnapshotStateList<Route>, String>(
        save = { stack -> stack.map(::encodeRoute) },
        restore = { saved ->
            mutableStateListOf<Route>().apply {
                addAll(saved.map(::decodeRoute))
                if (isEmpty()) add(Route.Main)
            }
        }
    )

@Composable
fun ITantraApp(modifier: Modifier = Modifier) {
    val stack = rememberSaveable(saver = RouteStackSaver) { mutableStateListOf<Route>(Route.Main) }
    ITantraApp(stack = stack, modifier = modifier)
}

@Composable
fun ITantraApp(stack: SnapshotStateList<Route>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // No BuildConfig: the buildConfig feature is off and app/build.gradle.kts is shared with
    // the other lanes, so the debuggable flag is read off the running app instead.
    val isDebugBuild = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    val state = rememberAppState()
    val emergencyState = rememberEmergencyState()
    val store = rememberMessageStore()
    val availableToHelp = rememberAvailableToHelp()
    val outgoingSos = rememberOutgoingSos()
    // On a fresh install the intro goes on top of Home rather than replacing it, so finishing
    // it is one pop and nothing else has to know it happened.
    LaunchedEffect(Unit) { if (!state.onboardingSeen) stack.add(Route.Onboarding) }
    val incomingSos = rememberIncomingSos()

    // An SOS asked for with the radio off waits here until the session comes up.
    LaunchedEffect(emergencyState, state.pendingSosCategory) {
        if (state.pendingSosCategory != null) state.sendPendingSos()
    }

    // Shaking is for when something is wrong, not when something is happening: never while a
    // take is being recorded or decoded, and never during an SOS.
    val shakeEnabled = !state.isTranscribing &&
        outgoingSos == null &&
        incomingSos.none { it.phase == IncomingSosPhase.ACTIVE || it.phase == IncomingSosPhase.ACCEPTED_WAITING } &&
        stack.last() != Route.ReportProblem
    ShakeToReport(enabled = shakeEnabled) {
        if (stack.last() != Route.ReportProblem) stack.add(Route.ReportProblem)
    }

    // Urgent is the one thing about a sent message the DTN tables do not keep, so the store is
    // told at the moment of queueing and remembers it beside read/unread.
    LaunchedEffect(state, store) {
        state.onMessageQueued = { bundleIdHex, urgent ->
            if (urgent) store.markUrgent(bundleIdHex)
            store.refresh()
        }
    }

    // MIC_BLOCKED is the only one with somewhere to send the user; the others just clear.
    fun captureErrorAction() {
        if (state.captureError == CaptureError.MIC_BLOCKED) {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        state.dismissCaptureError()
    }

    // Leaving a screen on purpose answers the error that was on it, so it does not sit there
    // for the rest of the session. A pop does not clear it: that is how Hands-free hands the
    // "nothing heard" strip back to Home.
    // A take that finished while the screen was being recreated is still waiting in the session.
    // Whichever composition is alive now picks it up, which is why the transcript is not lost
    // when the phone is rotated at the moment the recognizer returns.
    LaunchedEffect(state.pendingTranscript) {
        val transcript = state.takePendingTranscript() ?: return@LaunchedEffect
        state.applyTranscript(transcript)
        if (stack.last() != Route.NewMessage) {
            state.dismissCaptureError()
            stack.add(Route.NewMessage)
        }
    }

    // Out of the add-a-contact flow and back to the list, however it ended.
    fun backToSettings() {
        while (stack.size > 1 && stack.last() != Route.Settings) stack.removeAt(stack.lastIndex)
        if (stack.last() != Route.Settings) stack.add(Route.Settings)
        state.refreshContacts()
    }

    fun push(route: Route) {
        state.dismissCaptureError()
        stack.add(route)
    }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun resetToMain() {
        state.dismissCaptureError()
        stack.clear()
        stack.add(Route.Main)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    // The one screen still coming from LegacyScreens.kt never applied window insets of its own:
    // it leaned on the Scaffold that MainActivity no longer has. Every new screen handles its
    // own, which is why only this one is wrapped.
    val legacyInsets = modifier.statusBarsPadding().navigationBarsPadding()

    Box(Modifier.fillMaxSize().background(Color(0xFF07111C))) {
        when (val route = stack.last()) {
            Route.Main -> HomeScreen(
                languageCode = state.selectedLanguageCode,
                unreadCount = store.unreadCount,
                isTranscribing = state.isTranscribing,
                emergencyState = emergencyState,
                onLanguageSelected = { state.selectedLanguageCode = it },
                onStartRecording = state::startRecording,
                onStopRecording = state::stopRecording,
                onPttReleased = { state.startTranscription("PTT") },
                // Checked here rather than inside the screen: with no microphone there is
                // nothing for Hands-free to do but pulse at the user.
                onHandsFree = { if (state.requestCapture()) push(Route.HandsFree) },
                onWrite = {
                    state.beginTypedMessage()
                    push(Route.NewMessage)
                },
                onLogs = { push(Route.Logs) },
                onStartEmergency = state::startEmergencyMode,
                onStopEmergency = state::stopEmergencyMode,
                onSettings = {
                    state.refreshContacts()
                    push(Route.Settings)
                },
                // A running SOS is reopened rather than started again.
                onSos = { push(if (outgoingSos != null) Route.SosActive else Route.SosCategory) },
                sosInProgress = outgoingSos != null,
                incomingSosCount = incomingSos.count { it.phase == IncomingSosPhase.OFFERED },
                onOpenIncomingSos = { push(Route.IncomingSos) },
                onDeveloper = if (isDebugBuild) ({ push(Route.LegacyMain) }) else null,
                captureError = state.captureError,
                onCaptureErrorAction = ::captureErrorAction,
                modifier = modifier
            )

            Route.LegacyMain -> MainScreen(
                unreadCount = state.unreadCount,
                recordingFile = state.recordingFile,
                onStartRecording = state::startRecording,
                onStopRecording = state::stopRecording,
                onOpenHandsFree = { push(Route.HandsFree) },
                onOpenText = {
                    state.beginTypedMessage()
                    push(Route.NewMessage)
                },
                onOpenLogs = { push(Route.Logs) },
                onPttReleased = { state.startTranscription("PTT") },
                selectedLanguageCode = state.selectedLanguageCode,
                onLanguageChange = { state.selectedLanguageCode = it },
                isTranscribing = state.isTranscribing,
                speech = state.speech,
                transportState = state.transportState,
                pairedDevices = state.pairedDevices,
                onConnect = state::connectTo,
                onListen = state::listenForBluetooth,
                emergencyState = emergencyState,
                onStartEmergency = state::startEmergencyMode,
                onStopEmergency = state::stopEmergencyMode,
                onOpenNetwork = { context.startActivity(Intent(context, NetworkLabActivity::class.java)) },
                useLegacyTransport = state.useLegacyTransport,
                onLegacyTransportChange = { state.useLegacyTransport = it },
                modifier = legacyInsets
            )

            Route.Settings -> SettingsScreen(
                selfName = state.selfName,
                selfFingerprint = state.selfFingerprint,
                contactCode = state.selfContactCode,
                contacts = state.contacts,
                availableToHelp = availableToHelp,
                onAvailableChange = state::setAvailableToHelp,
                onRenameSelf = state::renameSelf,
                onAddContact = {
                    state.clearPendingContact()
                    state.contactError = null
                    push(Route.QrScan)
                },
                onRenameContact = state::renameContact,
                onRemoveContact = state::removeContact,
                onBack = ::pop,
                onReportProblem = { push(Route.ReportProblem) },
                onShowIntro = { push(Route.Onboarding) },
                onDeveloper = if (isDebugBuild) ({ push(Route.LegacyMain) }) else null,
                modifier = modifier
            )

            Route.Onboarding -> OnboardingFlow(
                initialName = state.selfName,
                onNameChange = { introName = it },
                onAddContact = {
                    state.markOnboardingSeen()
                    introName.takeIf { it.isNotBlank() }?.let(state::renameSelf)
                    resetToMain()
                    state.clearPendingContact()
                    stack.add(Route.Settings)
                    stack.add(Route.QrScan)
                },
                onOpenQuickSettings = { openQuickSettings(context) },
                onFinish = {
                    state.markOnboardingSeen()
                    introName.takeIf { it.isNotBlank() }?.let(state::renameSelf)
                    pop()
                },
                modifier = modifier
            )

            Route.ReportProblem -> {
                // Collected fresh each time the screen opens, so what is shown is what is sent.
                val report = remember { collectProblemReport(context, state.selectedLanguageCode) }
                ProblemReportScreen(
                    report = report,
                    onShare = { description -> shareProblemReport(context, report.asText(description)) },
                    onBack = ::pop,
                    modifier = modifier
                )
            }

            Route.SosCategory -> SosCategoryScreen(
                emergencyOn = emergencyState is EmergencyState.On,
                starting = state.pendingSosCategory != null,
                onSend = { category ->
                    state.startSos(category)
                    stack.removeAt(stack.lastIndex)
                    stack.add(Route.SosActive)
                },
                onBack = ::pop,
                modifier = modifier
            )

            Route.SosActive -> {
                val sos = outgoingSos
                val starting = state.pendingSosCategory != null
                if (sos == null && !starting) {
                    // Cancelled, ended, or never started because the radio could not come up.
                    LaunchedEffect(Unit) { resetToMain() }
                } else if (sos != null && sos.phase == OutgoingSosPhase.ENDED) {
                    LaunchedEffect(sos.phase) { resetToMain() }
                } else if (sos == null || sos.phase == OutgoingSosPhase.SEARCHING) {
                    // Also the screen for the seconds before the incident exists: asking for
                    // help has to look like something immediately, not bounce back Home.
                    SosSearchScreen(
                        state = sos,
                        category = state.pendingSosCategory,
                        onCancel = {
                            state.cancelSos()
                            resetToMain()
                        },
                        modifier = modifier
                    )
                } else {
                    SosChatScreen(
                        title = "Help is here",
                        trust = sos.trust,
                        shortAuthString = sos.shortAuthString,
                        lines = sos.chat,
                        draft = state.sosDraft,
                        onDraftChange = { state.sosDraft = it },
                        onSend = { state.sendSosChat(sos.sosIdHex) },
                        onMarkVerified = { state.markSosVerified(sos.sosIdHex) },
                        onBack = ::pop,
                        modifier = modifier
                    )
                }
            }

            Route.IncomingSos -> IncomingSosScreen(
                offers = incomingSos,
                onAccept = { offer -> state.acceptSos(offer.sosIdHex) },
                onDecline = { offer -> state.declineSos(offer.sosIdHex) },
                onOpen = { offer -> push(Route.HelpingSos(offer.sosIdHex)) },
                onBack = ::pop,
                modifier = modifier
            )

            is Route.HelpingSos -> {
                val offer = incomingSos.firstOrNull { it.sosIdHex == route.sosIdHex }
                if (offer == null) {
                    LaunchedEffect(route.sosIdHex) { pop() }
                } else {
                    SosChatScreen(
                        title = offer.category.label,
                        trust = offer.trust,
                        shortAuthString = offer.shortAuthString,
                        lines = offer.chat,
                        draft = state.sosDraft,
                        onDraftChange = { state.sosDraft = it },
                        onSend = { state.sendSosChat(offer.sosIdHex) },
                        onMarkVerified = { state.markSosVerified(offer.sosIdHex) },
                        onBack = ::pop,
                        modifier = modifier
                    )
                }
            }

            Route.QrScan -> QrScanScreen(
                onBack = {
                    state.clearPendingContact()
                    pop()
                },
                // The screen hands up the raw payload; whether it is worth anything is decided
                // in one place, by the state holder.
                onCode = { payload -> if (state.onContactScanned(payload)) push(Route.FingerprintCheck) },
                error = state.contactError,
                modifier = modifier
            )

            Route.FingerprintCheck -> {
                val capsule = state.pendingCapsule
                if (capsule == null) {
                    LaunchedEffect(Unit) { backToSettings() }
                } else {
                    FingerprintCheckScreen(
                        fingerprint = capsule.fingerprint,
                        onMatch = {
                            state.confirmFingerprint()
                            stack.add(Route.AddContactName)
                        },
                        onReject = {
                            state.clearPendingContact()
                            state.contactError =
                                "Nothing was saved. If the codes do not match, the person on the " +
                                    "other end is not who you think."
                            backToSettings()
                        },
                        onBack = {
                            state.clearPendingContact()
                            pop()
                        },
                        modifier = modifier
                    )
                }
            }

            Route.AddContactName -> {
                val capsule = state.pendingCapsule
                // Reached only through a confirmed comparison. If either half is missing -- a
                // recreation, or any path that skipped the check -- there is nothing to save.
                if (capsule == null || !state.fingerprintConfirmed) {
                    LaunchedEffect(Unit) {
                        state.clearPendingContact()
                        backToSettings()
                    }
                } else {
                    AddContactNameScreen(
                        nameHint = capsule.displayNameHint,
                        fingerprint = capsule.fingerprint,
                        onBack = {
                            state.clearPendingContact()
                            backToSettings()
                        },
                        onSave = { name ->
                            state.saveTrustedContact(name)
                            backToSettings()
                        },
                        modifier = modifier
                    )
                }
            }

            Route.HandsFree -> HandsFreeScreen(
                onStartRecording = state::startRecording,
                onStopRecording = state::stopRecording,
                onBack = ::pop,
                // A take that heard nothing leaves the person here, on the screen they were
                // using, with the reason under the orb. Nothing navigates on a failure.
                onDone = { state.startTranscription("HANDSFREE") },
                isTranscribing = state.isTranscribing,
                captureError = state.captureError,
                onCaptureErrorAction = ::captureErrorAction,
                modifier = modifier
            )

            Route.Logs -> LogsScreen(
                messages = store.messages,
                onBack = ::pop,
                onOpen = { message -> push(Route.MessageDetail(message.id)) },
                modifier = modifier
            )

            Route.NewMessage -> ComposerScreen(
                draft = state.draft,
                onDraftChange = { state.draft = it },
                mode = state.composerMode,
                onModeChange = { state.composerMode = it },
                recipients = state.recipients,
                selectedRecipientHex = state.selectedRecipientHex,
                onSelectRecipient = { state.selectedRecipientHex = it },
                emergencyOn = emergencyState is EmergencyState.On,
                onStartEmergency = state::startEmergencyMode,
                isSending = state.isSending,
                sendError = state.sendError,
                onDismissError = { state.sendError = null },
                sentToken = state.sentCount,
                onSend = { state.sendMessage(state.draft, state.composerMode == MessageMode.URGENT) {} },
                onSent = { resetToMain() },
                onBack = {
                    state.discardDraft()
                    resetToMain()
                },
                modifier = modifier
            )

            is Route.MessageDetail -> {
                val message = store.messages.firstOrNull { it.id == route.id }
                if (message == null) {
                    // The row went while the screen was open: an empty detail screen would be
                    // worse than going back to the list it came from.
                    LaunchedEffect(route.id) { pop() }
                } else {
                    MessageDetailScreen(
                        message = message,
                        onBack = ::pop,
                        onOpened = { store.markRead(message.id) },
                        onReplay = { state.speakAgain(message.body, message.languageCode) },
                        isTranscribing = state.isTranscribing,
                        onStartRecording = state::startRecording,
                        onStopRecording = state::stopRecording,
                        // A reply goes back to whoever wrote it, so the recipient is chosen for
                        // the user rather than asked for again.
                        onPttReply = {
                            message.peerNodeIdHex?.let { state.selectedRecipientHex = it }
                            state.startTranscription("REPLY")
                        },
                        onHandsFreeReply = { push(Route.HandsFree) },
                        onTypeReply = {
                            state.beginTypedMessage()
                            message.peerNodeIdHex?.let { state.selectedRecipientHex = it }
                            push(Route.NewMessage)
                        },
                        modifier = modifier
                    )
                }
            }
        }
    }
}
