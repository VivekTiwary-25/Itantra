package com.chmod777.itantra.demo

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pre-recorded clip playback for Message Detail (never the real TTS model).
 * Play → Pause → Play resumes; at the end the next Play starts from the top.
 * One clip at a time; the activity pauses it when the app leaves the screen.
 */
object DemoAudio {

    private var player: MediaPlayer? = null
    private var loadedUri: String? = null

    private val _playingUri = MutableStateFlow<String?>(null)
    val playingUri: StateFlow<String?> = _playingUri.asStateFlow()

    /** True when [uri] still points at something readable (a picked file may have been deleted). */
    fun isAvailable(context: Context, uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        return runCatching {
            context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    /** Returns false if the clip could not be played. */
    fun toggle(context: Context, uri: String): Boolean {
        val current = player
        if (current != null && loadedUri == uri) {
            return runCatching {
                if (current.isPlaying) {
                    current.pause()
                    _playingUri.value = null
                } else {
                    current.start()
                    _playingUri.value = uri
                }
                true
            }.getOrElse { release(); false }
        }
        release()
        return runCatching {
            val mp = MediaPlayer()
            player = mp
            loadedUri = uri
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(context.applicationContext, Uri.parse(uri))
            mp.setOnCompletionListener { release() }
            mp.prepare()
            mp.start()
            _playingUri.value = uri
            true
        }.getOrElse { release(); false }
    }

    fun pause() {
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
        _playingUri.value = null
    }

    fun release() {
        runCatching { player?.release() }
        player = null
        loadedUri = null
        _playingUri.value = null
    }
}
