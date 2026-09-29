package com.chmod777.itantra.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.demo.DemoAudio
import com.chmod777.itantra.demo.DemoMessage
import com.chmod777.itantra.demo.Direction
import com.chmod777.itantra.demo.SosResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The one message surface: Yash reading Vachana, Vivek reading the SOS,
 * Vachana reading either reply. Incoming messages get the shared reply row.
 */
@Composable
fun MessageDetailScreen(
    message: DemoMessage,
    onBack: () -> Unit,
    onOpened: () -> Unit,
    onPttReply: () -> Unit,
    onHandsFreeReply: () -> Unit,
    onTypeReply: () -> Unit,
) {
    val now = rememberNow()
    LaunchedEffect(message.id) { onOpened() }

    val ptt = rememberHoldToTalk()
    LaunchedEffect(ptt.orb) {
        if (ptt.orb == OrbState.TRANSCRIBING) {
            delay(TRANSCRIBE_BEAT_MS)
            onPttReply()
            ptt.reset()
        }
    }

    val incoming = message.direction == Direction.INCOMING
    val title = if (incoming) message.title() else if (message.peer != null) "To ${message.peer}" else "Help request"

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.night)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopBar(title = title, onBack = onBack, titleAccessory = { ModeTag(message.mode) })
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            val meta = buildString {
                append(detailTime(message.timestamp, now))
                if (message.sosResponse == SosResponse.ACCEPTED) append("  ·  You accepted")
            }
            Text(meta, color = Ink.quiet, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))
            Text(message.body, color = Ink.text, fontSize = 28.sp, lineHeight = 39.sp, fontWeight = FontWeight.Normal)
            Spacer(Modifier.height(28.dp))
            message.audioUri?.let { AudioControl(it) }
        }
        if (incoming && message.sosResponse != SosResponse.DECLINED) {
            ReplyControlRow(
                ptt = ptt,
                onHandsFree = onHandsFreeReply,
                onType = onTypeReply,
                modifier = Modifier.padding(top = 12.dp, bottom = 22.dp)
            )
        }
    }
}

/** Single Play audio ⇄ Pause control. No replay, no scrubber, no autoplay. */
@Composable
private fun AudioControl(uri: String) {
    val context = LocalContext.current
    var available by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) { available = withContext(Dispatchers.IO) { DemoAudio.isAvailable(context, uri) } }
    DisposableEffect(uri) { onDispose { DemoAudio.release() } }
    if (!available) return

    val playingUri by DemoAudio.playingUri.collectAsState()
    val playing = playingUri == uri
    Row(
        Modifier
            .clayShadow(26.dp, elevation = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(Ink.surfaceRaised)
            .clickable { if (!DemoAudio.toggle(context, uri)) available = false }
            .padding(start = 16.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Ink.gold,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(if (playing) "Pause" else "Play audio", color = Ink.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}
