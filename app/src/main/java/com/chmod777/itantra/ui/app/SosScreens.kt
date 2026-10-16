package com.chmod777.itantra.ui.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.protocol.SosCategory
import com.chmod777.itantra.sos.IncomingSos
import com.chmod777.itantra.sos.OutgoingSosState
import com.chmod777.itantra.sos.SosChatLine
import com.chmod777.itantra.sos.SosTrustState

/**
 * Category first, the way the engine models it: [SosCategory] is on the wire, so the picker is
 * not decoration — it is the one field a responder sees before deciding to come.
 *
 * I NEED HELP stays disabled until a category is chosen. A panic tap cannot send a blank call.
 */
@Composable
internal fun SosCategoryScreen(
    emergencyOn: Boolean,
    starting: Boolean,
    onSend: (SosCategory) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var chosen by rememberSaveable { mutableStateOf<SosCategory?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .background(Ink.night)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopBar(title = "SOS", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(
                "Who needs help, and what kind?",
                color = Ink.body,
                fontSize = 17.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            val rows = SosCategory.entries.chunked(2)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { category ->
                        CategoryTile(
                            category = category,
                            selected = chosen == category,
                            onClick = { chosen = category },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
            if (!emergencyOn) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Emergency mode is off. Sending this will switch it on, so nearby phones can " +
                        "hear you.",
                    color = Ink.muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
        PrimaryPill(
            label = if (starting) "Starting…" else "I need help",
            color = Ink.sos,
            contentColor = Color.White,
            enabled = chosen != null && !starting,
            onClick = { chosen?.let(onSend) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        )
    }
}

@Composable
private fun CategoryTile(
    category: SosCategory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Ink.sosSurface else Ink.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Ink.sos else Ink.line)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                category.label,
                color = if (selected) Ink.text else Ink.body,
                fontSize = if (category.label.length > 10) 15.sp else 17.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

/**
 * What [OutgoingSosState] already reports, shown as it is: how many phones were asked, how many
 * said no, and which wave the search is on. No invented progress bar.
 */
@Composable
internal fun SosSearchScreen(
    /** Null in the seconds between asking for help and the radio having an incident to report. */
    state: OutgoingSosState?,
    category: SosCategory?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Ink.night)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        SosPulse()
        Spacer(Modifier.height(36.dp))
        Text(
            state?.phase?.label ?: "Switching the radio on…",
            color = Ink.sosLight,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
        if (state != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                buildString {
                    append("asked ").append(state.offeredCount)
                    if (state.declinedCount > 0) append(" · ").append(state.declinedCount).append(" said no")
                },
                color = Ink.muted,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(4.dp))
            Text("Wave ${state.wave}", color = Ink.quiet, fontSize = 14.sp)
        }
        val shown = state?.category ?: category
        if (shown != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                askedFor(shown),
                color = Ink.body,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryPill(
            label = "Cancel SOS",
            color = Ink.surfaceRaised,
            contentColor = Ink.text,
            onClick = onCancel,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        )
    }
}

private fun askedFor(category: SosCategory) = "You asked for help with: ${category.label}"

@Composable
private fun SosPulse() {
    val transition = rememberInfiniteTransition(label = "sos pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "sos pulse progress"
    )
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2
            listOf(progress, (progress + 0.5f) % 1f).forEach { p ->
                drawCircle(
                    color = Ink.sos.copy(alpha = (1f - p) * 0.5f),
                    radius = maxRadius * (0.35f + p * 0.65f)
                )
            }
            drawCircle(color = Ink.sos, radius = maxRadius * 0.3f)
        }
    }
}

/**
 * The chat with whoever answered, and above it the thing that decides whether to believe them.
 *
 * The six digits come from [OutgoingSosState.shortAuthString] / [IncomingSos.shortAuthString],
 * which the engine derives from the live Noise handshake hash bound to this incident. They are
 * printed exactly as given and never re-derived here. The screen shows no code at all when the
 * engine has none, rather than a placeholder that could be mistaken for one.
 *
 * Confirming only changes a label. The chat works either way: this tells you whether to trust
 * the line, it does not hold back help while you decide.
 */
@Composable
internal fun SosChatScreen(
    title: String,
    trust: SosTrustState?,
    shortAuthString: String?,
    lines: List<SosChatLine>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onMarkVerified: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val verified = trust == SosTrustState.NEARBY_DEVICE_VERIFIED
    Column(
        modifier
            .fillMaxSize()
            .background(Ink.night)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopBar(title = title, onBack = onBack)
        Text(
            if (verified) "Checked in person" else trust?.label ?: "Connected · not yet checked",
            color = if (verified) Ink.gold else Ink.muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        if (!verified && shortAuthString != null) {
            SasBlock(code = shortAuthString, onMarkVerified = onMarkVerified)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lines) { line -> ChatBubble(line) }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Ink.surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    textStyle = TextStyle(color = Ink.text, fontSize = 17.sp),
                    cursorBrush = SolidColor(Ink.sosLight),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) Text("Say what you need", color = Ink.quiet, fontSize = 17.sp)
                        inner()
                    }
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (draft.isBlank()) Ink.surfaceRaised else Ink.sos)
                    .clickable(enabled = draft.isNotBlank(), onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send",
                    tint = if (draft.isBlank()) Ink.quiet else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun SasBlock(code: String, onMarkVerified: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Ink.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Say this out loud. It must match on both phones.",
            color = Ink.body,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            spaced(code),
            color = Ink.text,
            fontSize = 30.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onMarkVerified) {
            Text("CODES MATCH IN PERSON", color = Ink.gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 482917 reads better as 482 917; the digits and their order are untouched. */
private fun spaced(code: String): String =
    if (code.length == 6) code.substring(0, 3) + "  " + code.substring(3) else code

@Composable
private fun ChatBubble(line: SosChatLine) {
    val mine = line.fromMe
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (mine) Ink.sosSurface else Ink.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(line.text, color = Ink.text, fontSize = 16.sp, lineHeight = 22.sp)
        }
    }
}

/**
 * Someone nearby is asking for help. One card per offer, each carrying its own id — Accept and
 * Decline are bound to the offer they are drawn from, so two calls arriving together cannot
 * answer each other's.
 */
@Composable
internal fun IncomingSosScreen(
    offers: List<IncomingSos>,
    onAccept: (IncomingSos) -> Unit,
    onDecline: (IncomingSos) -> Unit,
    onOpen: (IncomingSos) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Ink.night)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopBar(title = "Help needed nearby", onBack = onBack)
        if (offers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No one nearby is asking for help.", color = Ink.quiet, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(offers, key = { it.sosIdHex }) { offer ->
                    OfferCard(offer, onAccept = { onAccept(offer) }, onDecline = { onDecline(offer) }, onOpen = { onOpen(offer) })
                }
            }
        }
    }
}

@Composable
private fun OfferCard(
    offer: IncomingSos,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpen: () -> Unit,
) {
    val active = offer.phase == IncomingSos.Phase.ACTIVE
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Ink.surface)
            .clickable(enabled = active, onClick = onOpen)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(Ink.sos))
            Spacer(Modifier.width(10.dp))
            Text(offer.category.label, color = Ink.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${offer.ageMs / 1000}s ago", color = Ink.quiet, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            listOfNotNull(offer.connection.label, offer.proximity?.name?.lowercase()?.replaceFirstChar { it.uppercase() })
                .joinToString(" · "),
            color = Ink.muted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(offer.trust.label, color = Ink.quiet, fontSize = 13.sp)

        when (offer.phase) {
            IncomingSos.Phase.OFFERED -> {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryPill(
                        label = "Accept",
                        color = Ink.sos,
                        contentColor = Color.White,
                        onClick = onAccept,
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryPill(
                        label = "Decline",
                        color = Ink.surfaceRaised,
                        contentColor = Ink.body,
                        onClick = onDecline,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            IncomingSos.Phase.ACCEPTED_WAITING ->
                Meta("Waiting for them to connect…", Ink.muted)

            IncomingSos.Phase.ACTIVE ->
                Meta("Open the conversation", Ink.gold)

            IncomingSos.Phase.DECLINED ->
                Meta("You declined", Ink.quiet)

            IncomingSos.Phase.TAKEN_BY_OTHER ->
                Meta("Someone else is helping", Ink.quiet)

            IncomingSos.Phase.RELAYED_NOTICE ->
                Meta("Relayed from further away — you cannot answer this one directly", Ink.quiet)

            IncomingSos.Phase.ENDED ->
                Meta("Ended", Ink.quiet)
        }
    }
}

@Composable
private fun Meta(text: String, color: Color) {
    Text(text, color = color, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
}
