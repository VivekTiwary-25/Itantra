package com.chmod777.itantra

import com.chmod777.itantra.ui.app.heardNothing
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptTest {
    @Test
    fun `empty and whitespace are nothing heard`() {
        assertTrue(heardNothing(""))
        assertTrue(heardNothing("   "))
        assertTrue(heardNothing("\n\t "))
    }

    @Test
    fun `whisper annotations are nothing heard`() {
        assertTrue(heardNothing("[BLANK_AUDIO]"))
        assertTrue(heardNothing(" [BLANK_AUDIO] "))
        assertTrue(heardNothing("[MUSIC]"))
        assertTrue(heardNothing("[SIDE CONVERSATION]"))
        assertTrue(heardNothing("(silence)"))
        assertTrue(heardNothing("[BLANK_AUDIO] [BLANK_AUDIO]"))
    }

    @Test
    fun `a long silence repeats the token and the last one is cut off`() {
        // Verbatim shape of what 17 seconds of a quiet room produced on the phone.
        val repeated = List(34) { "[Music]" }.joinToString(" ") + " [Music"
        assertTrue(heardNothing(repeated))
        assertTrue(heardNothing("[Music] [Music] [Mus"))
        assertTrue(heardNothing("[BLANK_AUDIO] [BLANK"))
    }

    @Test
    fun `real speech is kept, even next to an annotation`() {
        assertFalse(heardNothing("Bring the medical kit to the south gate."))
        assertFalse(heardNothing("[MUSIC] bring the kit"))
        assertFalse(heardNothing("दक्षिण गेट पर मेडिकल किट लाओ"))
        assertFalse(heardNothing("no"))
        // A bracket inside a sentence must not make the whole thing disappear.
        assertFalse(heardNothing("meet me at the gate (the north one)"))
        assertFalse(heardNothing("[Music] we are at the gate"))
        assertFalse(heardNothing("[Music] [Music] send help"))
    }
}
