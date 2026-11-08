package com.chmod777.itantra

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import speech.SpeechEngine

/**
 * Measurement helper (not a pass/fail test): speaks the three fixed sentences used by MessageSizeMeasurementTest through
 * the app's own SpeechEngine so the log line "playback start samples=N rate=R" (tag ITANTRA_TTS) gives each sentence's spoken
 * duration. Run with `am instrument -w -e class com.chmod777.itantra.TtsDurationInstrumentedTest ...`.
 */
@RunWith(AndroidJUnit4::class)
class TtsDurationInstrumentedTest {
    @Test
    fun speakFixedSentences() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = SpeechEngine(ctx)
        try {
            listOf(
                "en" to "Meet me at the shelter, the water is rising.",
                "hi" to "आश्रय स्थल पर मिलिए, पानी बढ़ रहा है।",
                "ta" to "தண்ணீர் உயர்கிறது, தங்குமிடத்தில் என்னைச் சந்திக்கவும்.",
            ).forEach { (lang, text) ->
                engine.speak(text, lang) // warm-up: model init
                engine.speak(text, lang)
            }
        } finally {
            engine.release()
        }
    }
}
