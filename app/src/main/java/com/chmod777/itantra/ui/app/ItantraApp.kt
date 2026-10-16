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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.chmod777.itantra.MainScreen
import com.chmod777.itantra.service.EmergencyState
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
    data object LegacyMain : Route
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
    is Route.MessageDetail -> "detail:" + route.id
}

private fun decodeRoute(value: String): Route = when {
    value == "handsfree" -> Route.HandsFree
    value == "new" -> Route.NewMessage
    value == "logs" -> Route.Logs
    value == "legacy" -> Route.LegacyMain
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
