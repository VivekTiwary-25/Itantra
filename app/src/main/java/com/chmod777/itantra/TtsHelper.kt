package com.chmod777.itantra

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/**
 * Offline Piper/VITS text-to-speech over sherpa-onnx.
 *
 * `speak` blocks for the whole load-synthesise-play cycle, so every caller must
 * already be off the main thread; the receive path dispatches it explicitly.
 * Calls are serialised because they share one AudioTrack and the native engines.
 */
class TtsHelper(private val context: Context) {

    // Piper voices phonemise through espeak-ng-data; MMS voices use sherpa-onnx's character
    // frontend and ship no espeak data, so they may need `normalize` to keep text in-vocabulary.
    private data class Voice(
        val modelDir: String,
        val onnxFileName: String,
        val usesEspeak: Boolean = true,
        val numThreads: Int = 2,
        val normalize: ((String) -> String)? = null
    )

    private val voices = mapOf(
        "en" to Voice("vits-piper-en_US-ryan-medium", "en_US-ryan-medium.onnx"),
        "hi" to Voice("vits-piper-hi_IN-pratham-medium", "hi_IN-pratham-medium.onnx"),
        "ml" to Voice("vits-piper-ml_IN-arjun-medium", "ml_IN-arjun-medium.onnx"),
        // model.onnx is the MMS export with its HiFi-GAN decoder in fp16 (tts-research/mms-ben/speed).
        "bn" to Voice(
            "vits-mms-ben", "model.onnx", usesEspeak = false, numThreads = 4,
            normalize = BengaliTextNormalizer::normalize
        ),
        // model.onnx is the MMS `tel` export with its HiFi-GAN decoder in fp16, same recipe as Bengali
        // (tts-research/mms-tel/MMS_TEL_CONVERSION.md). Thread count carried over from Bengali, unverified
        // on-device for Telugu; the foreman benchmarks and may retune it.
        "te" to Voice(
            "vits-mms-tel", "model.onnx", usesEspeak = false, numThreads = 4,
            normalize = TeluguTextNormalizer::normalize
        )
    )

    private val lock = Any()
    private val engines = HashMap<String, OfflineTts>()
    private var currentAudioTrack: AudioTrack? = null

    // ---- Public API: exactly the contract from Part 3 of the lane doc ----

    /** Language codes with a real, verified voice behind them. */
    fun isSupported(languageCode: String): Boolean = voices.containsKey(languageCode)

    fun speak(text: String, languageCode: String) {
        // Never log the message body itself; length is enough to debug routing.
        Log.d(TAG, "speak lang=$languageCode textLen=${text.length}")

        if (text.isBlank()) {
            Log.d(TAG, "nothing to speak: blank text")
            return
        }

        val voice = voices[languageCode]
        if (voice == null) {
            Log.w(TAG, "no verified TTS voice for '$languageCode'; text is delivered but not spoken")
            return
        }

        synchronized(lock) {
            val engine = try {
                getOrInit(languageCode, voice)
            } catch (e: Throwable) {
                Log.e(TAG, "model init failed for '$languageCode'", e)
                return
            }

            try {
                val spoken = voice.normalize?.invoke(text) ?: text
                if (spoken != text) Log.d(TAG, "normalised lang=$languageCode textLen=${text.length}->${spoken.length}")
                val startMs = SystemClock.elapsedRealtime()
                Log.d(TAG, "synthesis start lang=$languageCode")
                val audio = engine.generate(text = spoken, sid = 0, speed = 1.0f)
                Log.d(
                    TAG,
                    "synthesis done lang=$languageCode samples=${audio.samples.size} " +
                        "rate=${audio.sampleRate} deltaMs=${SystemClock.elapsedRealtime() - startMs}"
                )
                playAudio(audio.samples, audio.sampleRate)
            } catch (e: Throwable) {
                Log.e(TAG, "synthesis/playback failed for '$languageCode'", e)
            }
        }
    }

    /** Releases native engines and any audio resources. Safe to call repeatedly. */
    fun release() {
        synchronized(lock) {
            stopCurrentAudioTrack()
            for ((code, engine) in engines) {
                try {
                    engine.release()
                } catch (e: Throwable) {
                    Log.w(TAG, "releasing '$code' engine failed", e)
                }
            }
            engines.clear()
        }
    }

    // ---- Internal implementation below ----

    private fun getOrInit(languageCode: String, voice: Voice): OfflineTts {
        engines[languageCode]?.let { return it }

        val destDir = File(context.filesDir, voice.modelDir)
        val modelPath = File(destDir, voice.onnxFileName).absolutePath
        val tokensPath = File(destDir, "tokens.txt")
        val dataDir = if (voice.usesEspeak) File(destDir, "espeak-ng-data").absolutePath else ""

        installAssets(voice.modelDir, destDir)
        normaliseTokens(tokensPath)

        Log.d(TAG, "initialising '$languageCode' model=$modelPath")
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelPath,
                    tokens = tokensPath.absolutePath,
                    dataDir = dataDir
                ),
                numThreads = voice.numThreads,
                debug = false
            )
        )
        val engine = OfflineTts(config = config)
        engines[languageCode] = engine
        Log.d(TAG, "initialised '$languageCode' sampleRate=${engine.sampleRate()}")
        return engine
    }

    /**
     * Copies the model out of assets on first use. A marker file records that the
     * previous copy finished, so an interrupted copy is redone instead of being
     * reused as a half-populated model directory. The marker also names the APK
     * build it came from: an updated APK may ship a different model under the same
     * file name, and reusing the old copy silently ran a stale model.
     */
    private fun installAssets(assetDir: String, destDir: File) {
        val marker = File(destDir, INSTALL_MARKER)
        val stamp = "$assetDir@${apkUpdateTime()}"
        if (marker.exists() && marker.readText() == stamp) return

        if (destDir.exists() && !destDir.deleteRecursively()) {
            throw IllegalStateException("could not clear stale model dir ${destDir.absolutePath}")
        }
        Log.d(TAG, "installing assets '$assetDir' -> ${destDir.absolutePath}")
        val startMs = SystemClock.elapsedRealtime()
        copyAssetFolder(context.assets, assetDir, destDir.absolutePath)
        marker.writeText(stamp)
        Log.d(TAG, "installed '$assetDir' deltaMs=${SystemClock.elapsedRealtime() - startMs}")
    }

    private fun apkUpdateTime(): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime

    /**
     * sherpa-onnx's piper token reader calls exit(-1) — killing the process with no
     * catchable exception — when a CRLF tokens.txt makes it parse the space token as
     * a duplicate. `.gitattributes` keeps the checked-in assets byte-exact; this is
     * the runtime backstop for an APK built from a badly-converted tree.
     */
    private fun normaliseTokens(tokens: File) {
        if (!tokens.exists()) throw IllegalStateException("missing tokens.txt at ${tokens.absolutePath}")
        val bytes = tokens.readBytes()
        if (!bytes.contains(CR)) return
        Log.w(TAG, "tokens.txt at ${tokens.absolutePath} had CR bytes; stripping them")
        tokens.writeBytes(bytes.filter { it != CR }.toByteArray())
    }

    private fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toPath: String) {
        val files = assetManager.list(fromAssetPath) ?: return
        File(toPath).mkdirs()
        for (fileName in files) {
            val subFiles = assetManager.list("$fromAssetPath/$fileName")
            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssetFolder(assetManager, "$fromAssetPath/$fileName", "$toPath/$fileName")
            } else {
                assetManager.open("$fromAssetPath/$fileName").use { inputStream ->
                    File("$toPath/$fileName").outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }
    }

    private fun playAudio(samples: FloatArray, sampleRate: Int) {
        stopCurrentAudioTrack()

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferSizeInBytes = maxOf(minBufferSize, samples.size * BYTES_PER_FLOAT)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeInBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        currentAudioTrack = audioTrack
        try {
            Log.d(TAG, "playback start samples=${samples.size} rate=$sampleRate")
            audioTrack.play()
            audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            awaitDrain(audioTrack, samples.size, sampleRate)
            Log.d(TAG, "playback end")
        } finally {
            stopCurrentAudioTrack()
        }
    }

    /**
     * A blocking write only guarantees the samples were queued, so wait for the
     * playback head to reach them before tearing the track down. The timeout keeps
     * a stalled audio device from pinning this thread.
     */
    private fun awaitDrain(audioTrack: AudioTrack, totalFrames: Int, sampleRate: Int) {
        val expectedMs = totalFrames * 1000L / sampleRate
        val deadline = SystemClock.elapsedRealtime() + expectedMs + DRAIN_GRACE_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (audioTrack.playbackHeadPosition >= totalFrames) return
            Thread.sleep(DRAIN_POLL_MS)
        }
        Log.w(TAG, "playback did not drain within ${expectedMs + DRAIN_GRACE_MS}ms")
    }

    private fun stopCurrentAudioTrack() {
        val track = currentAudioTrack ?: return
        currentAudioTrack = null
        try {
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.pause()
                track.flush()
                track.stop()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "stopping AudioTrack failed", e)
        }
        try {
            track.release()
        } catch (e: Throwable) {
            Log.w(TAG, "releasing AudioTrack failed", e)
        }
    }

    private companion object {
        const val TAG = "ITANTRA_TTS"
        const val INSTALL_MARKER = ".install-complete"
        const val BYTES_PER_FLOAT = 4
        const val DRAIN_GRACE_MS = 750L
        const val DRAIN_POLL_MS = 20L
        const val CR = '\r'.code.toByte()
    }
}
