package com.chmod777.itantra.demo.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.demo.MessageMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** "← Title" bar shared by every pushed screen. */
@Composable
fun TopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    titleAccessory: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Ink.text)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            title,
            color = Ink.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (titleAccessory != null) {
            Spacer(Modifier.width(10.dp))
            titleAccessory()
        }
    }
}

/** Small restrained mode tag: nothing for Normal, amber "Urgent", red "SOS". */
@Composable
fun ModeTag(mode: MessageMode, modifier: Modifier = Modifier) {
    val (label, fg, bg) = when (mode) {
        MessageMode.NORMAL -> return
        MessageMode.URGENT -> Triple("Urgent", Ink.amber, Ink.amberSurface)
        MessageMode.SOS, MessageMode.SOS_REPLY -> Triple("SOS", Ink.sosLight, Ink.sosSurface)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(fg))
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
    }
}

/** Large pill action (Send, Done, Save). Depresses on touch. */
@Composable
fun PrimaryPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ink.gold,
    contentColor: Color = Ink.onGold,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "pill")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.4f }
            .clayShadow(29.dp, elevation = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = contentColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (icon != null) {
            Spacer(Modifier.width(10.dp))
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        }
    }
}

fun relativeTime(timestamp: Long, now: Long): String {
    val diff = (now - timestamp).coerceAtLeast(0)
    return when {
        diff < 60_000 -> "Now"
        diff < 60 * 60_000 -> "${diff / 60_000}m"
        isSameDay(timestamp, now) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
    }
}

fun detailTime(timestamp: Long, now: Long): String {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    return if (isSameDay(timestamp, now)) "Today, $time"
    else SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}
