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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
                    HoldToTalkScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun HoldToTalkScreen(modifier: Modifier = Modifier) {
    var isHolding by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    val view = LocalView.current
    val context = LocalContext.current
    val requestMicPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val recorder = remember { PcmRecorder(File(context.filesDir, "recording.wav")) }
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

    if (showLogs) {
        LogsScreen(
            messages = FAKE_MESSAGES.reversed(),
            onBack = { showLogs = false },
            modifier = modifier
        )
        return
    }

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
                    val recordingFile = File(context.filesDir, "recording.wav")
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
            LogsRow(
                count = FAKE_MESSAGES.size,
                onClick = { showLogs = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
        }
    }
}

private data class FakeMessage(val text: String, val timestamp: String)

private val FAKE_MESSAGES = listOf(
    FakeMessage("Water rising near the school", "10:42"),
    FakeMessage("Six people at the temple", "10:43"),
    FakeMessage("Need medical supplies", "10:44")
)

@Composable
private fun LogsRow(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color(0xBA0D1D2B))
            .border(1.dp, Color(0x21B6CFE7), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            tint = Color(0xFFF4F7FB),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Logs",
            color = Color(0xFFF4F7FB),
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFFF8AD3C))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = count.toString(),
                color = Color(0xFF2C1800),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF91A2B4),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun LogsScreen(messages: List<FakeMessage>, onBack: () -> Unit, modifier: Modifier = Modifier) {
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
            items(messages) { message ->
                MessageCard(message)
            }
        }
    }
}

@Composable
private fun MessageCard(message: FakeMessage) {
    val cardShape = RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 15.dp,
        bottomEnd = 15.dp,
        bottomStart = 4.dp
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(cardShape)
            .background(Color(0xBA0D1D2B))
            .border(1.dp, Color(0x21B6CFE7), cardShape)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(Color(0xFF6FD4DF))
        )
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 14.dp, bottom = 10.dp)
        ) {
            Text(
                text = message.timestamp,
                color = Color(0xFF91A2B4),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = message.text,
                color = Color(0xFFDCE6EF),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
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
fun GreetingPreview() {
    SIH_iTantraTheme {
        HoldToTalkScreen()
    }
}
