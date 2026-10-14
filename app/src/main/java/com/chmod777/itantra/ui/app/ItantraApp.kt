package com.chmod777.itantra.ui.app

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.chmod777.itantra.HandsFreeScreen
import com.chmod777.itantra.LogsScreen
import com.chmod777.itantra.MainScreen
import com.chmod777.itantra.MessageDetailScreen
import com.chmod777.itantra.NewMessageScreen
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
    data class MessageDetail(val index: Int) : Route
}

@Composable
fun ITantraApp(modifier: Modifier = Modifier) {
    val stack = remember { mutableStateListOf<Route>(Route.Main) }
    ITantraApp(stack = stack, modifier = modifier)
}

@Composable
fun ITantraApp(stack: SnapshotStateList<Route>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state = rememberAppState()
    val emergencyState = rememberEmergencyState()

    fun push(route: Route) = stack.add(route)
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun resetToMain() { stack.clear(); stack.add(Route.Main) }

    BackHandler(enabled = stack.size > 1) { pop() }

    Box(Modifier.fillMaxSize().background(Color(0xFF07111C))) {
        when (val route = stack.last()) {
            Route.Main -> MainScreen(
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
                onPttReleased = { state.startTranscription("PTT") { push(Route.NewMessage) } },
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
                modifier = modifier
            )

            Route.HandsFree -> HandsFreeScreen(
                onStartRecording = state::startRecording,
                onStopRecording = state::stopRecording,
                onBack = ::pop,
                onDone = { state.startTranscription("HANDSFREE") { push(Route.NewMessage) } },
                isTranscribing = state.isTranscribing,
                modifier = modifier
            )

            Route.Logs -> LogsScreen(
                messages = state.messages,
                onBack = ::pop,
                onOpenMessage = { index ->
                    state.markRead(index)
                    push(Route.MessageDetail(index))
                },
                modifier = modifier
            )

            Route.NewMessage -> NewMessageScreen(
                draft = state.draft,
                onDraftChange = { state.draft = it },
                isSending = state.isSending,
                sendError = state.sendError,
                onBack = {
                    state.discardDraft()
                    resetToMain()
                },
                recipients = state.recipients,
                selectedRecipientHex = state.selectedRecipientHex,
                onSelectRecipient = { state.selectedRecipientHex = it },
                useLegacyTransport = state.useLegacyTransport,
                onSend = { text -> state.sendMessage(text) { resetToMain() } },
                modifier = modifier
            )

            is Route.MessageDetail -> MessageDetailScreen(
                message = state.messages[route.index],
                onBack = ::pop,
                modifier = modifier
            )
        }
    }
}
