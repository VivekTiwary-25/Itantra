package com.chmod777.itantra.ui.app

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Scan someone's code. The payload goes straight up to [onCode], which verifies it; nothing
 * here decides whether a code is trustworthy.
 */
@Composable
internal fun QrScanScreen(
    onBack: () -> Unit,
    onCode: (String) -> Unit,
    error: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    // One code per visit to this screen: a scanner that keeps firing while the next screen
    // animates in would push the same contact through the flow twice.
    var taken by remember { mutableStateOf(false) }
    val onDecoded: (String) -> Unit = { payload ->
        if (!taken) {
            taken = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onCode(payload)
        }
    }

    Box(modifier.fillMaxSize().background(Ink.deep)) {
        if (granted) {
            CameraPreview(onDecoded = onDecoded)
            // Keeps the header and caption legible over a bright camera scene.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Ink.deep.copy(alpha = 0.92f),
                            0.22f to Ink.deep.copy(alpha = 0.35f),
                            0.62f to Ink.deep.copy(alpha = 0.35f),
                            0.78f to Ink.deep.copy(alpha = 0.9f),
                        )
                    )
            )
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopBar(title = "Add trusted contact", onBack = onBack)
            Spacer(Modifier.weight(1f))
            Viewfinder(Modifier.size(250.dp))
            Spacer(Modifier.height(28.dp))
            Text(
                if (granted || !asked) "Scan their iTantra code" else "Camera access is needed to scan",
                color = Ink.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Hold the phone steady over the code on their Settings screen",
                color = Ink.muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            if (!granted && asked) {
                TextButton(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow camera", color = Ink.gold, fontSize = 16.sp)
                }
            }
            if (error != null) {
                Spacer(Modifier.height(20.dp))
                WarningStrip(error, Modifier.padding(horizontal = 20.dp))
            }
            Spacer(Modifier.weight(1.3f))
        }
    }
}

@Composable
private fun CameraPreview(onDecoded: (String) -> Unit) {
    val context = LocalContext.current
    val decoded by rememberUpdatedState(onDecoded)
    val barcodeView = remember {
        BarcodeView(context).apply {
            decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            decodeSingle { result -> result.text?.takeIf { it.isNotEmpty() }?.let(decoded) }
        }
    }
    val lifecycle = (context as? ComponentActivity)?.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> barcodeView.resume()
                Lifecycle.Event.ON_PAUSE -> barcodeView.pause()
                else -> Unit
            }
        }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            barcodeView.pause()
        }
    }
    AndroidView(factory = { barcodeView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun Viewfinder(modifier: Modifier) {
    Canvas(modifier) {
        val len = size.minDimension * 0.18f
        val w = 4.dp.toPx()
        val c = Ink.gold
        val max = size.width
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(c, Offset(x, y), Offset(x + dx * len, y), w, StrokeCap.Round)
            drawLine(c, Offset(x, y), Offset(x, y + dy * len), w, StrokeCap.Round)
        }
        corner(0f, 0f, 1f, 1f)
        corner(max, 0f, -1f, 1f)
        corner(0f, max, 1f, -1f)
        corner(max, max, -1f, -1f)
    }
}

/**
 * The step between scanning and saving. Nothing has been written yet and nothing will be unless
 * THESE MATCH is tapped.
 *
 * The whole fingerprint is shown, all eight groups. It is already a truncation — SHA-256 cut to
 * 16 bytes — and shortening it further is the one change on this screen that would genuinely
 * weaken it: forging a key that matches four groups is around 2^64 work, against 2^128 for
 * eight. It is two lines on screen either way.
 */
@Composable
internal fun FingerprintCheckScreen(
    fingerprint: String,
    onMatch: () -> Unit,
    onReject: () -> Unit,
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
        TopBar(title = "Check this is really them", onBack = onBack)
        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            // The name in a scanned code is chosen by whoever made it. Putting it here as
            // "Vivek's code shows" would lend a forged code the authority of a familiar name at
            // the exact moment the user is deciding whether to believe it. It appears on the
            // next screen instead, in an editable field labelled as what *you* call them.
            Text("Their code shows", color = Ink.muted, fontSize = 15.sp)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Ink.inset)
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                Text(
                    fingerprint,
                    color = Ink.text,
                    fontSize = 22.sp,
                    lineHeight = 34.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Ask them to open Settings and read their own fingerprint out loud. " +
                    "Every group must match.",
                color = Ink.body,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "If even one group is different, someone else is on the other end. Nothing is " +
                    "saved until you say they match.",
                color = Ink.muted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryPill(
            label = "These match",
            onClick = onMatch,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onReject) {
                Text("They don't match", color = Ink.sosLight, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** The last step: what to call them on this phone. Reached only after the codes were compared. */
@Composable
internal fun AddContactNameScreen(
    nameHint: String,
    fingerprint: String,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable(nameHint) { mutableStateOf(nameHint) }
    Column(
        modifier
            .fillMaxSize()
            .background(Ink.night)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopBar(title = "Name this contact", onBack = onBack)
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("What should this phone call them?", color = Ink.muted, fontSize = 15.sp)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Ink.surface)
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    singleLine = true,
                    textStyle = TextStyle(color = Ink.text, fontSize = 20.sp),
                    cursorBrush = SolidColor(Ink.gold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (name.isEmpty()) Text("Their name", color = Ink.quiet, fontSize = 20.sp)
                        inner()
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Checked fingerprint", color = Ink.muted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                fingerprint,
                color = Ink.quiet,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryPill(
            label = "Save contact",
            enabled = name.isNotBlank(),
            onClick = { onSave(name.trim()) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        )
    }
}

/** Warm-red inline note, same shape as the capture errors but without an action. */
@Composable
internal fun WarningStrip(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.sosSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, color = Ink.body, fontSize = 15.sp, lineHeight = 21.sp)
    }
}
