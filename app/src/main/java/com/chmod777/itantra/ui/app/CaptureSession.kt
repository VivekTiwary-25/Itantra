package com.chmod777.itantra.ui.app

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chmod777.itantra.LATENCY_TAG
import com.chmod777.itantra.PcmRecorder
import com.chmod777.itantra.WAV_HEADER_SIZE
import com.chmod777.itantra.network.ItantraNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import speech.SpeechEngine
import java.io.File

/** A finished take, waiting for whichever composition is on screen to pick it up. */
internal data class Transcript(val text: String, val languageCode: String, val source: String)

/**
 * The microphone, the recognizer and any transcription in flight, owned by the process rather
 * than by a composition.
 *
 * This exists because of a crash. The recognizer used to be created with `remember` and released
 * from a `DisposableEffect`, and the transcription ran in `rememberCoroutineScope`. Rotating the
 * phone during a decode therefore released the native recognizer while `OfflineRecognizer.decode`
 * was still inside it on a worker thread:
 *
 *     Fatal signal 11 (SIGSEGV), fault addr 0x798, tid (DefaultDispatch)
 *     #03 Java_com_k2fsa_sherpa_onnx_OfflineRecognizer_decode
 *     #13 speech.SpeechRecognizerManager.recognizeAudio
 *     #21 AppState$startTranscription$1$text$1.invokeSuspend
 *
 * Cancelling the coroutine could never have helped: the thread is inside a JNI call that does not
 * check for interruption, so the only fix is for nothing to free the recognizer underneath it.
 * Here the engine is created once on the application context and never released before the
 * process ends, and the work runs in a scope that no screen can cancel. The transcript lands in
 * [pending] and waits there, so a take survives a recreation instead of being dropped.
 *
 * Keeping the models loaded between screens is a cost worth naming: they stay in memory for the
 * life of the process. It is also a gain — a model load is 1.3 s to 3.7 s on this phone, and
 * before this change every rotation paid it again.
 *
 * Nothing in `speech/` changes; this only decides who owns it and for how long.
 */
internal object CaptureSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var speechOrNull: SpeechEngine? = null
    private var recorderOrNull: PcmRecorder? = null
    private var fileOrNull: File? = null

    /** Safe to call on every composition; the second call onwards does nothing. */
    fun attach(context: Context) {
        if (speechOrNull != null) return
        val app = context.applicationContext
        val file = File(app.filesDir, "recording.wav")
        fileOrNull = file
        recorderOrNull = PcmRecorder(file)
        speechOrNull = SpeechEngine(app)
    }

    val speech: SpeechEngine get() = requireNotNull(speechOrNull) { "CaptureSession.attach not called" }
    val recorder: PcmRecorder get() = requireNotNull(recorderOrNull) { "CaptureSession.attach not called" }
    val recordingFile: File get() = requireNotNull(fileOrNull) { "CaptureSession.attach not called" }

    var isTranscribing by mutableStateOf(false)
        private set

    /** Shown by Home and Hands-free. Here rather than in the screen, so a recreation keeps it. */
    var captureError by mutableStateOf<CaptureError?>(null)

    /** The last finished take nobody has taken yet. */
    var pending by mutableStateOf<Transcript?>(null)
        private set

    fun takePending(): Transcript? = pending?.also { pending = null }

    fun discardRecording() {
        runCatching { fileOrNull?.takeIf { it.exists() }?.outputStream()?.close() }
    }

    /**
     * Runs the recognizer over the take just recorded. [micGranted] only decides which message an
     * empty file earns: no samples with the microphone off is a blocked microphone, and no
     * samples with it on is a hold too short to catch anything.
     */
    fun transcribe(source: String, languageCode: String, micGranted: Boolean) {
        if (isTranscribing) return
        val file = fileOrNull
        if (file == null || file.length() <= WAV_HEADER_SIZE) {
            captureError = if (micGranted) CaptureError.NOTHING_HEARD else CaptureError.MIC_BLOCKED
            return
        }
        captureError = null
        isTranscribing = true
        val releaseElapsedMs = SystemClock.elapsedRealtime()
        Log.d(LATENCY_TAG, "[$source] release t=$releaseElapsedMs")
        ItantraNetwork.recordProductTiming("product_speech_end", mapOf("source" to source))
        scope.launch {
            val text = try {
                speech.transcribe(file.absolutePath, languageCode)
            } catch (e: Throwable) {
                Log.e(LATENCY_TAG, "[$source] transcribe failed lang=$languageCode", e)
                captureError = CaptureError.TRANSCRIBE_FAILED
                isTranscribing = false
                return@launch
            }
            val readyElapsedMs = SystemClock.elapsedRealtime()
            Log.d(
                LATENCY_TAG,
                "[$source] transcript ready deltaMs=${readyElapsedMs - releaseElapsedMs} " +
                    "wavBytes=${file.length()}"
            )
            ItantraNetwork.recordProductTiming("product_transcript_ready", mapOf("source" to source))
            if (heardNothing(text)) {
                Log.d(LATENCY_TAG, "[$source] nothing heard, raw=${text.take(32)}")
                captureError = CaptureError.NOTHING_HEARD
                discardRecording()
                isTranscribing = false
                return@launch
            }
            pending = Transcript(text = text, languageCode = languageCode, source = source)
            isTranscribing = false
        }
    }
}
