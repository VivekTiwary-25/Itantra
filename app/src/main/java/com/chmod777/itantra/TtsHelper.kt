package com.chmod777.itantra

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File


class TtsHelper(private val context: Context) {

    private val hindiModelDir = "vits-piper-hi_IN-pratham-medium"
    private val englishModelDir = "vits-piper-en_US-ryan-medium"
    private val bengaliModelDir = "vits-mms-ben"
    private val marathiModelDir = "vits-mms-mar"

    private var hindiTts: OfflineTts? = null
    private var englishTts: OfflineTts? = null
    private var bengaliTts: OfflineTts? = null
    private var marathiTts: OfflineTts? = null
    private var currentAudioTrack: AudioTrack? = null

    // ---- Public API: exactly the contract from Part 3 of the lane doc ----


    fun speak(text: String, languageCode: String) {
        android.util.Log.d("TtsHelper", "speak() called with text=\"$text\" len=${text.length} lang=$languageCode")

        val ttsEngine = when (languageCode) {
            "hi" -> getOrInitHindi()
            "en" -> getOrInitEnglish()
            "bn" -> getOrInitBengali()
            "mr" -> getOrInitMarathi()
            else -> null
        } ?: return

        val audio = ttsEngine.generate(text = text, sid = 0, speed = 1.0f)
        playAudio(audio.samples, audio.sampleRate)
    }

    // ---- Internal implementation below ----

    private fun getOrInitHindi(): OfflineTts {
        return hindiTts ?: loadModel(hindiModelDir, "hi_IN-pratham-medium.onnx").also { hindiTts = it }
    }

    private fun getOrInitEnglish(): OfflineTts {
        return englishTts ?: loadModel(englishModelDir, "en_US-ryan-medium.onnx").also { englishTts = it }
    }

    private fun getOrInitBengali(): OfflineTts {
        return bengaliTts ?: loadModel(bengaliModelDir, "model.onnx", useEspeakData = false).also { bengaliTts = it }
    }

    private fun getOrInitMarathi(): OfflineTts {
        return marathiTts ?: loadModel(marathiModelDir, "model.onnx", useEspeakData = false).also { marathiTts = it }
    }


    private fun loadModel(modelDirName: String, onnxFileName: String, useEspeakData: Boolean = true): OfflineTts {
        val destDir = File(context.filesDir, modelDirName).absolutePath

        // FORCE re-copy for debugging
        val dest = File(destDir)
        if (dest.exists()) {
            dest.deleteRecursively()
        }
        copyAssetFolder(context.assets, modelDirName, destDir)

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$destDir/$onnxFileName",
                    tokens = "$destDir/tokens.txt",
                    dataDir = if (useEspeakData) "$destDir/espeak-ng-data" else ""
                ),
                numThreads = 2,
                debug = true          // turn debug on
            )
        )
        return OfflineTts(config = config)
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
        // Stop and release any currently playing audio first
        currentAudioTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                // ignore if already stopped/released
            }
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferSizeInBytes = maxOf(minBufferSize, samples.size * 4)

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
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        currentAudioTrack = audioTrack
        audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        audioTrack.play()
    }
}