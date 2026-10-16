package com.chmod777.itantra.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What went wrong with the last attempt to capture speech, in the words the user sees.
 *
 * The screens never decide this: [AppState] sets it, so the orb on Home and the Hands-free
 * screen cannot disagree about whether the microphone works.
 */
internal enum class CaptureError(val message: String, val action: String?) {
    /** The permission is off and the system will not ask again from inside the app. */
    MIC_BLOCKED("iTantra cannot hear you. Microphone permission is off.", "OPEN SETTINGS"),

    /** The engine ran and came back with silence, or with one of Whisper's annotation tokens. */
    NOTHING_HEARD("Didn't catch anything. Try again.", null),

    /** The engine itself failed. Different from hearing nothing, because the fix is different. */
    TRANSCRIBE_FAILED("Could not turn that into text.", "TRY AGAIN"),
}

/**
 * A warm-red inline strip, never a system dialog: a rescue tool should not stack a modal over
 * the one button the user is trying to press.
 */
@Composable
internal fun CaptureErrorStrip(
    error: CaptureError,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.sosSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Ink.sos)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                error.message,
                color = Ink.body,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.weight(1f)
            )
        }
        if (error.action != null) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    error.action,
                    color = Ink.gold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
