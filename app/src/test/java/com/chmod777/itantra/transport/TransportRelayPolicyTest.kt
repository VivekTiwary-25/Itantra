package com.chmod777.itantra.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportRelayPolicyTest {
    private fun message(messageId: Long = 10L, ttl: Int = 3) = TransportMessage(
        version = 1,
        messageId = messageId,
        ttl = ttl,
        language = MessageLanguage.ENGLISH,
        text = "relay test",
    )

    @Test
    fun `first message preserves id and decreases ttl once`() {
        val decision = TransportRelayPolicy().decideForIncoming(message(messageId = 42L, ttl = 3))

        assertTrue(decision is RelayDecision.Forward)
        val forwarded = (decision as RelayDecision.Forward).message
        assertEquals(42L, forwarded.messageId)
        assertEquals(2, forwarded.ttl)
        assertEquals("relay test", forwarded.text)
    }

    @Test
    fun `duplicate message is never forwarded again`() {
        val policy = TransportRelayPolicy()
        policy.decideForIncoming(message(messageId = 99L, ttl = 3))

        assertEquals(RelayDecision.Duplicate, policy.decideForIncoming(message(messageId = 99L, ttl = 3)))
    }

    @Test
    fun `ttl zero is accepted but not forwarded`() {
        assertEquals(
            RelayDecision.TtlExpired,
            TransportRelayPolicy().decideForIncoming(message(messageId = 7L, ttl = 0)),
        )
    }
}
