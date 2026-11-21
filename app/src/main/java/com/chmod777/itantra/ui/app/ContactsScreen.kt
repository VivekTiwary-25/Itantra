package com.chmod777.itantra.ui.app

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.identity.ContactQrCodec
import com.chmod777.itantra.identity.IdentityCapsuleV1
import com.chmod777.itantra.identity.TrustedContact
import com.chmod777.itantra.protocol.cbor.MalformedInputException
import com.chmod777.itantra.service.NetworkingRuntime
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Minimal working bridge for the real QR trust setup the private demo needs: show this
 * phone's signed identity QR, scan another phone's, compare fingerprints, trust. Full
 * contact management (rename, remove, details) is Coming soon.
 */
@Composable
fun ContactsScreen() {
    val store = AppStore
    BackHandler { store.back() }
    val identity by NetworkingRuntime.identity.collectAsState()
    var contacts by remember { mutableStateOf(NetworkingRuntime.database.all()) }
    var pending by remember { mutableStateOf<IdentityCapsuleV1?>(null) }
    var pendingName by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var myName by remember(identity) { mutableStateOf(identity?.displayNameHint ?: "") }
    var showQr by remember { mutableStateOf(false) }

    fun parse(text: String) {
        problem = try {
            val capsule = ContactQrCodec.decodeAndVerify(text)
            if (capsule.nodeId.contentEquals(identity?.nodeId)) {
                "That is this phone's own code."
            } else {
                pending = capsule
                pendingName = capsule.displayNameHint
                null
            }
        } catch (e: MalformedInputException) {
            "Not a valid iTantra contact code."
        }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let { parse(it) } }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        ScreenHeader("Trusted contacts", { store.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
            Text("Only people whose QR you have scanned can receive your private messages.", color = Ink.TextDim, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))

            // ---- This phone
            Text("This phone", color = Ink.TextMute, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(myName, { myName = it.take(32) }, "Your name, shown to people who scan you", Modifier.weight(1f))
                if (myName.trim() != (identity?.displayNameHint ?: "") && myName.isNotBlank()) {
                    QuietButton("Save", { NetworkingRuntime.renameSelf(myName.trim()) }, color = Ink.Signal)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Fingerprint  ${identity?.fingerprint ?: "…"}", color = Ink.TextMute, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(10.dp))
            QuietButton(if (showQr) "Hide my QR" else "Show my QR", { showQr = !showQr }, color = Ink.Text)
            if (showQr) {
                val qr = remember(identity) { identity?.let { qrBitmap(ContactQrCodec.encode(it.signedCapsule())) } }
                qr?.let {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(it.asImageBitmap(), "This phone's iTantra QR", Modifier.size(260.dp).clip(RoundedCornerShape(12.dp)))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            // ---- Add
            Text("Add someone", color = Ink.TextMute, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            if (pending == null) {
                ClayButton(
                    "Scan their QR",
                    {
                        problem = null
                        scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan an iTantra QR").setBeepEnabled(false))
                    },
                    Ink.ClayHi, Ink.Text, Modifier.fillMaxWidth(),
                )
            }
            problem?.let { Text(it, color = Ink.Urgent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            pending?.let { capsule ->
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Check this fingerprint matches the one on their phone", color = Ink.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(capsule.fingerprint, color = Ink.Signal, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                    Field(pendingName, { pendingName = it.take(32) }, "Name on this phone", Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ClayButton(
                            "It matches · Trust",
                            {
                                NetworkingRuntime.database.upsert(TrustedContact.fromVerifiedCapsule(capsule, pendingName, System.currentTimeMillis()))
                                contacts = NetworkingRuntime.database.all()
                                pending = null
                                store.logVersion++
                            },
                            Ink.Signal, Ink.SignalInk, Modifier.weight(1f),
                        )
                        QuietButton("Cancel", { pending = null })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            // ---- List
            if (contacts.isNotEmpty()) {
                Text("Trusted", color = Ink.TextMute, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                contacts.forEach { contact ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Ink.ClayHi), contentAlignment = Alignment.Center) {
                            Text(contact.localName.take(1).uppercase(), color = Ink.Text, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(contact.localName, color = Ink.Text, fontSize = 16.sp)
                            Text(contact.fingerprint, color = Ink.TextMute, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            QuietButton("Manage contacts", { store.go(Route.ComingSoon("Contacts")) }, color = Ink.TextMute)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Ink.Text, fontSize = 16.sp),
        cursorBrush = SolidColor(Ink.Signal),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Ink.ClayLo).padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, color = Ink.TextMute, fontSize = 14.sp)
                inner()
            }
        },
    )
}

private fun qrBitmap(text: String, size: Int = 600): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
