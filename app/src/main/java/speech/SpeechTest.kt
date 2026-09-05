package speech

import android.content.Context

class SpeechTest(context: Context) {

    private val speechRecognizerManager =
        SpeechRecognizerManager(context)

    fun testEnglish(
        samples: FloatArray,
        sampleRate: Int = 16000
    ): String {
        return speechRecognizerManager.recognizeAudio(
            samples = samples,
            sampleRate = sampleRate,
            language = "en"
        )
    }

    fun testHindi(
        samples: FloatArray,
        sampleRate: Int = 16000
    ): String {
        return speechRecognizerManager.recognizeAudio(
            samples = samples,
            sampleRate = sampleRate,
            language = "hi"
        )
    }

    fun testKannada(
        samples: FloatArray,
        sampleRate: Int = 16000
    ): String {
        return speechRecognizerManager.recognizeAudio(
            samples = samples,
            sampleRate = sampleRate,
            language = "kn"
        )
    }
}