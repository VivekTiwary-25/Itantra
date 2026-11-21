package com.chmod777.itantra.ui.app

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max

/** Field-radio palette: navy/charcoal ground, radio yellow for voice, muted amber Urgent, warm red SOS. */
object Ink {
    val Bg = Color(0xFF0A111C)
    val BgRaised = Color(0xFF101A28)
    val Clay = Color(0xFF16222F)
    val ClayHi = Color(0xFF1E2C3C)
    val ClayLo = Color(0xFF0C141F)
    val Line = Color(0x1FB6CFE7)
    val Text = Color(0xFFEEF2F7)
    val TextDim = Color(0xFFA2B0C0)
    val TextMute = Color(0xFF6B7B8E)
    val Signal = Color(0xFFF4B740)
    val SignalInk = Color(0xFF2A1B02)
    val Neutral = Color(0xFFDDE4EC)
    val NeutralInk = Color(0xFF111A25)
    val Urgent = Color(0xFFCB8A3E)
    val UrgentInk = Color(0xFF26170A)
    val Sos = Color(0xFFE0574C)
    val SosInk = Color(0xFF2B0A07)
    val Ok = Color(0xFF86C9A6)
    val Glass = Color(0xE6132030)
}

@Composable
fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

/** Soft clay surface: raised by a dark drop shadow, lit from the top-left, sinking when pressed. */
fun Modifier.clay(shape: Shape, base: Color, pressed: Boolean = false, elevation: Dp = 12.dp): Modifier {
    val top = lerp(base, Color.White, if (pressed) 0.02f else 0.10f)
    val bottom = lerp(base, Color.Black, if (pressed) 0.06f else 0.22f)
    return this
        .shadow(if (pressed) elevation / 4 else elevation, shape, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(shape)
        .background(Brush.linearGradient(if (pressed) listOf(bottom, top) else listOf(top, bottom)))
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = if (pressed) 0.04f else 0.14f), Color.Transparent)), shape)
}

/** Restrained glass for temporary surfaces: sheets and contextual prompts. */
fun Modifier.glass(shape: Shape): Modifier = this
    .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color(0xF01B2A3B), Color(0xF0111B28))))
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f))), shape)

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink.Text)
        }
        leading?.invoke()
        Text(title, color = Ink.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Tactile primary action (Send, Accept). Depresses on touch; shows a quiet busy label. */
@Composable
fun ClayButton(
    label: String,
    onClick: () -> Unit,
    color: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(90), label = "press")
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .scale(scale)
            .heightIn(min = height)
            .clay(shape, if (enabled) color else Ink.ClayHi, pressed)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { it(); Spacer(Modifier.width(8.dp)) }
        Text(label, color = if (enabled) ink else Ink.TextMute, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun QuietButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Ink.TextDim) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Live level history rendered as centred bars; [level] is polled, never faked. */
@Composable
fun LiveWaveform(level: () -> Float, color: Color, modifier: Modifier = Modifier, bars: Int = 18, maxHeight: Dp = 44.dp) {
    val history = remember { mutableStateListOf<Float>().apply { repeat(bars) { add(0f) } } }
    LaunchedEffect(Unit) {
        while (true) {
            history.removeAt(0)
            history.add(level())
            delay(70)
        }
    }
    Canvas(modifier.height(maxHeight).semantics { contentDescription = "Live audio level" }) {
        val gap = size.width / (bars * 2f)
        val barW = gap
        history.forEachIndexed { i, v ->
            val h = max(size.height * 0.08f, size.height * v.coerceIn(0f, 1f))
            val x = gap / 2 + i * gap * 2
            drawRoundRect(color, Offset(x, (size.height - h) / 2), Size(barW, h), CornerRadius(barW / 2))
        }
    }
}

private val STATIC_BARS = listOf(8, 16, 26, 13, 22, 10, 18, 28, 12, 20, 9, 24, 15, 11, 19, 7)

/** The existing Hands-free tile's decorative waveform. */
@Composable
fun StaticWaveform(color: Color, modifier: Modifier = Modifier) {
    Row(modifier.height(28.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        STATIC_BARS.forEach { h -> Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(color)) }
    }
}

/** Slow, subtle pulse for an active SOS indicator; static when reduced motion is on. */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    val still = reducedMotion()
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    Box(modifier.size(size).clip(CircleShape).background(color.copy(alpha = if (still) 1f else a)))
}

/** Outlined pencil-in-square compose symbol. */
@Composable
fun ComposeGlyph(color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val s = size.minDimension
        val w = s * 0.09f
        val stroke = Stroke(w, cap = StrokeCap.Round)
        // Square open at the top-right where the pencil enters.
        val left = s * 0.12f
        val top = s * 0.18f
        val right = s * 0.82f
        val bottom = s * 0.88f
        drawLine(color, Offset(s * 0.5f, top), Offset(left, top), w, StrokeCap.Round)
        drawLine(color, Offset(left, top), Offset(left, bottom), w, StrokeCap.Round)
        drawLine(color, Offset(left, bottom), Offset(right, bottom), w, StrokeCap.Round)
        drawLine(color, Offset(right, bottom), Offset(right, s * 0.52f), w, StrokeCap.Round)
        // Pencil.
        drawLine(color, Offset(s * 0.40f, s * 0.60f), Offset(s * 0.86f, s * 0.14f), w * 1.35f, StrokeCap.Round)
        drawCircle(color, w * 0.6f, Offset(s * 0.38f, s * 0.62f), style = stroke)
    }
}

/** Restrained chat symbol for Logs. */
@Composable
fun ChatGlyph(color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val s = size.minDimension
        val w = s * 0.09f
        drawRoundRect(color, Offset(s * 0.1f, s * 0.14f), Size(s * 0.8f, s * 0.58f), CornerRadius(s * 0.16f), style = Stroke(w))
        drawLine(color, Offset(s * 0.3f, s * 0.72f), Offset(s * 0.22f, s * 0.9f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.22f, s * 0.9f), Offset(s * 0.46f, s * 0.72f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.3f, s * 0.36f), Offset(s * 0.7f, s * 0.36f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.3f, s * 0.52f), Offset(s * 0.56f, s * 0.52f), w, StrokeCap.Round)
    }
}

/** Small text tag; never colour alone — the word carries the meaning. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    }
}

@Composable
fun ComingSoonScreen(title: String, onHome: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        ScreenHeader("", onBack)
        Spacer(Modifier.height(96.dp))
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = Ink.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text("Coming soon", color = Ink.Signal, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("This part of iTantra isn't in this build yet.", color = Ink.TextDim, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            ClayButton("Back to Home", onHome, Ink.ClayHi, Ink.Text, Modifier.fillMaxWidth())
        }
    }
}
