package com.chmod777.itantra

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val timestamp: String,
    val direction: MessageDirection,
    val isRead: Boolean
)

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val MessageListSaver = Saver<SnapshotStateList<Message>, ArrayList<Bundle>>(
    save = { messages ->
        ArrayList(messages.map { message ->
            Bundle().apply {
                putString("text", message.text)
                putString("timestamp", message.timestamp)
                putString("direction", message.direction.name)
                putBoolean("isRead", message.isRead)
            }
        })
    },
    restore = { savedMessages ->
        mutableStateListOf<Message>().apply {
            savedMessages.forEach { saved ->
                add(
                    Message(
                        text = checkNotNull(saved.getString("text")),
                        timestamp = checkNotNull(saved.getString("timestamp")),
                        direction = MessageDirection.valueOf(checkNotNull(saved.getString("direction"))),
                        isRead = saved.getBoolean("isRead")
                    )
                )
            }
        }
    }
)

// Fake Speech/Transport stand-ins, per docs/CONTRACTS.md. Hardcoded until the
// real lanes exist -- do not couple this UI to real STT/TTS/transport here.
private fun transcribe(wavFilePath: String): String = "this is a test message"

private fun speak(text: String, languageCode: String) {
    // no-op stand-in: no real TTS yet
}

private fun sendMessage(text: String) {
    // no-op stand-in: no real transport yet
}

private fun onMessageReceived(callback: (text: String, languageCode: String) -> Unit) {
    // no-op stand-in: no real transport yet, so this callback is never invoked
}

@Composable
fun ITantraApp(modifier: Modifier = Modifier) {
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
    var draft by rememberSaveable { mutableStateOf("") }
    var selectedMessageIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val messages = rememberSaveable(saver = MessageListSaver) {
        mutableStateListOf(
            Message("Water rising near the school", "10:42", MessageDirection.RECEIVED, isRead = false),
            Message("Six people at the temple", "10:43", MessageDirection.RECEIVED, isRead = false),
            Message("Need medical supplies", "10:44", MessageDirection.RECEIVED, isRead = false)
        )
    }
    val unreadCount = messages.count { it.direction == MessageDirection.RECEIVED && !it.isRead }

    val requestMicPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) {
        onMessageReceived { text, languageCode ->
            messages.add(
                Message(
                    text = text,
                    timestamp = LocalTime.now().format(TIME_FORMAT),
                    direction = MessageDirection.RECEIVED,
                    isRead = false
                )
            )
            speak(text, languageCode)
        }
    }

    when (screen) {
        Screen.MAIN -> MainScreen(
            unreadCount = unreadCount,
            onOpenHandsFree = { screen = Screen.HANDS_FREE },
            onOpenText = {
                draft = ""
                screen = Screen.NEW_MESSAGE
            },
            onOpenLogs = { screen = Screen.LOGS },
            onTranscript = { text ->
                draft = text
                screen = Screen.NEW_MESSAGE
            },
            modifier = modifier
        )

        Screen.HANDS_FREE -> HandsFreeScreen(
            onBack = { screen = Screen.MAIN },
            onDone = {
                draft = transcribe("")
                screen = Screen.NEW_MESSAGE
            },
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
            onBack = {
                draft = ""
                screen = Screen.MAIN
            },
            onSend = { text ->
                sendMessage(text)
                messages.add(
                    Message(
                        text = text,
                        timestamp = LocalTime.now().format(TIME_FORMAT),
                        direction = MessageDirection.SENT,
                        isRead = true
                    )
                )
                draft = ""
                screen = Screen.MAIN
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
private fun MainScreen(
    unreadCount: Int,
    onOpenHandsFree: () -> Unit,
    onOpenText: () -> Unit,
    onOpenLogs: () -> Unit,
    onTranscript: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isHolding by remember { mutableStateOf(false) }
    val view = LocalView.current
    val context = LocalContext.current
    val recordingFile = remember { File(context.filesDir, "recording.wav") }
    val recorder = remember { PcmRecorder(recordingFile) }
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
                                    val hasMicPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasMicPermission) {
                                        recorder.start()
                                    }
                                    val released = try {
                                        tryAwaitRelease()
                                    } finally {
                                        isHolding = false
                                        if (hasMicPermission) {
                                            recorder.stop()
                                        }
                                    }
                                    if (released) {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onTranscript(transcribe(recordingFile.absolutePath))
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
                text = if (isHolding) "Recording..." else "Idle",
                color = Color(0xFF91A2B4)
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
            BentoControls(
                unreadCount = unreadCount,
                onOpenHandsFree = onOpenHandsFree,
                onOpenText = onOpenText,
                onOpenLogs = onOpenLogs,
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
private fun HandsFreeScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
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
            IconButton(onClick = onBack) {
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
                text = "Listening...",
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
            onClick = onDone,
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
        Spacer(Modifier.height(20.dp))
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
        Button(
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
            Text("SEND", fontWeight = FontWeight.Bold)
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
                    text = if (isSent) "↑ Sent" else "↓ Received",
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
                text = message.timestamp,
                color = Color(0xFF91A2B4),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (isSent) "↑ Sent" else "↓ Received",
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
