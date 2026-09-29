package com.chmod777.itantra.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleDefaultsTest {

    @Test
    fun vachanaTrustedListIsTheFilmedFourAndExcludesHerselfAndVivek() {
        val contacts = RoleDefaults.trustedContacts(DemoActor.VACHANA)
        assertEquals(listOf("Yash", "Trisha", "Utkarsh Keshri", "Vaishnavi M H"), contacts)
        assertFalse("Vachana" in contacts)
        assertFalse("Vivek" in contacts)
    }

    @Test
    fun noActorTrustsThemselvesAndVivekIsNeverPreTrusted() {
        DemoActor.entries.forEach { actor ->
            val contacts = RoleDefaults.trustedContacts(actor)
            assertFalse("$actor lists itself", actor.displayName in contacts)
            assertFalse("$actor pre-trusts Vivek", "Vivek" in contacts)
        }
    }

    @Test
    fun vachanaPttAndHandsFreeDefaultsAreSeparateLines() {
        assertEquals(
            "Hey, remember to bring milk on your way back.",
            RoleDefaults.transcript(DemoActor.VACHANA, CaptureKind.PTT, "en")
        )
        assertEquals(
            "I need help near the south gate.",
            RoleDefaults.transcript(DemoActor.VACHANA, CaptureKind.HANDS_FREE, "en")
        )
        DEMO_LANGUAGES.forEach { l ->
            assertNotEquals(
                l.code,
                RoleDefaults.transcript(DemoActor.VACHANA, CaptureKind.PTT, l.code),
                RoleDefaults.transcript(DemoActor.VACHANA, CaptureKind.HANDS_FREE, l.code)
            )
        }
    }

    @Test
    fun responderDefaultsMatchTheScript() {
        CaptureKind.entries.forEach { kind ->
            assertEquals("Yep, got it.", RoleDefaults.transcript(DemoActor.YASH, kind, "en"))
            assertEquals("I'm coming. Stay where you are.", RoleDefaults.transcript(DemoActor.VIVEK, kind, "en"))
        }
    }

    @Test
    fun everyLanguageHasADefaultForEveryActorAndKind() {
        assertEquals(10, DEMO_LANGUAGES.size)
        DemoActor.entries.forEach { actor ->
            CaptureKind.entries.forEach { kind ->
                DEMO_LANGUAGES.forEach { l ->
                    assertTrue(RoleDefaults.transcript(actor, kind, l.code).isNotBlank())
                }
            }
        }
    }

    @Test
    fun presetsCoverEachActorsFilmedIncomingEvent() {
        val yash = RoleDefaults.presets(DemoActor.YASH).single()
        assertEquals("Vachana" to MessageMode.NORMAL, yash.sender to yash.mode)
        val vivek = RoleDefaults.presets(DemoActor.VIVEK).single()
        assertEquals("Vachana" to MessageMode.SOS, vivek.sender to vivek.mode)
        val vachana = RoleDefaults.presets(DemoActor.VACHANA).map { it.sender to it.mode }
        assertEquals(listOf("Yash" to MessageMode.NORMAL, "Vivek" to MessageMode.SOS_REPLY), vachana)
    }

    @Test
    fun eachFlavorIsBoundToExactlyOneActor() {
        // BuildConfig.DEMO_ACTOR is fixed per flavor; this just proves it resolves.
        assertTrue(DemoActor.current in DemoActor.entries)
    }
}
