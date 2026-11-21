package com.chmod777.itantra.ui.app

import android.Manifest
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.network.ItantraNetwork
import com.chmod777.itantra.protocol.SosCategory
import com.chmod777.itantra.service.EmergencyState
import com.chmod777.itantra.sos.IncomingSos
import com.chmod777.itantra.sos.SosConnectionKind
import com.chmod777.itantra.transport.BluetoothPermissions

/**
 * Root of the demo product UI. Owns runtime permissions and starts the Bluetooth radio
 * (the Emergency-mode foreground service) once it is allowed: private messages wait in the
 * encrypted store without it, and SOS cannot reach anyone without it.
 */
@Composable
fun ProductApp() {
    val context = LocalContext.current
    val store = AppStore
    val radio by ItantraNetwork.emergencyState.collectAsState()
    var startRadioWhenGranted by remember { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val radioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (startRadioWhenGranted && BluetoothPermissions.areGranted(context)) ItantraNetwork.startEmergencyMode(context)
        startRadioWhenGranted = false
    }
    val radioPermissions = remember {
        val list = BluetoothPermissions.requiredRuntimePermissions().toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list += Manifest.permission.POST_NOTIFICATIONS
        list.toTypedArray()
    }
    val startRadio = {
        if (BluetoothPermissions.areGranted(context)) {
            ItantraNetwork.startEmergencyMode(context)
        } else {
            startRadioWhenGranted = true
            radioLauncher.launch(radioPermissions)
        }
    }
    val requestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    LaunchedEffect(Unit) {
        if (!store.hasMicPermission()) requestMic()
        if (radio == EmergencyState.Off) startRadio()
    }

    Box(Modifier.fillMaxSize().background(Ink.Bg)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            AnimatedContent(
                targetState = store.route,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(110)) },
                label = "route",
            ) { route ->
                when (route) {
                    Route.Home -> HomeScreen(onRequestMic = requestMic, onStartRadio = startRadio)
                    Route.HandsFree -> HandsFreeScreen(onRequestMic = requestMic)
                    Route.Composer -> ComposerScreen(onStartRadio = startRadio)
                    Route.Logs -> LogsScreen()
                    is Route.Detail -> MessageDetailScreen(route.bundleIdHex)
                    is Route.SosDetail -> SosDetailScreen(route.sosIdHex)
                    is Route.Conversation -> ConversationScreen(route.sosIdHex, onRequestMic = requestMic)
                    Route.Contacts -> ContactsScreen()
                    is Route.ComingSoon -> ComingSoonScreen(route.title, onHome = { store.home() }, onBack = { store.back() })
                }
            }
        }
        SosOfferSheet()
    }
}

// ---------------------------------------------------------------------------- Incoming SOS (responder)

/**
 * Temporary glass sheet above whatever is open when a nearby phone offers an SOS. Before
 * acceptance the protocol discloses only category, language and age (spec §34), so the
 * requester's words arrive over the encrypted chat once this phone accepts.
 */
@Composable
private fun SosOfferSheet() {
    val store = AppStore
    val view = LocalView.current
    // Keep showing the offer we surfaced until the user closes it, so a withdrawn or
    // taken request is explained instead of silently vanishing.
    var shownId by remember { mutableStateOf<String?>(null) }
    val offers = store.incomingSos
    val candidate = offers.firstOrNull {
        it.connection == SosConnectionKind.NEARBY_LIVE && it.phase == IncomingSos.Phase.OFFERED && it.sosIdHex !in store.dismissedOffers
    }?.sosIdHex
    LaunchedEffect(candidate, store.dismissedOffers.size) {
        if (shownId == null || shownId in store.dismissedOffers) shownId = candidate
    }
    val offer = offers.firstOrNull { it.sosIdHex == shownId }
    LaunchedEffect(offer?.sosIdHex) { if (offer != null) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    val close = {
        shownId?.let { store.dismissedOffers.add(it) }
        shownId = null
    }

    AnimatedVisibility(
        visible = offer != null && offer.phase != IncomingSos.Phase.ACTIVE,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        )
    }
    AnimatedVisibility(
        visible = offer != null && offer.phase != IncomingSos.Phase.ACTIVE,
        enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
        modifier = Modifier.fillMaxSize(),
    ) {
        val current = offer ?: return@AnimatedVisibility
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Ink.Sos.copy(alpha = 0.05f))
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(Ink.Sos)
                    Spacer(Modifier.width(10.dp))
                    Tag("SOS", Ink.Sos)
                }
                Text(
                    "Someone nearby needs help",
                    color = Ink.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(offerDetails(current), color = Ink.TextDim, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                when (current.phase) {
                    IncomingSos.Phase.OFFERED -> {
                        Text("You'll see their message once you accept.", color = Ink.TextMute, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        ClayButton("Accept", { store.acceptSos(current.sosIdHex) }, Ink.Sos, Ink.SosInk, Modifier.fillMaxWidth(), height = 60.dp)
                        QuietButton("Decline", { store.declineSos(current.sosIdHex); shownId = null }, Modifier.fillMaxWidth())
                    }
                    IncomingSos.Phase.ACCEPTED_WAITING -> {
                        ClayButton("Connecting…", {}, Ink.Sos, Ink.SosInk, Modifier.fillMaxWidth(), enabled = false, height = 60.dp)
                    }
                    IncomingSos.Phase.TAKEN_BY_OTHER -> {
                        Text("Someone else is already helping.", color = Ink.Text, fontSize = 15.sp)
                        QuietButton("Close", close, Modifier.fillMaxWidth())
                    }
                    else -> {
                        Text("This request has ended.", color = Ink.Text, fontSize = 15.sp)
                        QuietButton("Close", close, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private fun offerDetails(offer: IncomingSos): String {
    val parts = mutableListOf<String>()
    if (offer.category != SosCategory.OTHER) parts += offer.category.label
    parts += languageName(offer.language)
    val seconds = offer.ageMs / 1000
    parts += if (seconds < 60) "just now" else "${seconds / 60} min ago"
    return parts.joinToString(" · ")
}
