package com.chmod777.itantra.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

const val SAMPLE_RATE_HZ = 16000
const val WAV_HEADER_SIZE = 44

/** Writes one 16 kHz, 16-bit, mono PCM WAV per start/stop (the Speech lane contract). */
class PcmRecorder(private val outputFile: File) {
    @Volatile private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    /** Smoothed input level in 0..1, for the waveform only. */
    @Volatile var level: Float = 0f
        private set

    val recording: Boolean get() = isRecording

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRecording) return true
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize)
        } catch (e: Exception) {
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        audioRecord = record
        isRecording = true
        level = 0f
        record.startRecording()
        recordingThread = Thread {
            val buffer = ByteArray(minBufferSize)
            RandomAccessFile(outputFile, "rw").use { file ->
                file.setLength(0)
                file.seek(WAV_HEADER_SIZE.toLong())
                while (isRecording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        file.write(buffer, 0, read)
                        level = level * 0.5f + rms(buffer, read) * 0.5f
                    }
                }
                val dataLength = file.length() - WAV_HEADER_SIZE
                file.seek(0)
                file.write(buildWavHeader(dataLength))
            }
        }.also { it.start() }
        return true
    }

    fun stop() {
        if (!isRecording && recordingThread == null) return
        isRecording = false
        recordingThread?.join()
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        level = 0f
    }

    /** Recorded audio length in ms, from the file on disk. */
    fun durationMs(): Long = ((outputFile.length() - WAV_HEADER_SIZE).coerceAtLeast(0) * 1000) / (SAMPLE_RATE_HZ * 2)

    private fun rms(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        val samples = length / 2
        if (samples == 0) return 0f
        for (i in 0 until samples) {
            val s = ((buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xff)).toShort() / 32768.0
            sum += s * s
        }
        // Speech RMS rarely exceeds ~0.3; sqrt spreads quiet input for a livelier meter.
        return sqrt((sqrt(sum / samples) / 0.3).coerceIn(0.0, 1.0)).toFloat()
    }
}

private fun buildWavHeader(dataLength: Long): ByteArray {
    val bitsPerSample = 16
    val channels = 1
    val byteRate = SAMPLE_RATE_HZ * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val buffer = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
    buffer.putInt((36 + dataLength).toInt())
    buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
    buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
    buffer.putInt(16)
    buffer.putShort(1.toShort()) // PCM
    buffer.putShort(channels.toShort())
    buffer.putInt(SAMPLE_RATE_HZ)
    buffer.putInt(byteRate)
    buffer.putShort(blockAlign.toShort())
    buffer.putShort(bitsPerSample.toShort())
    buffer.put("data".toByteArray(Charsets.US_ASCII))
    buffer.putInt(dataLength.toInt())
    return buffer.array()
}
