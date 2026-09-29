package com.chmod777.itantra.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.demo.DemoMessage
import com.chmod777.itantra.demo.Direction
import com.chmod777.itantra.demo.MessageMode
import com.chmod777.itantra.demo.SosResponse
import kotlinx.coroutines.delay

@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

fun DemoMessage.title(): String = when {
    direction == Direction.INCOMING -> peer ?: "Someone nearby"
    peer != null -> peer
    else -> "Help request"
}

@Composable
fun LogsScreen(messages: List<DemoMessage>, onBack: () -> Unit, onOpen: (DemoMessage) -> Unit) {
    val now = rememberNow()
    val listState = rememberLazyListState()
    // A new arrival goes to the top: keep it in view.
    LaunchedEffect(messages.firstOrNull()?.id) { listState.animateScrollToItem(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.night)
            .statusBarsPadding()
    ) {
        TopBar(title = "Logs", onBack = onBack)
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No messages yet", color = Ink.quiet, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    LogRow(message, now, onClick = { onOpen(message) })
                }
            }
        }
    }
}

@Composable
private fun LogRow(message: DemoMessage, now: Long, onClick: () -> Unit) {
    val unread = message.direction == Direction.INCOMING && message.unread
    val outgoing = message.direction == Direction.OUTGOING
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.width(20.dp).height(24.dp), contentAlignment = Alignment.Center) {
            if (unread) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (message.mode.isSos) Ink.sos else Ink.unread)
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (message.mode != MessageMode.NORMAL) {
                    ModeTag(message.mode)
                    Spacer(Modifier.width(8.dp))
                }
                if (outgoing) {
                    Icon(
                        Icons.AutoMirrored.Rounded.CallMade,
                        contentDescription = "Sent",
                        tint = Ink.quiet,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    message.title(),
                    color = Ink.text,
                    fontSize = 17.sp,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    relativeTime(message.timestamp, now),
                    color = if (unread) Ink.text else Ink.quiet,
                    fontSize = 13.sp,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                (if (outgoing) "You: " else "") + message.body,
                color = if (unread) Ink.body else Ink.muted,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when (message.sosResponse) {
                SosResponse.ACCEPTED -> Meta("You accepted", Ink.sosLight)
                SosResponse.DECLINED -> Meta("Declined", Ink.quiet)
                SosResponse.NONE -> Unit
            }
        }
    }
}

@Composable
private fun Meta(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, color = color, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
}
