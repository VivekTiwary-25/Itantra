package com.chmod777.itantra.ui.app

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmod777.itantra.service.NetworkingRuntime
import java.io.File
import kotlin.math.sqrt

/**
 * Everything a problem report may contain, assembled where it can be read before it is sent.
 *
 * What is deliberately absent: message text, transcripts, contact names, node ids, keys and
 * fingerprints. The log lines come from the metrics JSONL, whose own contract already forbids
 * callers putting plaintext, names or keys in it, so the exclusion holds by construction rather
 * than by filtering afterwards.
 */
internal data class ProblemReport(
    val appVersion: String,
    val device: String,
    val android: String,
    val language: String,
    val logLines: List<String>,
) {
    /** What the share sheet carries. The description goes first: it is the part a human reads. */
    fun asText(description: String): String = buildString {
        appendLine("iTantra problem report")
        appendLine()
        appendLine("What went wrong:")
        appendLine(description.ifBlank { "(not described)" })
        appendLine()
        appendLine("App: $appVersion")
        appendLine("Phone: $device")
        appendLine("Android: $android")
        appendLine("Language: $language")
        appendLine()
        appendLine("Recent app log (${logLines.size} lines):")
        logLines.forEach { appendLine(it) }
    }
}

private const val MAX_LOG_LINES = 80

/**
 * Reads the facts and the tail of the app's own event log. Nothing here looks at messages,
 * contacts or keys; the only file opened is the metrics JSONL this app wrote itself.
 */
internal fun collectProblemReport(context: Context, languageCode: String): ProblemReport {
    val packageInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()
    val version = buildString {
        append(packageInfo?.versionName ?: "unknown")
        append(" (")
        append(if (isDebuggable(context)) "debug" else "release")
        append(")")
    }
    return ProblemReport(
        appVersion = version,
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        language = uiLanguage(languageCode).englishName,
        logLines = recentLogLines(),
    )
}

private fun isDebuggable(context: Context): Boolean =
    (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

private fun recentLogLines(): List<String> = runCatching {
    val directory: File = NetworkingRuntime.benchDirectory
    val newest = directory.listFiles { file -> file.name.startsWith("events_") && file.name.endsWith(".jsonl") }
        ?.maxByOrNull { it.lastModified() }
        ?: return emptyList()
    newest.readLines().takeLast(MAX_LOG_LINES)
}.getOrDefault(emptyList())

/**
 * Two shakes inside a second, and at most one report a minute.
 *
 * [enabled] is false while a take is being recorded or decoded, and while an SOS is running.
 * Someone shaking a phone during an emergency is not filing a bug report, and a screen that
 * steals that moment is worse than no feature at all.
 */
@Composable
internal fun ShakeToReport(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val current by rememberUpdatedState(onShake)
    DisposableEffect(enabled, context) {
        if (!enabled) return@DisposableEffect onDispose { }
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensors == null || accelerometer == null) return@DisposableEffect onDispose { }

        var firstShakeAt = 0L
        var lastPromptAt = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val g = sqrt(
                    (event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]).toDouble()
                ) / SensorManager.GRAVITY_EARTH
                if (g < SHAKE_G) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastPromptAt < PROMPT_COOLDOWN_MS) return
                if (now - firstShakeAt in 1..SECOND_SHAKE_WINDOW_MS) {
                    firstShakeAt = 0L
                    lastPromptAt = now
                    current()
                } else {
                    firstShakeAt = now
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensors.unregisterListener(listener) }
    }
}

private const val SHAKE_G = 2.2f
private const val SECOND_SHAKE_WINDOW_MS = 1_000L
private const val PROMPT_COOLDOWN_MS = 60_000L

/**
 * Shows the whole report before any of it leaves the screen, and hands it to Android's share
 * sheet only when the user says so. There is no upload: the app has no network code for one,
 * which is the only promise worth making in an app whose point is working with the radios off.
 */
@Composable
internal fun ProblemReportScreen(
    report: ProblemReport,
    onShare: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var description by rememberSaveable { mutableStateOf("") }
    Column(
        modifier
            .fillMaxSize()
            .background(Ink.night)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopBar(title = "Report a problem", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                "Nothing is sent anywhere by this app. You choose where it goes, on the next " +
                    "screen.",
                color = Ink.body,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(18.dp))
            Label("What went wrong?")
            Panel {
                BasicTextField(
                    value = description,
                    onValueChange = { description = it.take(600) },
                    textStyle = TextStyle(color = Ink.text, fontSize = 17.sp, lineHeight = 24.sp),
                    cursorBrush = SolidColor(Ink.gold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (description.isEmpty()) {
                            Text(
                                "What were you doing, and what happened?",
                                color = Ink.quiet,
                                fontSize = 17.sp
                            )
                        }
                        inner()
                    }
                )
            }

            Spacer(Modifier.height(18.dp))
            Label("What goes in")
            Panel {
                Fact("App", report.appVersion)
                Fact("Phone", report.device)
                Fact("Android", report.android)
                Fact("Language", report.language)
                Fact("App log", "${report.logLines.size} lines")
            }

            Spacer(Modifier.height(18.dp))
            Label("What never goes in")
            Text(
                "Your messages, what you dictated, your contacts' names, and your keys.",
                color = Ink.muted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            if (report.logLines.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Label("The log, in full")
                Panel {
                    Text(
                        report.logLines.joinToString("\n"),
                        color = Ink.quiet,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        PrimaryPill(
            label = "Share report",
            onClick = { onShare(description) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        color = Ink.muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.surface)
            .padding(14.dp),
        content = content
    )
}

@Composable
private fun Fact(name: String, value: String) {
    Text("$name: $value", color = Ink.body, fontSize = 14.sp, lineHeight = 20.sp)
}

/** Plain text through the share sheet: no file, no provider, no permission, no network. */
internal fun shareProblemReport(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "iTantra problem report")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, "Share problem report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
