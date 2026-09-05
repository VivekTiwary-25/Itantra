package speech
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

class AudioRecorder {

    private val sampleRate = 16000

    private var audioRecord: AudioRecord? = null

    private val recordedSamples = mutableListOf<Float>()

    @SuppressLint("MissingPermission")
    fun startRecording(): String {

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (bufferSize <= 0) {
            return "Could not initialize microphone"
        }

        recordedSamples.clear()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()

        return "Recording started"
    }

    fun readAudio(): FloatArray {

        val recorder = audioRecord ?: return FloatArray(0)

        val buffer = ShortArray(1024)

        val readCount = recorder.read(
            buffer,
            0,
            buffer.size
        )

        if (readCount > 0) {
            for (i in 0 until readCount) {
                recordedSamples.add(
                    buffer[i].toFloat() / 32768.0f
                )
            }
        }

        return recordedSamples.toFloatArray()
    }

    fun stopRecording(): FloatArray {

        audioRecord?.let { recorder ->
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }

            recorder.release()
        }

        audioRecord = null

        return recordedSamples.toFloatArray()
    }
}