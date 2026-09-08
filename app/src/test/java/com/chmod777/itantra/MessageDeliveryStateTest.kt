package com.chmod777.itantra

import com.chmod777.itantra.transport.MessageLanguage
import com.chmod777.itantra.transport.SendMessageResult
import com.chmod777.itantra.transport.TransportMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDeliveryStateTest {
    @Test
    fun `send result maps failures to visible messages`() {
        assertEquals(
            "Not connected. Connect to another phone and try again.",
            SendMessageResult.NotConnected.failureMessage(),
        )
        assertEquals("Send failed: write failed", SendMessageResult.Error("write failed").failureMessage())
        assertNull(
            SendMessageResult.Sent(
                TransportMessage(1, 42L, 3, MessageLanguage.ENGLISH, "hello"),
            ).failureMessage(),
        )
    }

    @Test
    fun `only the matching message id is acknowledged`() {
        assertTrue(matchesAcknowledgement(42L, 42L))
        assertFalse(matchesAcknowledgement(41L, 42L))
        assertFalse(matchesAcknowledgement(null, 42L))
    }
}
