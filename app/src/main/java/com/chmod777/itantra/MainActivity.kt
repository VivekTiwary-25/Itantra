package com.chmod777.itantra


import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.chmod777.itantra.ui.theme.SIH_iTantraTheme
import speech.AudioRecorder
import speech.SpeechRecognizerManager

class MainActivity : ComponentActivity() {

    private val audioRecorder = AudioRecorder()

    private var isReading = false
    private var recordingThread: Thread? = null

    // Default language
    private var selectedLanguage = "en"

    private var result by mutableStateOf("Ready")

    private lateinit var speechRecognizerManager: SpeechRecognizerManager
    private lateinit var ttsHelper: TtsHelper

    private val microphonePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (isGranted) {
                startAudioRecording()
            } else {
                result = "Microphone permission denied"

                Toast.makeText(
                    this,
                    "Microphone permission is required",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private fun startAudioRecording() {

        if (isReading) {
            result = "Already recording"
            return
        }

        val message = audioRecorder.startRecording()

        if (message != "Recording started") {
            result = message
            return
        }

        isReading = true

        result = if (selectedLanguage == "en") {
            "Recording English... Speak now"
        } else {
            "Recording Hindi... Speak now"
        }

        recordingThread = Thread {
            while (isReading) {
                audioRecorder.readAudio()
            }
        }.apply {
            start()
        }
    }

    private fun stopAudioRecording() {

        if (!isReading) {
            result = "No recording in progress"
            return
        }

        // Stop the reading loop
        isReading = false

        Thread {
            try {
                // Wait until readAudio() loop exits
                recordingThread?.join()

                // Stop AudioRecord and get all samples
                val audio = audioRecorder.stopRecording()

                if (audio.isEmpty()) {
                    runOnUiThread {
                        result = "No audio recorded"
                    }
                    return@Thread
                }

                runOnUiThread {
                    result =
                        if (selectedLanguage == "en") {
                            "Converting English speech..."
                        } else {
                            "Converting Hindi speech..."
                        }
                }

                // ---- S6: timing measurement ----
                val audioDurationSeconds = audio.size / 16000.0

                val startTime = System.currentTimeMillis()

                // Use the selected model
                val text = speechRecognizerManager.recognizeAudio(
                    samples = audio,
                    sampleRate = 16000,
                    language = selectedLanguage
                )

                val endTime = System.currentTimeMillis()
                val inferenceSeconds = (endTime - startTime) / 1000.0
                val rtf = inferenceSeconds / audioDurationSeconds

                android.util.Log.d(
                    "S6_TIMING",
                    "Audio duration: ${"%.2f".format(audioDurationSeconds)}s | " +
                            "Inference time: ${"%.2f".format(inferenceSeconds)}s | " +
                            "RTF: ${"%.3f".format(rtf)}"
                )
                // ---- end S6 ----

                runOnUiThread {
                    result = if (text.isBlank()) {
                        "No speech detected"
                    } else {
                        "$text\n\n[${"%.2f".format(inferenceSeconds)}s, RTF ${"%.3f".format(rtf)}]"
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {
                    result = "Recognition error: ${e.message}"
                }

            } finally {
                recordingThread = null
            }
        }.start()
    }
    private fun speakText(text: String) {
        Thread {
            try {
                ttsHelper.speak(text, selectedLanguage)
            } catch (e: Exception) {
                runOnUiThread {
                    result = "TTS error: ${e.message}"
                }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        speechRecognizerManager = SpeechRecognizerManager(this)
        ttsHelper = TtsHelper(this)

        setContent {
            SIH_iTantraTheme {

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Text(result)

                    Button(
                        onClick = {
                            if (!isReading) {
                                selectedLanguage = "en"
                                result = "English selected"
                            }
                        }
                    ) {
                        Text("Select English")
                    }

                    Button(
                        onClick = {
                            if (!isReading) {
                                selectedLanguage = "hi"
                                result = "Hindi selected"
                            }
                        }
                    ) {
                        Text("Select Hindi")
                    }

                    Button(
                        onClick = {
                            if (
                                checkSelfPermission(
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                startAudioRecording()
                            } else {
                                result = "Requesting microphone permission..."

                                microphonePermissionLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )
                            }
                        }
                    ) {
                        Text("Start Recording")
                    }

                    Button(
                        onClick = {
                            stopAudioRecording()
                        }
                    ) {
                        Text("Stop Recording")
                    }

                    var textToSpeak by remember { mutableStateOf("Hello, this is a test") }

                    OutlinedTextField(
                        value = textToSpeak,
                        onValueChange = { textToSpeak = it },
                        label = { Text("Text to speak") }
                    )

                    Button(
                        onClick = {
                            result = "Speaking..."
                            speakText(textToSpeak)
                        }
                    ) {
                        Text("Speak")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        isReading = false

        recordingThread?.interrupt()
        recordingThread = null

        speechRecognizerManager.release()
    }
}