package com.chmod777.itantra.ui.app

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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.MessageDeliveryState

/**
 * The one message surface: reading a received message, or checking on one you sent.
 * Incoming messages get the shared reply row.
 */
@Composable
internal fun MessageDetailScreen(
    message: UiMessage,
    onBack: () -> Unit,
    onOpened: () -> Unit,
    onReplay: () -> Unit,
    isTranscribing: Boolean,
    onStartRecording: () -> Boolean,
    onStopRecording: () -> Unit,
    onPttReply: () -> Unit,
    onHandsFreeReply: () -> Unit,
    onTypeReply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberNow()
    LaunchedEffect(message.id) { onOpened() }

    val incoming = message.direction == Direction.INCOMING
    val title = if (incoming) message.title() else if (message.peer != null) "To ${message.peer}" else "Help request"

    Column(
        modifier
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
            message.delivery?.let { delivery ->
                Spacer(Modifier.height(6.dp))
                Text(delivery.tagText(), color = delivery.tagColor(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(20.dp))
            Text(message.body, color = Ink.text, fontSize = 28.sp, lineHeight = 39.sp, fontWeight = FontWeight.Normal)
            if (message.delivery == MessageDeliveryState.EXPIRED) {
                Spacer(Modifier.height(16.dp))
                Text("Nobody carried this far enough in time. Send it again.", color = Ink.muted, fontSize = 16.sp)
            }
            Spacer(Modifier.height(28.dp))
            if (incoming) ReplayControl(onReplay)
        }
        if (incoming && message.sosResponse != SosResponse.DECLINED) {
            ReplyControlRow(
                isTranscribing = isTranscribing,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onPttReleased = onPttReply,
                onHandsFree = onHandsFreeReply,
                onType = onTypeReply,
                modifier = Modifier.padding(top = 12.dp, bottom = 22.dp)
            )
        }
    }
}

// Only text ever arrived, so this speaks it again rather than playing anything back.
@Composable
private fun ReplayControl(onReplay: () -> Unit) {
    Row(
        Modifier
            .clayShadow(26.dp, elevation = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(Ink.surfaceRaised)
            .clickable(onClick = onReplay)
            .padding(start = 16.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Ink.gold,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text("Play again", color = Ink.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}
