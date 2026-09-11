package speech

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineDolphinModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

class SpeechRecognizerManager(private val context: Context) {

    // Languages served by the Dolphin multilingual CTC model. English stays on Whisper tiny.en.
    private val dolphinLanguages = setOf("hi", "gu", "mr", "ta", "te", "or", "bn")

    private var currentModelKey = ""
    private var recognizer: OfflineRecognizer? = null

    private fun modelKeyFor(language: String): String = when {
        language == "en" -> "en"
        language in dolphinLanguages -> "dolphin"
        else -> "whisper-base"
    }

    private fun getRecognizer(language: String): OfflineRecognizer {
        val modelKey = modelKeyFor(language)

        // Reuse the currently loaded model
        if (recognizer != null && currentModelKey == modelKey) {
            return recognizer!!
        }

        // Release previous model before loading another
        recognizer?.release()
        recognizer = null

        val modelConfig = when (modelKey) {
            "en" -> OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = "tiny.en-encoder.int8.onnx",
                    decoder = "tiny.en-decoder.int8.onnx",
                    language = "en",
                    task = "transcribe"
                ),
                tokens = "tiny.en-tokens.txt",
                modelType = "whisper",
                numThreads = 2,
                provider = "cpu"
            )

            "dolphin" -> OfflineModelConfig(
                dolphin = OfflineDolphinModelConfig(
                    model = "dolphin-base-ctc-multi-lang-int8/model.int8.onnx"
                ),
                tokens = "dolphin-base-ctc-multi-lang-int8/tokens.txt",
                modelType = "dolphin",
                numThreads = 2,
                provider = "cpu"
            )

            else -> OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = "base-encoder.int8.onnx",
                    decoder = "base-decoder.int8.onnx",
                    language = "hi",
                    task = "transcribe"
                ),
                tokens = "base-tokens.txt",
                modelType = "whisper",
                numThreads = 2,
                provider = "cpu"
            )
        }

        // PERF-INSTRUMENTATION (temporary): model construction cost, first load vs reuse.
        val loadStartMs = SystemClock.elapsedRealtime()
        recognizer = OfflineRecognizer(
            assetManager = context.assets,
            config = OfflineRecognizerConfig(modelConfig = modelConfig)
        )
        Log.d(PERF_TAG, "model load key=$modelKey deltaMs=${SystemClock.elapsedRealtime() - loadStartMs}")

        currentModelKey = modelKey

        return recognizer!!
    }

    fun recognizeAudio(
        samples: FloatArray,
        sampleRate: Int,
        language: String
    ): String {

        // PERF-INSTRUMENTATION (temporary): splits model-load cost from decode cost and
        // records audio duration so real-time factor can be computed from logcat alone.
        val audioMs = if (sampleRate > 0) samples.size * 1000L / sampleRate else 0L
        val getStartMs = SystemClock.elapsedRealtime()
        val currentRecognizer = getRecognizer(language)
        val loadMs = SystemClock.elapsedRealtime() - getStartMs
        val stream = currentRecognizer.createStream()

        try {
            val decodeStartMs = SystemClock.elapsedRealtime()
            stream.acceptWaveform(
                samples,
                sampleRate = sampleRate
            )

            currentRecognizer.decode(stream)

            val text = currentRecognizer.getResult(stream).text
            val decodeMs = SystemClock.elapsedRealtime() - decodeStartMs
            Log.d(
                PERF_TAG,
                "decode lang=$language audioMs=$audioMs loadMs=$loadMs decodeMs=$decodeMs " +
                    "rtf=${if (audioMs > 0) decodeMs.toDouble() / audioMs else -1.0} textLen=${text.length}"
            )
            return text

        } finally {
            stream.release()
        }
    }

    fun release() {
        recognizer?.release()
        recognizer = null
        currentModelKey = ""
    }

    private companion object {
        // PERF-INSTRUMENTATION (temporary): remove with the perf-measurement pass.
        const val PERF_TAG = "ITANTRA_PERF_STT"
    }
}