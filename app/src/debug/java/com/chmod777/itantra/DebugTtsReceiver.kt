package com.chmod777.itantra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import java.util.concurrent.Executors

/**
 * Debug-only validation hook. Speaks `text_b64` (base64 of UTF-8) in `lang` through
 * the same TtsHelper the receive path uses:
 *
 *   adb shell am broadcast -n com.chmod777.itantra/.DebugTtsReceiver --es lang ml --es text_b64 <b64>
 *
 * Not part of the release build. Speech is logged under ITANTRA_TTS by TtsHelper.
 */
class DebugTtsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val lang = intent.getStringExtra("lang") ?: return
        val b64 = intent.getStringExtra("text_b64") ?: return
        val text = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
        val helper = helper(context.applicationContext)
        val pending = goAsync()
        executor.execute {
            try {
                Log.d(TAG, "debug speak lang=$lang codepoints=${text.codePointCount(0, text.length)}")
                helper.speak(text, lang)
                Log.d(TAG, "debug speak returned lang=$lang")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ITANTRA_TTS"
        val executor = Executors.newSingleThreadExecutor()
        private var instance: TtsHelper? = null

        @Synchronized
        fun helper(context: Context): TtsHelper =
            instance ?: TtsHelper(context).also { instance = it }
    }
}
