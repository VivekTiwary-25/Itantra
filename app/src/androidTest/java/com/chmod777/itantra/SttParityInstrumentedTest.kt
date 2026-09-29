package com.chmod777.itantra

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import speech.SpeechEngine
import java.io.File

/**
 * Phone-side STT parity/perf run (Task 5b). NOT a pass/fail test: it feeds WAVs from
 * <filesDir>/stt-parity/<lang>/ through the app's own SpeechEngine.transcribe() and writes
 * <filesDir>/stt-parity/results-<lang>.json. Run manually with `am instrument` (never
 * connectedAndroidTest, which uninstalls the app and deletes the pushed model files).
 *
 *   -e lang hi         language folder and language code
 *   -e prime en        optional: transcribe the first clip with this language first (measures a switch into [lang])
 *   -e passes 2        repeat the clip list (pass 2 = warm, same recognizer)
 *   -e limit 5         number of clips
 */
@RunWith(AndroidJUnit4::class)
class SttParityInstrumentedTest {
    @Test
    fun runClips() {
        val args = InstrumentationRegistry.getArguments()
        val lang = args.getString("lang") ?: "hi"
        val prime = args.getString("prime") ?: ""
        val passes = (args.getString("passes") ?: "2").toInt()
        val limit = (args.getString("limit") ?: "5").toInt()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.filesDir, "stt-parity/$lang")
        val clips = dir.listFiles { f -> f.extension == "wav" }!!.sortedBy { it.name }.take(limit)
        val engine = SpeechEngine(ctx)
        val out = JSONArray()
        Log.d(TAG, "start lang=$lang prime='$prime' passes=$passes clips=${clips.size} ${mem()}")
        try {
            if (prime.isNotEmpty()) {
                val t0 = System.nanoTime()
                engine.transcribe(clips.first().absolutePath, prime)
                Log.d(TAG, "prime lang=$prime totalMs=${(System.nanoTime() - t0) / 1_000_000} ${mem()}")
            }
            for (pass in 1..passes) {
                for (clip in clips) {
                    val t0 = System.nanoTime()
                    val text = engine.transcribe(clip.absolutePath, lang)
                    val ms = (System.nanoTime() - t0) / 1_000_000
                    Log.d(TAG, "pass=$pass clip=${clip.name} totalMs=$ms ${mem()}")
                    out.put(JSONObject().put("pass", pass).put("clip", clip.name).put("text", text).put("total_ms", ms))
                }
            }
        } finally {
            engine.release()
        }
        File(ctx.filesDir, "stt-parity/results-$lang.json").writeText(out.toString(1), Charsets.UTF_8)
        Log.d(TAG, "done lang=$lang ${mem()}")
    }

    private fun mem(): String {
        val fields = File("/proc/self/status").readLines()
            .filter { it.startsWith("VmHWM") || it.startsWith("VmRSS") }
            .joinToString(" ") { it.replace(Regex("\\s+"), "") }
        return fields
    }

    private companion object {
        const val TAG = "STTPARITY"
    }
}
