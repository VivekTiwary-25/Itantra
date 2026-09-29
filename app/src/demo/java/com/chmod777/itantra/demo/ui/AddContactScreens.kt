package com.chmod777.itantra.demo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Demo "Add trusted contact": any QR code advances to a name form. No payload,
 * keys or fingerprints are shown or checked in the film build. If the camera is
 * unavailable, a 1 s long-press on the viewfinder is a hidden stand-in scan.
 */
@Composable
fun QrScanScreen(onBack: () -> Unit, onScanned: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    var done by remember { mutableStateOf(false) }
    val scanned = {
        if (!done) {
            done = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onScanned()
        }
    }

    Box(Modifier.fillMaxSize().background(Ink.deep)) {
        if (granted) {
            CameraPreview(onDecoded = scanned)
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
            Viewfinder(
                Modifier
                    .size(250.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            if (withTimeoutOrNull(1000) { tryAwaitRelease() } == null) scanned()
                        })
                    }
            )
            Spacer(Modifier.height(28.dp))
            Text(
                if (granted || !asked) "Scan their iTantra code" else "Camera access is needed to scan",
                color = Ink.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Hold the phone steady over the code",
                color = Ink.muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            if (!granted && asked) {
                TextButton(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow camera", color = Ink.gold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.weight(1.3f))
        }
    }
}

@Composable
private fun CameraPreview(onDecoded: () -> Unit) {
    val context = LocalContext.current
    val decoded by rememberUpdatedState(onDecoded)
    val barcodeView = remember {
        BarcodeView(context).apply {
            decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            decodeSingle { result -> if (!result.text.isNullOrEmpty()) decoded() }
        }
    }
    val lifecycle = (context as ComponentActivity).lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> barcodeView.resume()
                Lifecycle.Event.ON_PAUSE -> barcodeView.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
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

@Composable
fun AddContactFormScreen(onBack: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(300)
        focus.requestFocus()
        keyboard?.show()
    }
    val save = { if (name.isNotBlank()) { keyboard?.hide(); onSave(name.trim()) } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.night)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopBar(title = "Add trusted contact", onBack = onBack)
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Ink.gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Code scanned", color = Ink.muted, fontSize = 15.sp)
            }
            Spacer(Modifier.height(28.dp))
            Text("Name", color = Ink.muted, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(color = Ink.text, fontSize = 20.sp),
                cursorBrush = SolidColor(Ink.gold),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink.surface)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .focusRequester(focus),
            )
        }
        PrimaryPill(
            label = "Save",
            enabled = name.isNotBlank(),
            onClick = save,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )
    }
}
