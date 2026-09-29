package com.chmod777.itantra.demo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.chmod777.itantra.demo.MessageMode

/** Field-radio palette: matte navy surfaces, gold primary, amber Urgent, warm-red SOS. All opaque. */
object Ink {
    val night = Color(0xFF07111C)
    val deep = Color(0xFF040B13)
    val surface = Color(0xFF0E1B29)
    val surfaceRaised = Color(0xFF142536)
    val inset = Color(0xFF09141F)
    val line = Color(0xFF1B2C3D)
    val text = Color(0xFFF4F7FB)
    val body = Color(0xFFDCE6EF)
    val muted = Color(0xFF91A2B4)
    val quiet = Color(0xFF5E7084)
    val gold = Color(0xFFF8AD3C)
    val goldLight = Color(0xFFFFD07A)
    val goldDark = Color(0xFFC87508)
    val onGold = Color(0xFF2C1800)
    val amber = Color(0xFFD9A04A)
    val amberSurface = Color(0xFF2A2215)
    val sos = Color(0xFFE0604F)
    val sosLight = Color(0xFFF08A78)
    val sosSurface = Color(0xFF2A171A)
    val unread = Color(0xFF6FD4DF)
}

fun MessageMode.accent(): Color = when (this) {
    MessageMode.NORMAL -> Ink.gold
    MessageMode.URGENT -> Ink.amber
    MessageMode.SOS, MessageMode.SOS_REPLY -> Ink.sos
}

@Composable
fun DemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ink.gold,
            onPrimary = Ink.onGold,
            secondary = Ink.muted,
            background = Ink.night,
            onBackground = Ink.text,
            surface = Ink.surface,
            onSurface = Ink.text,
            surfaceVariant = Ink.surfaceRaised,
            onSurfaceVariant = Ink.muted,
            surfaceContainerLow = Ink.surface,
            surfaceContainer = Ink.surface,
            surfaceContainerHigh = Ink.surfaceRaised,
            surfaceContainerHighest = Ink.surfaceRaised,
            outline = Ink.line,
            outlineVariant = Ink.line,
            error = Ink.sos,
            scrim = Color(0xFF000000),
        ),
        content = content
    )
}
