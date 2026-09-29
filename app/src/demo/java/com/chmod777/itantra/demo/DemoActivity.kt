package com.chmod777.itantra.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import com.chmod777.itantra.demo.ui.AddContactFormScreen
import com.chmod777.itantra.demo.ui.ComposerDraft
import com.chmod777.itantra.demo.ui.ComposerScreen
import com.chmod777.itantra.demo.ui.DemoTheme
import com.chmod777.itantra.demo.ui.DirectorConsole
import com.chmod777.itantra.demo.ui.HandsFreeScreen
import com.chmod777.itantra.demo.ui.HomeScreen
import com.chmod777.itantra.demo.ui.Ink
import com.chmod777.itantra.demo.ui.LogsScreen
import com.chmod777.itantra.demo.ui.MessageDetailScreen
import com.chmod777.itantra.demo.ui.QrScanScreen

/** In-memory back stack. The demo has a handful of screens; no navigation library needed. */
sealed interface Route {
    data object Home : Route
    data object Logs : Route
    data object Director : Route
    data class Detail(val messageId: String) : Route
    /** [replyToId] null = started from Home. */
    data class HandsFree(val replyToId: String?) : Route
    class Composer(val draft: ComposerDraft) : Route
    class QrScan(val draft: ComposerDraft) : Route
    class AddContact(val draft: ComposerDraft) : Route
}

class DemoActivity : ComponentActivity() {

    companion object {
        const val ACTION_OPEN_LOGS = "com.chmod777.itantra.demo.OPEN_LOGS"
        const val ACTION_ACCEPT_SOS = "com.chmod777.itantra.demo.ACCEPT_SOS"
    }

    private val stack = mutableStateListOf<Route>(Route.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DemoStore.init(this)
        DemoNotifications.ensureChannels(this)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { DemoTheme { DemoApp(stack) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        DemoAudio.pause()
    }

    /** Notification taps land on Logs (never straight on the detail) whether cold or warm. */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_LOGS -> openLogs()
            ACTION_ACCEPT_SOS -> {
                intent.getStringExtra(DemoEvents.EXTRA_MESSAGE_ID)?.let { id ->
                    DemoStore.setSosResponse(id, SosResponse.ACCEPTED)
                    DemoNotifications.cancel(this, id)
                }
                openLogs()
            }
        }
    }

    private fun openLogs() {
        stack.clear()
        stack.add(Route.Home)
        stack.add(Route.Logs)
    }
}

@Composable
private fun DemoApp(stack: SnapshotStateList<Route>) {
    val messages by DemoStore.messages.collectAsState()
    val contacts by DemoStore.contacts.collectAsState()
    val language by DemoStore.language.collectAsState()

    fun push(route: Route) = stack.add(route)
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replaceTop(route: Route) { stack[stack.lastIndex] = route }
    fun resetTo(vararg routes: Route) { stack.clear(); stack.addAll(routes) }

    BackHandler(enabled = stack.size > 1) { pop() }

    Box(Modifier.fillMaxSize().background(Ink.night)) {
        AnimatedContent(
            targetState = stack.last(),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "screen"
        ) { route ->
            when (route) {
                Route.Home -> HomeScreen(
                    languageCode = language,
                    unreadCount = messages.count { it.direction == Direction.INCOMING && it.unread },
                    onLanguageSelected = DemoStore::setLanguage,
                    onPttCaptured = { push(Route.Composer(ComposerDraft(DemoStore.transcript(CaptureKind.PTT)))) },
                    onHandsFree = { push(Route.HandsFree(replyToId = null)) },
                    onWrite = { push(Route.Composer(ComposerDraft("", focusOnOpen = true))) },
                    onLogs = { push(Route.Logs) },
                    onDirector = { push(Route.Director) },
                )

                Route.Logs -> LogsScreen(
                    messages = messages,
                    onBack = ::pop,
                    onOpen = { push(Route.Detail(it.id)) },
                )

                is Route.Detail -> {
                    val message = messages.firstOrNull { it.id == route.messageId }
                    if (message == null) {
                        // Cleared from the director console while open.
                        LaunchedEffect(Unit) { pop() }
                    } else {
                        MessageDetailScreen(
                            message = message,
                            onBack = ::pop,
                            onOpened = { DemoStore.markRead(message.id) },
                            onPttReply = {
                                push(Route.Composer(ComposerDraft.reply(message, DemoStore.transcript(CaptureKind.PTT))))
                            },
                            onHandsFreeReply = { push(Route.HandsFree(replyToId = message.id)) },
                            onTypeReply = { push(Route.Composer(ComposerDraft.reply(message, "", focus = true))) },
                        )
                    }
                }

                is Route.HandsFree -> {
                    val replyTo = route.replyToId?.let(DemoStore::message)
                    HandsFreeScreen(
                        replyTo = replyTo?.peer,
                        onBack = ::pop,
                        onTranscribed = {
                            val text = DemoStore.transcript(CaptureKind.HANDS_FREE)
                            replaceTop(
                                Route.Composer(if (replyTo != null) ComposerDraft.reply(replyTo, text) else ComposerDraft(text))
                            )
                        },
                    )
                }

                is Route.Composer -> ComposerScreen(
                    draft = route.draft,
                    contacts = contacts,
                    onBack = ::pop,
                    onAddContact = { push(Route.QrScan(route.draft)) },
                    onSent = {
                        val draft = route.draft
                        DemoStore.addOutgoing(draft.outgoingRecipient, draft.text.text.trim(), draft.outgoingMode)
                        if (draft.isReply) resetTo(Route.Home, Route.Logs) else resetTo(Route.Home)
                    },
                )

                is Route.QrScan -> QrScanScreen(
                    onBack = ::pop,
                    onScanned = { replaceTop(Route.AddContact(route.draft)) },
                )

                is Route.AddContact -> AddContactFormScreen(
                    onBack = ::pop,
                    onSave = { name ->
                        DemoStore.addContact(name)
                        route.draft.recipient = name
                        pop()
                    },
                )

                Route.Director -> DirectorConsole(onClose = ::pop)
            }
        }
    }
}
