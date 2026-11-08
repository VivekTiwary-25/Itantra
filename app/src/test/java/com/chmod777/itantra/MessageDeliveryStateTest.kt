package com.chmod777.itantra

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
                TransportMessage(1, 42L, 3, "en", "hello"),
            ).failureMessage(),
        )
    }

    @Test
    fun `a legacy hop ack is never shown as delivered`() {
        assertTrue(legacyAcknowledgedState() != MessageDeliveryState.DELIVERED)
        assertEquals("Next phone received (legacy)", legacyAcknowledgedState().label)
    }

    @Test
    fun `only a verified receipt state maps to delivered`() {
        val delivered = com.chmod777.itantra.dtn.DeliveryState.entries.filter {
            it.toMessageDeliveryState() == MessageDeliveryState.DELIVERED
        }
        assertEquals(listOf(com.chmod777.itantra.dtn.DeliveryState.DELIVERED), delivered)
        assertEquals("Relayed", com.chmod777.itantra.dtn.DeliveryState.RELAYED.toMessageDeliveryState().label)
    }

    @Test
    fun `only the matching message id is acknowledged`() {
        assertTrue(matchesAcknowledgement(42L, 42L))
        assertFalse(matchesAcknowledgement(41L, 42L))
        assertFalse(matchesAcknowledgement(null, 42L))
    }
}
