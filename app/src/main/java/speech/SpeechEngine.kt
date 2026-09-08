package speech


import android.content.Context
import com.chmod777.itantra.TtsHelper
import java.io.File
import java.io.RandomAccessFile

/**
 * S10 — Lane 1 talks to exactly these two functions.
 * No sherpa-onnx types, no model paths, no internals leak out.
 */
class SpeechEngine(context: Context) {

    private val recognizerManager = SpeechRecognizerManager(context)
    private val ttsHelper = TtsHelper(context)

    fun transcribe(wavFilePath: String, languageCode: String): String {
        val (samples, sampleRate) = readWavAsFloatArray(wavFilePath)

        return recognizerManager.recognizeAudio(
            samples = samples,
            sampleRate = sampleRate,
            language = languageCode
        )
    }

    fun speak(text: String, languageCode: String) {
        ttsHelper.speak(text, languageCode)
    }

    /** True when [languageCode] has a real voice, as opposed to one Speech can only transcribe. */
    fun canSpeak(languageCode: String): Boolean = ttsHelper.isSupported(languageCode)

    fun release() {
        recognizerManager.release()
        ttsHelper.release()
    }

    // ---- Internal: read a 16kHz mono 16-bit PCM WAV file into a FloatArray ----
    private fun readWavAsFloatArray(path: String): Pair<FloatArray, Int> {
        val file = File(path)
        RandomAccessFile(file, "r").use { raf ->
            // Standard WAV header is 44 bytes; sample rate is at byte offset 24
            val header = ByteArray(44)
            raf.readFully(header)

            val sampleRate =
                (header[24].toInt() and 0xff) or
                        ((header[25].toInt() and 0xff) shl 8) or
                        ((header[26].toInt() and 0xff) shl 16) or
                        ((header[27].toInt() and 0xff) shl 24)

            val dataSize = (file.length() - 44).toInt()
            val audioBytes = ByteArray(dataSize)
            raf.readFully(audioBytes)

            // Convert 16-bit little-endian PCM to normalized floats
            val sampleCount = dataSize / 2
            val samples = FloatArray(sampleCount)
            for (i in 0 until sampleCount) {
                val low = audioBytes[i * 2].toInt() and 0xff
                val high = audioBytes[i * 2 + 1].toInt()
                val sample = (high shl 8) or low
                samples[i] = sample / 32768.0f
            }

            return Pair(samples, sampleRate)
        }
    }
}