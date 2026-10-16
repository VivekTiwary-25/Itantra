package com.chmod777.itantra.ui.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.identity.TrustedContact
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * "You": the three things that only existed in the Net Lab — this phone's code, its name and
 * its fingerprint — plus the trusted contacts and, in a debug build, the way into the Net Lab.
 */
@Composable
internal fun SettingsScreen(
    selfName: String,
    selfFingerprint: String,
    contactCode: String,
    contacts: List<TrustedContact>,
    availableToHelp: Boolean,
    onAvailableChange: (Boolean) -> Unit,
    onRenameSelf: (String) -> Unit,
    onAddContact: () -> Unit,
    onRenameContact: (TrustedContact, String) -> Unit,
    onRemoveContact: (TrustedContact) -> Unit,
    onBack: () -> Unit,
    onReportProblem: () -> Unit,
    onShowIntro: () -> Unit,
    /** Null in a release build, where there is nothing to open. */
    onDeveloper: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var name by rememberSaveable(selfName) { mutableStateOf(selfName) }
    var renaming by remember { mutableStateOf<TrustedContact?>(null) }
    var removing by remember { mutableStateOf<TrustedContact?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .background(Ink.night)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopBar(title = "You", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            QrBlock(contactCode)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { copyToClipboard(context, contactCode) }) {
                    Text("COPY CODE", color = Ink.gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("Your name")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Ink.surface)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.take(32) },
                        singleLine = true,
                        textStyle = TextStyle(color = Ink.text, fontSize = 18.sp),
                        cursorBrush = SolidColor(Ink.gold),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = { onRenameSelf(name) }, enabled = name.isNotBlank() && name != selfName) {
                    Text("SAVE", color = if (name.isNotBlank() && name != selfName) Ink.gold else Ink.quiet, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("This is what your contacts see.", color = Ink.muted, fontSize = 13.sp)

            Spacer(Modifier.height(18.dp))
            SectionLabel("Your fingerprint")
            Text(
                selfFingerprint,
                color = Ink.body,
                fontSize = 15.sp,
                lineHeight = 24.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "Read this out when someone adds you, so they can check it matches.",
                color = Ink.muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink.surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Available to help nearby users", color = Ink.text, fontSize = 16.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Lets a stranger's SOS reach this phone. Uses more battery.",
                        color = Ink.muted,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = availableToHelp,
                    onCheckedChange = onAvailableChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink.onGold,
                        checkedTrackColor = Ink.gold,
                        uncheckedThumbColor = Ink.muted,
                        uncheckedTrackColor = Ink.inset,
                    )
                )
            }

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Contacts", color = Ink.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onAddContact) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Ink.gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ADD", color = Ink.gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (contacts.isEmpty()) {
                Text(
                    "Nobody yet. You add a contact by scanning their code face to face — that is " +
                        "the only way a message can reach them.",
                    color = Ink.muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                contacts.forEach { contact ->
                    ContactRow(
                        contact = contact,
                        onRename = { renaming = contact },
                        onRemove = { removing = contact },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
            // The "prove it" card is the demo: a judge should be invited to switch the radios
            // off and watch it keep working, which means it has to be replayable.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShowIntro)
                    .padding(vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("How this works", color = Ink.text, fontSize = 16.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("The four cards from the first run.", color = Ink.muted, fontSize = 13.sp)
                }
                Text("\u2192", color = Ink.quiet, fontSize = 16.sp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReportProblem)
                    .padding(vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Report a problem", color = Ink.text, fontSize = 16.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Or shake the phone. Nothing is sent by the app.", color = Ink.muted, fontSize = 13.sp)
                }
                Text("\u2192", color = Ink.quiet, fontSize = 16.sp)
            }

            if (onDeveloper != null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDeveloper)
                        .padding(vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Developer", color = Ink.muted, fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Text("→", color = Ink.quiet, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    renaming?.let { contact ->
        var newName by remember(contact) { mutableStateOf(contact.localName) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = Ink.surfaceRaised,
            title = { Text("Rename contact", color = Ink.text) },
            text = {
                BasicTextField(
                    value = newName,
                    onValueChange = { newName = it.take(32) },
                    singleLine = true,
                    textStyle = TextStyle(color = Ink.text, fontSize = 18.sp),
                    cursorBrush = SolidColor(Ink.gold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.inset)
                        .padding(14.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { onRenameContact(contact, newName); renaming = null }) {
                    Text("Rename", color = Ink.gold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("Cancel", color = Ink.text) }
            }
        )
    }

    removing?.let { contact ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = Ink.surfaceRaised,
            title = { Text("Remove ${contact.localName}?", color = Ink.text) },
            text = {
                Text(
                    "You will not be able to message them, and messages already queued for them " +
                        "will never be delivered. Adding them again means meeting in person and " +
                        "comparing fingerprints a second time.",
                    color = Ink.body,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { onRemoveContact(contact); removing = null }) {
                    Text("Remove", color = Ink.sosLight)
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text("Keep", color = Ink.text) }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Ink.muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun QrBlock(code: String) {
    val bitmap = remember(code) { if (code.isBlank()) null else qrBitmap(code) }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Ink.surface)
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            Text("No identity yet", color = Ink.quiet, fontSize = 15.sp)
        } else {
            Image(bitmap.asImageBitmap(), contentDescription = "This phone's iTantra code", Modifier.size(230.dp))
        }
    }
}

@Composable
private fun ContactRow(contact: TrustedContact, onRename: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(contact.localName, color = Ink.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                contact.fingerprint.take(9),
                color = Ink.quiet,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Box {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "More",
                tint = Ink.muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { menuOpen = true }
                    .padding(8.dp)
                    .size(22.dp)
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = Ink.surfaceRaised
            ) {
                DropdownMenuItem(
                    text = { Text("Rename", color = Ink.text) },
                    onClick = { menuOpen = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Remove", color = Ink.sosLight) },
                    onClick = { menuOpen = false; onRemove() }
                )
            }
        }
    }
}

/** Same writer the Net Lab uses, so both screens show the same code in the same shape. */
private fun qrBitmap(text: String, size: Int = 600): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) {
        bitmap.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
    }
    return bitmap
}

private fun copyToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("iTantra contact code", code))
}
