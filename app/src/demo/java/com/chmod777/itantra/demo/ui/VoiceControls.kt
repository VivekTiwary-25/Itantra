package com.chmod777.itantra.demo.ui

import android.graphics.BlurMaskFilter
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class OrbState { IDLE, HELD, LISTENING, TRANSCRIBING }

private val OrbState.capturing get() = this == OrbState.HELD || this == OrbState.LISTENING

/** Soft blurred drop shadow under a rounded shape: the "clay" lift for primary controls. */
fun Modifier.clayShadow(
    radius: Dp,
    elevation: Dp = 10.dp,
    color: Color = Color.Black.copy(alpha = 0.55f),
): Modifier = drawBehind {
    val blur = elevation.toPx() * 1.6f
    drawIntoCanvas { canvas ->
        val paint = Paint()
        paint.asFrameworkPaint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        val r = radius.toPx()
        canvas.drawRoundRect(0f, elevation.toPx() * 0.6f, size.width, size.height + elevation.toPx() * 0.6f, r, r, paint)
    }
}

/**
 * The one talk orb. Home uses it at ~200dp, Message Detail's reply row at ~64dp,
 * Hands-free uses it in the continuous LISTENING state. Visual only; gestures
 * come from [holdToTalk] or the caller.
 */
@Composable
fun TalkOrb(
    state: OrbState,
    diameter: Dp,
    modifier: Modifier = Modifier,
    showRipples: Boolean = true,
    idleContent: @Composable () -> Unit,
) {
    val pressScale by animateFloatAsState(
        targetValue = if (state.capturing) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "orb press"
    )
    val captureAlpha by animateFloatAsState(if (state.capturing) 1f else 0f, tween(160), label = "capture")
    val busyAlpha by animateFloatAsState(if (state == OrbState.TRANSCRIBING) 1f else 0f, tween(160), label = "busy")
    // Idle orbs must not animate: an always-running transition redraws the blurred
    // shadows every frame. Ripple/sweep clocks exist only while something moves.
    val ripple = if (captureAlpha > 0f) loopingProgress(1500) else 0f
    val sweep = if (busyAlpha > 0f) loopingProgress(900) * 360f else 0f
    val bigOrb = diameter > 100.dp

    Box(
        modifier = modifier
            .size(diameter)
            .drawBehind {
                val r = size.minDimension / 2f
                if (showRipples && captureAlpha > 0f) {
                    for (i in 0 until 3) {
                        val p = (ripple + i / 3f) % 1f
                        drawCircle(
                            color = Ink.gold.copy(alpha = (1f - p) * 0.42f * captureAlpha),
                            radius = r * (1f + p * (if (bigOrb) 0.42f else 0.55f)),
                            style = Stroke(width = (if (bigOrb) 2.dp else 1.5.dp).toPx())
                        )
                    }
                }
                if (busyAlpha > 0f) {
                    drawArc(
                        color = Ink.gold.copy(alpha = 0.9f * busyAlpha),
                        startAngle = sweep,
                        sweepAngle = 70f,
                        useCenter = false,
                        topLeft = Offset(-r * 0.12f, -r * 0.12f),
                        size = Size(size.width + r * 0.24f, size.height + r * 0.24f),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clayShadow(radius = diameter / 2, elevation = if (bigOrb) 14.dp else 7.dp)
            .clip(RoundedCornerShape(50))
            .drawBehind { drawClayBody(pressed = state.capturing) },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.graphicsLayer { alpha = 1f - maxOf(captureAlpha, busyAlpha) }) { idleContent() }
        if (captureAlpha > 0f) {
            LiveWaveform(
                color = Ink.onGold,
                bars = if (bigOrb) 9 else 5,
                modifier = Modifier
                    .graphicsLayer { alpha = captureAlpha }
                    .size(width = diameter * 0.52f, height = diameter * 0.34f)
            )
        }
        if (busyAlpha > 0f) {
            BusyDots(Modifier.graphicsLayer { alpha = busyAlpha }, dot = if (bigOrb) 9.dp else 5.dp)
        }
    }
}

private fun DrawScope.drawClayBody(pressed: Boolean) {
    val r = size.minDimension / 2f
    drawCircle(
        brush = Brush.radialGradient(
            colors = if (pressed) listOf(Color(0xFFF2B456), Ink.gold, Color(0xFFB86A06))
            else listOf(Ink.goldLight, Ink.gold, Ink.goldDark),
            center = Offset(size.width * 0.36f, size.height * 0.30f),
            radius = r * 1.55f
        )
    )
    if (pressed) {
        // Inset shading: the surface reads as pushed in.
        drawCircle(
            brush = Brush.radialGradient(
                0.62f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.22f),
                center = center,
                radius = r
            )
        )
    } else {
        drawCircle(
            color = Color.White.copy(alpha = 0.22f),
            radius = r - 1.dp.toPx(),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

@Composable
private fun BusyDots(modifier: Modifier, dot: Dp) {
    val t by rememberInfiniteTransition(label = "dots")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "dots t")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(dot * 0.7f)) {
        repeat(3) { i ->
            val a = 0.35f + 0.65f * abs(sin(((t - i * 0.18f) * PI).toFloat()))
            Box(
                Modifier
                    .size(dot)
                    .graphicsLayer { alpha = a }
                    .clip(RoundedCornerShape(50))
                    .drawBehind { drawCircle(Ink.onGold) }
            )
        }
    }
}

/** 0→1 repeating clock. Only call it while the caller is actually animating. */
@Composable
private fun loopingProgress(periodMs: Int): Float {
    val t by rememberInfiniteTransition(label = "loop")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "loop t")
    return t
}

/** Animated voice bars shared by the orb, the Hands-free tile and the listening screen. */
@Composable
fun LiveWaveform(color: Color, bars: Int, modifier: Modifier = Modifier, animated: Boolean = true) {
    val t = if (animated) loopingProgress(1300) else 0f
    Canvas(modifier) {
        val gap = size.width / (bars * 2f - 1f)
        val barW = gap
        for (i in 0 until bars) {
            val phase = i * 0.61f
            val speed = 1f + (i % 3) * 0.5f
            val level = if (animated) 0.22f + 0.78f * abs(sin((t * speed * 2 * PI + phase).toFloat()))
            else STATIC_LEVELS[i % STATIC_LEVELS.size]
            val h = size.height * level
            drawRoundRect(
                color = color,
                topLeft = Offset(i * gap * 2f, (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}

private val STATIC_LEVELS = listOf(0.35f, 0.7f, 1f, 0.55f, 0.85f, 0.4f, 0.65f, 0.3f)

/**
 * Press-and-hold gesture for any talk orb. [onHoldStart] on touch-down;
 * [onHoldEnd] with the hold duration once every finger has lifted. Sliding off
 * the orb mid-sentence does not end the take (it matters on the small reply orb).
 */
fun Modifier.holdToTalk(
    enabled: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: (heldMillis: Long) -> Unit,
): Modifier = this.then(
    if (!enabled) Modifier else Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val start = SystemClock.uptimeMillis()
            onHoldStart()
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
            onHoldEnd(SystemClock.uptimeMillis() - start)
        }
    }
)

/** Minimum hold that counts as speech; shorter taps are ignored (no accidental drafts mid-take). */
const val MIN_HOLD_MS = 350L

/**
 * State + haptics for one hold-to-talk control. Both the Home orb and the reply
 * orb use this so they behave identically.
 */
class HoldToTalkState {
    var orb by mutableStateOf(OrbState.IDLE)
        internal set

    fun start(view: android.view.View) {
        if (orb != OrbState.IDLE) return
        orb = OrbState.HELD
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun end(view: android.view.View, heldMillis: Long) {
        if (orb != OrbState.HELD) return
        if (heldMillis < MIN_HOLD_MS) {
            orb = OrbState.IDLE
            return
        }
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        orb = OrbState.TRANSCRIBING
    }

    fun reset() {
        orb = OrbState.IDLE
    }
}

/** Callers observe [HoldToTalkState.orb]: TRANSCRIBING means a take just finished. */
@Composable
fun rememberHoldToTalk(): HoldToTalkState = remember { HoldToTalkState() }

@Composable
fun Modifier.holdToTalk(state: HoldToTalkState, enabled: Boolean = true): Modifier {
    val view = LocalView.current
    return holdToTalk(
        enabled = enabled,
        onHoldStart = { state.start(view) },
        onHoldEnd = { ms -> state.end(view, ms) }
    )
}

/** Matte clay tile under the Home orb that opens the continuous Hands-free capture. */
@Composable
fun HandsFreeTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "hf")
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clayShadow(22.dp, elevation = 8.dp)
            .clip(shape)
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(Ink.surfaceRaised, Ink.surface)))
                drawRoundRect(Color.White.copy(alpha = 0.05f), style = Stroke(1.dp.toPx()), cornerRadius = CornerRadius(22.dp.toPx()))
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Hands-free", color = Ink.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text("Speak freely, tap Done when finished", color = Ink.muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        LiveWaveform(Ink.gold.copy(alpha = 0.85f), bars = 8, animated = false, modifier = Modifier.size(width = 58.dp, height = 26.dp))
    }
}

/**
 * Message Detail's reply controls: the same orb, Hands-free and typing entry points
 * as Home, in a compact row. [ptt] must be created with [rememberHoldToTalk].
 */
@Composable
fun ReplyControlRow(
    ptt: HoldToTalkState,
    onHandsFree: () -> Unit,
    onType: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
    ) {
        ReplyItem(label = if (ptt.orb == OrbState.TRANSCRIBING) "Transcribing…" else "Hold to talk") {
            TalkOrb(
                state = ptt.orb,
                diameter = 66.dp,
                modifier = Modifier.holdToTalk(ptt, enabled = ptt.orb != OrbState.TRANSCRIBING)
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = "Hold to talk", tint = Ink.onGold, modifier = Modifier.size(28.dp))
            }
        }
        ReplyItem(label = "Hands-free") {
            ClayRoundButton(onClick = onHandsFree, enabled = ptt.orb == OrbState.IDLE) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = "Hands-free", tint = Ink.gold, modifier = Modifier.size(28.dp))
            }
        }
        ReplyItem(label = "Type") {
            ClayRoundButton(onClick = onType, enabled = ptt.orb == OrbState.IDLE) {
                Icon(Icons.Outlined.Keyboard, contentDescription = "Type", tint = Ink.text, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun ReplyItem(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(104.dp)) {
        Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) { content() }
        Spacer(Modifier.height(10.dp))
        Text(label, color = Ink.muted, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Neutral round clay button (secondary sibling of the gold orb). */
@Composable
fun ClayRoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 66.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.55f), label = "clay")
    Box(
        modifier = modifier
            .size(diameter)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clayShadow(diameter / 2, elevation = 7.dp)
            .clip(RoundedCornerShape(50))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFF1C3146), Ink.surfaceRaised, Ink.surface),
                        center = Offset(size.width * 0.35f, size.height * 0.3f),
                        radius = size.minDimension * 0.8f
                    )
                )
                drawCircle(Color.White.copy(alpha = 0.07f), style = Stroke(1.dp.toPx()), radius = size.minDimension / 2f - 0.5.dp.toPx())
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** One quiet centred status line under an orb ("Listening…", "Transcribing…"). */
@Composable
fun StatusLine(text: String, modifier: Modifier = Modifier, color: Color = Ink.muted) {
    Box(modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}
