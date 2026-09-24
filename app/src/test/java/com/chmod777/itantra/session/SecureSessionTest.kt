package com.chmod777.itantra.session

import com.chmod777.itantra.crypto.NoiseStaticKey
import com.chmod777.itantra.protocol.LinkFrame
import com.chmod777.itantra.protocol.LinkFrameType
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.testing.InMemoryPeerLink
import com.chmod777.itantra.transport.PeerLink
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureSessionTest {
    private val config = ProtocolConfig.DEFAULT.copy(noiseHandshakeTimeoutMs = 2_000)
    private val caps = PeerCapabilities(1, PeerCapabilities.FLAG_DTN_RELAY)
    private val clock = { System.nanoTime() }

    private fun links() = InMemoryPeerLink.pair(ByteArray(8) { 1 }, ByteArray(8) { 2 }, config, attPayloadBytes = 20)

    private suspend fun kotlinx.coroutines.CoroutineScope.establishBoth(
        a: PeerLink,
        b: PeerLink,
    ): Pair<SecureSession, SecureSession> {
        val sa = async { SecureSession.establish(a, this, NoiseStaticKey.generate(), caps, config, clock) }
        val sb = async { SecureSession.establish(b, this, NoiseStaticKey.generate(), caps, config, clock) }
        return sa.await() to sb.await()
    }

    @Test
    fun xxHandshakeEstablishesMatchingEncryptedSessions() = runBlocking<Unit> {
        val (a, b) = links()
        val (sa, sb) = establishBoth(a, b)
        assertArrayEquals(sa.handshakeHash, sb.handshakeHash)
        assertEquals(caps, sb.peerCapabilities)

        val secret = "inventory: bundle 42".toByteArray()
        sa.send(secret)
        assertArrayEquals(secret, withTimeout(2_000) { sb.incoming().first() })
        // Nothing after the handshake crossed the link as plaintext.
        a.sentFrames.forEach { frame ->
            val type = LinkFrame.decode(frame).type
            assertTrue(type == LinkFrameType.NOISE_HANDSHAKE || type == LinkFrameType.NOISE_TRANSPORT)
            assertFalse(String(frame, Charsets.ISO_8859_1).contains("inventory"))
        }
        sa.close()
        sb.close()
    }

    @Test
    fun freshStaticKeysGiveDifferentSessionHandles() = runBlocking<Unit> {
        val (a1, b1) = links()
        val (s1, _) = establishBoth(a1, b1)
        val (a2, b2) = links()
        val (s2, _) = establishBoth(a2, b2)
        assertFalse(s1.peerSessionKey == s2.peerSessionKey)
        assertFalse(s1.handshakeHash.contentEquals(s2.handshakeHash))
    }

    @Test
    fun tamperedTransportFrameClosesTheSession() = runBlocking<Unit> {
        val (a, b) = links()
        val (sa, sb) = establishBoth(a, b)
        // Flip one payload byte of the next fragment the initiator sends.
        a.tamper = { fragment -> fragment.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte() } }
        sa.send("hello".toByteArray())
        assertThrows(SessionTerminatedException::class.java) { runBlocking { withTimeout(2_000) { sb.incoming().toList() } } }
        assertEquals(PeerLink.State.CLOSED, b.state)
    }

    @Test
    fun replayedTransportFrameIsRejected() = runBlocking<Unit> {
        val (a, b) = links()
        val (sa, sb) = establishBoth(a, b)
        sa.send("once".toByteArray())
        val received = withTimeout(2_000) { sb.incoming().first() }
        assertArrayEquals("once".toByteArray(), received)
        // Re-inject the exact ciphertext frame the initiator just sent.
        b.injectFrame(a.sentFrames.last())
        assertThrows(SessionTerminatedException::class.java) { runBlocking { withTimeout(2_000) { sb.incoming().toList() } } }
    }

    @Test
    fun plaintextFrameAfterHandshakeIsRejected() = runBlocking<Unit> {
        val (a, b) = links()
        val (_, sb) = establishBoth(a, b)
        b.injectFrame(LinkFrame(LinkFrameType.HELLO, byteArrayOf(1, 2, 3)).encode())
        assertThrows(SessionTerminatedException::class.java) { runBlocking { withTimeout(2_000) { sb.incoming().toList() } } }
    }

    @Test
    fun malformedHandshakeFailsSafely() = runBlocking<Unit> {
        val (a, b) = links()
        // The responder receives garbage instead of Noise message 1.
        a.injectFrame(ByteArray(0))
        b.injectFrame(LinkFrame(LinkFrameType.NOISE_HANDSHAKE, ByteArray(7)).encode())
        val result = runCatching { SecureSession.establish(b, this, NoiseStaticKey.generate(), caps, config, clock) }
        assertTrue(result.exceptionOrNull() is HandshakeFailedException)
    }

    @Test
    fun replayedHandshakeFromAnEarlierSessionFails() = runBlocking<Unit> {
        val (a1, b1) = links()
        establishBoth(a1, b1)
        val recordedMessage1 = a1.sentFrames.first()
        val recordedMessage3 = a1.sentFrames[1]
        // Replay the recorded initiator messages at a fresh responder.
        val (a2, b2) = links()
        b2.injectFrame(recordedMessage1)
        b2.injectFrame(recordedMessage3)
        val result = runCatching { SecureSession.establish(b2, this, NoiseStaticKey.generate(), caps, config, clock) }
        assertTrue(result.exceptionOrNull() is HandshakeFailedException)
        a2.close()
    }

    @Test
    fun silentPeerTimesOut() = runBlocking<Unit> {
        val (a, _) = links()
        val result = runCatching {
            SecureSession.establish(a, this, NoiseStaticKey.generate(), caps, config.copy(noiseHandshakeTimeoutMs = 200), clock)
        }
        assertTrue(result.exceptionOrNull() is HandshakeFailedException)
    }
}
