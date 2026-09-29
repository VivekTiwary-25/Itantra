package com.chmod777.itantra.demo.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.demo.MessageMode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The one send confirmation (~1.25 s): matte backdrop, the send mark lifts away,
 * a ring sweeps closed, a disc settles and a tick draws, then the contextual
 * line ("Sent to Yash" / "SOS sent"). Normal and SOS differ only in accent.
 */
@Composable
fun SendOverlay(mode: MessageMode, label: String, onFinished: () -> Unit) {
    val view = LocalView.current
    val finished by rememberUpdatedState(onFinished)
    val accent = mode.accent()
    val onAccent = if (mode.isSos) Color.White else Ink.onGold

    val backdrop = remember { Animatable(0f) }
    val plane = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val disc = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    val caption = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { backdrop.animateTo(1f, tween(160)) }
            launch { plane.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
            launch { delay(120); ring.animateTo(1f, tween(430, easing = LinearOutSlowInEasing)) }
            launch { delay(460); disc.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
            launch {
                delay(560)
                tick.animateTo(1f, tween(230, easing = FastOutSlowInEasing))
            }
            launch {
                delay(600)
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                    else HapticFeedbackConstants.VIRTUAL_KEY
                )
            }
            launch { delay(640); caption.animateTo(1f, tween(220)) }
        }
        delay(350)
        finished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = backdrop.value }
            .background(Ink.night)
            // Swallow touches so nothing underneath can be tapped mid-animation.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(132.dp)) {
                    val stroke = 3.dp.toPx()
                    drawCircle(accent.copy(alpha = 0.14f), style = Stroke(stroke))
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * ring.value,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                    val r = size.minDimension / 2f - stroke * 3
                    drawCircle(accent, radius = r * disc.value)
                    if (tick.value > 0f) {
                        val path = Path().apply {
                            moveTo(center.x - r * 0.38f, center.y + r * 0.02f)
                            lineTo(center.x - r * 0.1f, center.y + r * 0.3f)
                            lineTo(center.x + r * 0.42f, center.y - r * 0.28f)
                        }
                        val measure = PathMeasure().apply { setPath(path, false) }
                        val partial = Path()
                        measure.getSegment(0f, measure.length * tick.value, partial, true)
                        drawPath(partial, onAccent, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer {
                            translationY = -plane.value * 46.dp.toPx()
                            translationX = plane.value * 22.dp.toPx()
                            alpha = 1f - plane.value
                            rotationZ = -18f * plane.value
                        }
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                label,
                color = Ink.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.graphicsLayer {
                    alpha = caption.value
                    translationY = (1f - caption.value) * 10.dp.toPx()
                }
            )
        }
    }
}
