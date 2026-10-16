package com.chmod777.itantra.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val CARD_COUNT = 4
private const val LAST_CARD = CARD_COUNT - 1
private const val MAX_NAME = 32

/**
 * First-run walkthrough: four cards, a skip on every one.
 *
 * It only reports. The name goes out through [onNameChange] as it is typed and is saved by
 * whoever owns identity; the buttons that touch the rest of the app just call up. No card
 * claims anything has been done, because at the moment a card is read, it has not.
 */
@Composable
internal fun OnboardingFlow(
    initialName: String,
    onNameChange: (String) -> Unit,
    onAddContact: () -> Unit,
    onOpenQuickSettings: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saveable: a rotation or process restart must not send someone back to card 1.
    var card by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(initialName) }

    val next: () -> Unit = { if (card < LAST_CARD) card++ else onFinish() }
    BackHandler(enabled = card > 0) { card-- }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.night)
            .statusBarsPadding()
            .navigationBarsPadding()
            // The name field lives on card 2; without this the keyboard sits on top of NEXT.
            .imePadding()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgressDots(current = card)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onFinish)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("SKIP", color = Ink.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }
        }

        AnimatedContent(
            targetState = card,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(tween(240)) + slideInHorizontally(tween(240)) { dir * it / 10 }) togetherWith
                    fadeOut(tween(120))
            },
            label = "onboardingCard"
        ) { index ->
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center
                ) {
                    when (index) {
                        0 -> CardText(
                            index,
                            "Works with no signal",
                            "Speech becomes text, text hops from phone to phone, and becomes speech again. No tower, no internet.",
                            art = { HopArt() }
                        )
                        1 -> {
                            CardText(index, "Your name", body = null)
                            NameField(
                                value = name,
                                onValueChange = {
                                    name = it.take(MAX_NAME)
                                    onNameChange(name)
                                },
                                onDone = { if (name.isNotBlank()) next() }
                            )
                            Text(
                                "This is what your contacts see.",
                                color = Ink.muted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                            )
                        }
                        2 -> CardText(
                            index,
                            "Add your first contact",
                            "Scan a QR code face to face. Only people you add can message you, and only you can read what they send."
                        )
                        else -> CardText(
                            index,
                            "Prove it",
                            "Turn mobile data and Wi-Fi off. Everything here still works — that is the point.",
                            // Said out loud because someone about to flip switches should know the
                            // app is not the one flipping them.
                            footnote = "This only opens the panel. iTantra does not switch anything off."
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                when (index) {
                    0 -> PrimaryPill("NEXT", onClick = next)
                    // A blank name would show contacts an empty label.
                    1 -> PrimaryPill("NEXT", onClick = next, enabled = name.isNotBlank())
                    2 -> {
                        PrimaryPill("ADD A CONTACT", onClick = onAddContact)
                        Spacer(Modifier.height(12.dp))
                        PrimaryPill("LATER", onClick = next, color = Ink.surfaceRaised, contentColor = Ink.text)
                    }
                    else -> {
                        PrimaryPill("OPEN QUICK SETTINGS", onClick = onOpenQuickSettings)
                        Spacer(Modifier.height(12.dp))
                        PrimaryPill("DONE", onClick = onFinish, color = Ink.surfaceRaised, contentColor = Ink.text)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun CardText(
    index: Int,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    art: (@Composable () -> Unit)? = null,
    footnote: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        if (art != null) {
            art()
            Spacer(Modifier.height(36.dp))
        }
        Text(
            "${index + 1} OF $CARD_COUNT",
            color = Ink.gold,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(title, color = Ink.text, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
        if (body != null) {
            Spacer(Modifier.height(16.dp))
            Text(body, color = Ink.body, fontSize = 18.sp, lineHeight = 28.sp)
        } else {
            Spacer(Modifier.height(20.dp))
        }
        if (footnote != null) {
            Spacer(Modifier.height(16.dp))
            Text(footnote, color = Ink.muted, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.surface)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Ink.text, fontSize = 20.sp),
            cursorBrush = SolidColor(Ink.gold),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Three phones in a line with a message mid-hop between them: the idea, without words. */
@Composable
private fun HopArt(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(56.dp)) {
        val y = size.height / 2
        val xs = listOf(0.08f, 0.5f, 0.92f).map { it * size.width }
        val stroke = 3.dp.toPx()
        for (i in 0 until xs.lastIndex) {
            drawLine(Ink.line, Offset(xs[i], y), Offset(xs[i + 1], y), strokeWidth = stroke, cap = StrokeCap.Round)
            drawCircle(Ink.goldLight, radius = 4.dp.toPx(), center = Offset((xs[i] + xs[i + 1]) / 2, y))
        }
        xs.forEach { x ->
            drawCircle(Ink.surfaceRaised, radius = 14.dp.toPx(), center = Offset(x, y))
            drawCircle(Ink.gold, radius = 7.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun ProgressDots(current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Step ${current + 1} of $CARD_COUNT" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(CARD_COUNT) { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp, tween(200), label = "dot")
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == current) Ink.gold else Ink.quiet)
            )
        }
    }
}

/**
 * Shown immediately before Android's own permission dialog, so the person has read why before
 * they are asked. It explains; the system dialog is the request. Nothing here may suggest the
 * permission is already granted.
 *
 * The four reasons it carries:
 *  - microphone: "iTantra needs the microphone to turn your voice into text"
 *  - nearby devices: "iTantra uses Bluetooth to reach other phones without a tower"
 *  - notifications: "So you hear a message or an SOS when the app is closed"
 *  - camera: "Only to read another phone's code"
 */
@Composable
internal fun PermissionRationale(
    title: String,
    reason: String,
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.night)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = Ink.text, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Text(reason, color = Ink.body, fontSize = 18.sp, lineHeight = 28.sp)
            Spacer(Modifier.height(16.dp))
            Text("Android will ask you next.", color = Ink.muted, fontSize = 14.sp)
        }
        PrimaryPill("CONTINUE", onClick = onContinue)
        Spacer(Modifier.height(12.dp))
        PrimaryPill("NOT NOW", onClick = onNotNow, color = Ink.surfaceRaised, contentColor = Ink.text)
        Spacer(Modifier.height(20.dp))
    }
}
