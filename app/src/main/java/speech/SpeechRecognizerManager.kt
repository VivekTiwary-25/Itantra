package speech

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

class SpeechRecognizerManager(private val context: Context) {

    private var currentLanguage = ""
    private var recognizer: OfflineRecognizer? = null

    private fun getRecognizer(language: String): OfflineRecognizer {

        // Reuse the currently loaded model
        if (recognizer != null && currentLanguage == language) {
            return recognizer!!
        }

        // Release previous model before loading another
        recognizer?.release()
        recognizer = null

        val whisperConfig: OfflineWhisperModelConfig
        val tokens: String

        if (language == "en") {
            whisperConfig = OfflineWhisperModelConfig(
                encoder = "tiny.en-encoder.int8.onnx",
                decoder = "tiny.en-decoder.int8.onnx",
                language = "en",
                task = "transcribe"
            )

            tokens = "tiny.en-tokens.txt"

        } else {
            whisperConfig = OfflineWhisperModelConfig(
                encoder = "base-encoder.int8.onnx",
                decoder = "base-decoder.int8.onnx",
                language = "hi",
                task = "transcribe"
            )

            tokens = "base-tokens.txt"
        }

        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                whisper = whisperConfig,
                tokens = tokens,
                modelType = "whisper",
                numThreads = 2,
                provider = "cpu"
            )
        )

        recognizer = OfflineRecognizer(
            assetManager = context.assets,
            config = config
        )

        currentLanguage = language

        return recognizer!!
    }

    fun recognizeAudio(
        samples: FloatArray,
        sampleRate: Int,
        language: String
    ): String {

        val currentRecognizer = getRecognizer(language)
        val stream = currentRecognizer.createStream()

        try {
            stream.acceptWaveform(
                samples,
                sampleRate = sampleRate
            )

            currentRecognizer.decode(stream)

            return currentRecognizer.getResult(stream).text

        } finally {
            stream.release()
        }
    }

    fun release() {
        recognizer?.release()
        recognizer = null
        currentLanguage = ""
    }
}