package speech

import android.content.Context
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentification
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentificationConfig
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentificationWhisperConfig

class LanguageIdentifierManager(context: Context) {

    private val languageIdentifier: SpokenLanguageIdentification

    init {
        val config = SpokenLanguageIdentificationConfig(
            whisper = SpokenLanguageIdentificationWhisperConfig(
                encoder = "tiny-encoder.int8.onnx",
                decoder = "tiny-decoder.int8.onnx"
            ),
            numThreads = 1,
            provider = "cpu"
        )

        languageIdentifier = SpokenLanguageIdentification(
            assetManager = context.assets,
            config = config
        )
    }

    fun detectLanguage(
        samples: FloatArray,
        sampleRate: Int
    ): String {

        val stream = languageIdentifier.createStream()

        stream.acceptWaveform(
            samples,
            sampleRate
        )

        val language = languageIdentifier.compute(stream)

        stream.release()

        return language
    }
}