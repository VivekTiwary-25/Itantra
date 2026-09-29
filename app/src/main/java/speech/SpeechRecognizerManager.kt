package speech

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

class SpeechRecognizerManager(private val context: Context) {

    // Languages served by IndicConformer (NeMo CTC). English stays on Whisper tiny.en.
    private val indicConformerLanguages = setOf("hi", "gu", "mr", "ta", "te", "or", "bn")

    private val indicConformerDir = File(context.filesDir, INDIC_CONFORMER_DIR_NAME)

    private var currentModelKey = ""
    private var recognizer: OfflineRecognizer? = null

    // IndicConformer wrappers are per language (each one is a thin graph over the shared encoder
    // weights), so switching language means a new recognizer; the same language reuses it.
    private fun modelKeyFor(language: String): String = when {
        language == "en" -> "en"
        language in indicConformerLanguages -> "ic-$language"
        else -> ""
    }

    private fun getRecognizer(language: String): OfflineRecognizer? {
        val modelKey = modelKeyFor(language)
        if (modelKey.isEmpty()) {
            Log.e(PERF_TAG, "no speech model for language=$language")
            return null
        }

        // Reuse the currently loaded model
        if (recognizer != null && currentModelKey == modelKey) {
            return recognizer!!
        }

        // Release previous model before loading another (two IndicConformer sessions would need ~2x RAM)
        recognizer?.release()
        recognizer = null
        currentModelKey = ""

        // English lives in the APK assets; IndicConformer is read from the app's private files folder
        // by absolute path (no assetManager), so the 650 MB weights never pass through the APK.
        var assetManager: android.content.res.AssetManager? = context.assets
        val modelConfig = if (modelKey == "en") {
            OfflineModelConfig(
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
        } else {
            assetManager = null
            val model = File(indicConformerDir, "$language.onnx")
            val tokens = File(indicConformerDir, "languages/$language/tokens.txt")
            val weights = File(indicConformerDir, "shared/encoder.weights.bin")
            val missing = listOf(model, tokens, weights).filterNot { it.isFile }
            if (missing.isNotEmpty()) {
                Log.e(PERF_TAG, "IndicConformer files missing for language=$language: $missing")
                return null
            }
            OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = model.absolutePath),
                tokens = tokens.absolutePath,
                numThreads = 2,
                provider = "cpu"
            )
        }

        // PERF-INSTRUMENTATION (temporary): model construction cost, first load vs reuse.
        val loadStartMs = SystemClock.elapsedRealtime()
        recognizer = OfflineRecognizer(
            assetManager = assetManager,
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
        // No model (unsupported language or IndicConformer files not pushed): empty text, the
        // editor still opens and the user can type.
        val currentRecognizer = getRecognizer(language) ?: return ""
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

    companion object {
        // PERF-INSTRUMENTATION (temporary): remove with the perf-measurement pass.
        private const val PERF_TAG = "ITANTRA_PERF_STT"

        // The ONE place that says where the IndicConformer package lives on the phone: a folder in
        // the app's private files dir holding shared/encoder.weights.bin, <lang>.onnx and
        // languages/<lang>/tokens.txt. Swapping in another build of the package needs no code change:
        // push the files here (docs/STT_INDICCONFORMER.md).
        const val INDIC_CONFORMER_DIR_NAME = "indicconformer"

    }
}
