package com.chmod777.itantra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.Executors

/**
 * Debug-only TTS benchmark hook. Runs sherpa-onnx `generate` (no playback) on a VITS model
 * directory that was pushed to the device, so candidate models and runtime settings can be
 * timed with the app's exact native runtime without rebuilding the APK:
 *
 *   adb shell am broadcast -n com.chmod777.itantra/.DebugTtsBenchReceiver \
 *     --es dir <abs model dir> --es model model.onnx --ei threads 4 --es provider cpu \
 *     --es tag <label> --es text_b64 <b64> [--ei repeat 1] [--es data_dir <espeak dir>] [--es wav <name>]
 *     [--ef noise_scale 0.667 --ef noise_scale_w 0.8]  (0/0 makes VITS output deterministic for A/B comparison)
 *     [--ef silence_scale 0.2]  (1 disables sherpa-onnx's amplitude-dependent pause shortening)
 *
 * `provider` is passed through verbatim, so `cpu:<abs config file>` enables sherpa-onnx's
 * ORT session config file (profiling, SessionConfig.* entries). `--es action release`
 * frees the cached engine. Each call logs `bench done` under ITANTRA_BENCH when finished.
 */
class DebugTtsBenchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        val extras = intent.extras
        executor.execute {
            try {
                if (extras?.getString("action") == "release") {
                    release()
                } else if (extras != null) {
                    run(app, Args(
                        dir = extras.getString("dir") ?: error("dir missing"),
                        model = extras.getString("model") ?: "model.onnx",
                        tokens = extras.getString("tokens") ?: "tokens.txt",
                        dataDir = extras.getString("data_dir") ?: "",
                        threads = extras.getInt("threads", 4),
                        provider = extras.getString("provider") ?: "cpu",
                        tag = extras.getString("tag") ?: "bench",
                        text = String(Base64.decode(extras.getString("text_b64") ?: "", Base64.DEFAULT), Charsets.UTF_8),
                        repeat = extras.getInt("repeat", 1),
                        wav = extras.getString("wav"),
                        noiseScale = extras.getFloat("noise_scale", 0.667f),
                        noiseScaleW = extras.getFloat("noise_scale_w", 0.8f),
                        silenceScale = extras.getFloat("silence_scale", 0.2f)
                    ))
                }
            } catch (e: Throwable) {
                Log.e(TAG, "bench failed", e)
            } finally {
                Log.d(TAG, "bench done")
                pending.finish()
            }
        }
    }

    private data class Args(
        val dir: String, val model: String, val tokens: String, val dataDir: String, val threads: Int,
        val provider: String, val tag: String, val text: String, val repeat: Int, val wav: String?,
        val noiseScale: Float, val noiseScaleW: Float, val silenceScale: Float
    )

    private fun run(context: Context, a: Args) {
        val key = listOf(a.dir, a.model, a.tokens, a.dataDir, a.threads.toString(), a.provider, a.noiseScale.toString(), a.noiseScaleW.toString()).joinToString("|")
        if (key != engineKey) {
            release()
            val t0 = SystemClock.elapsedRealtime()
            engine = OfflineTts(config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(a.dir, a.model).absolutePath,
                        tokens = File(a.dir, a.tokens).absolutePath,
                        dataDir = a.dataDir,
                        noiseScale = a.noiseScale,
                        noiseScaleW = a.noiseScaleW
                    ),
                    numThreads = a.threads,
                    debug = false,
                    provider = a.provider
                )
            ))
            engineKey = key
            Log.d(TAG, "bench init tag=${a.tag} threads=${a.threads} provider=${a.provider} ms=${SystemClock.elapsedRealtime() - t0}")
        }
        val tts = engine ?: return
        for (i in 0 until a.repeat) {
            val t0 = SystemClock.elapsedRealtime()
            val audio = tts.generateWithConfig(a.text, GenerationConfig(silenceScale = a.silenceScale, speed = 1.0f, sid = 0))
            val ms = SystemClock.elapsedRealtime() - t0
            Log.d(TAG, "bench gen tag=${a.tag} cp=${a.text.codePointCount(0, a.text.length)} samples=${audio.samples.size} rate=${audio.sampleRate} ms=$ms")
            if (a.wav != null && i == 0) {
                val out = File(context.filesDir, "bench-out/${a.wav}.wav")
                out.parentFile?.mkdirs()
                writeWav(out, audio.samples, audio.sampleRate)
            }
        }
    }

    private fun release() {
        engine?.release()
        engine = null
        engineKey = null
    }

    private fun writeWav(file: File, samples: FloatArray, rate: Int) {
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(0)
            val data = samples.size * 2
            fun le32(v: Int) = f.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun le16(v: Int) = f.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            f.writeBytes("RIFF"); le32(36 + data); f.writeBytes("WAVEfmt ")
            le32(16); le16(1); le16(1); le32(rate); le32(rate * 2); le16(2); le16(16)
            f.writeBytes("data"); le32(data)
            val buf = ByteArray(data)
            samples.forEachIndexed { i, s ->
                val v = (s.coerceIn(-1f, 1f) * 32767).toInt()
                buf[2 * i] = v.toByte(); buf[2 * i + 1] = (v shr 8).toByte()
            }
            f.write(buf)
        }
    }

    private companion object {
        const val TAG = "ITANTRA_BENCH"
        val executor = Executors.newSingleThreadExecutor()
        var engine: OfflineTts? = null
        var engineKey: String? = null
    }
}
