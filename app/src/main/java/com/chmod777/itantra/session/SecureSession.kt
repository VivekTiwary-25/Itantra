package com.chmod777.itantra.session

import com.chmod777.itantra.crypto.CryptoFailure
import com.chmod777.itantra.crypto.NoiseHandshake
import com.chmod777.itantra.crypto.NoiseStaticKey
import com.chmod777.itantra.crypto.NoiseTransport
import com.chmod777.itantra.crypto.Primitives
import com.chmod777.itantra.crypto.shortHex
import com.chmod777.itantra.protocol.LinkFrame
import com.chmod777.itantra.protocol.LinkFrameType
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.protocol.cbor.CborCodec
import com.chmod777.itantra.protocol.cbor.CborLimits
import com.chmod777.itantra.protocol.cbor.MalformedInputException
import com.chmod777.itantra.protocol.cbor.asMap
import com.chmod777.itantra.protocol.cbor.cborMap
import com.chmod777.itantra.protocol.cbor.int
import com.chmod777.itantra.transport.LinkClosedException
import com.chmod777.itantra.transport.LinkRole
import com.chmod777.itantra.transport.PeerLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Capabilities exchanged inside Noise messages 2 and 3. */
data class PeerCapabilities(val protocolMajor: Int, val flags: Int) {
    val relaysBundles: Boolean get() = flags and FLAG_DTN_RELAY != 0
    val availableToHelp: Boolean get() = flags and FLAG_SOS_RESPONDER != 0

    fun encode(): ByteArray = CborCodec.encode(cborMap { put(1, protocolMajor); put(2, flags) })

    companion object {
        const val FLAG_DTN_RELAY = 0x01
        const val FLAG_SOS_RESPONDER = 0x02

        @Throws(MalformedInputException::class)
        fun decode(bytes: ByteArray): PeerCapabilities {
            val map = CborCodec.decode(bytes, CborLimits(maxInputBytes = 64, maxDepth = 1, maxContainerItems = 8)).asMap()
            return PeerCapabilities(map.int(1, 0..255), map.int(2, 0..0xFFFF))
        }
    }
}

class HandshakeFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A Noise-XX-protected session over one [PeerLink] (spec §9). After [establish]
 * returns, every byte crossing the link is a Noise transport message.
 */
class SecureSession private constructor(
    val link: PeerLink,
    private val transport: NoiseTransport,
    private val inbound: ReceiveChannel<ByteArray>,
    val peerCapabilities: PeerCapabilities,
    val handshakeDurationNs: Long,
) {
    /** Session-scoped peer handle: hash of the peer's ephemeral-per-session Noise static key. */
    val peerSessionKey: String = Primitives.sha256(transport.remoteStaticKey).shortHex(12)

    /** Noise handshake hash, used for the SOS short authentication string (audit #6). */
    val handshakeHash: ByteArray get() = transport.handshakeHash.copyOf()

    private val sendMutex = Mutex()

    suspend fun send(plaintext: ByteArray) {
        sendMutex.withLock {
            val ciphertext = transport.encrypt(plaintext)
            link.send(LinkFrame(LinkFrameType.NOISE_TRANSPORT, ciphertext).encode())
        }
    }

    /**
     * Decrypted protocol frames. The first undecryptable, replayed or non-transport
     * frame closes the link: implicit Noise nonces cannot resynchronise.
     */
    fun incoming(): Flow<ByteArray> = flow {
        for (raw in inbound) {
            val plaintext = try {
                val frame = LinkFrame.decode(raw)
                if (frame.type != LinkFrameType.NOISE_TRANSPORT) throw MalformedInputException("non-transport frame after handshake")
                transport.decrypt(frame.body)
            } catch (e: Exception) {
                if (e !is CryptoFailure && e !is MalformedInputException) throw e
                link.close()
                throw SessionTerminatedException("session closed: ${e.message}")
            }
            emit(plaintext)
        }
    }

    suspend fun close() {
        inbound.cancel()
        link.close()
        transport.destroy()
    }

    companion object {
        /**
         * Runs the XX handshake on a READY link. The link's [LinkRole.INITIATOR] side is
         * the Noise initiator. Throws [HandshakeFailedException] on timeout or any failure;
         * the caller then closes the link.
         */
        suspend fun establish(
            link: PeerLink,
            scope: CoroutineScope,
            staticKey: NoiseStaticKey,
            localCapabilities: PeerCapabilities,
            config: ProtocolConfig,
            monotonicNs: () -> Long,
        ): SecureSession {
            val started = monotonicNs()
            val inbound = link.incomingFrames().produceIn(scope)
            val handshake = NoiseHandshake(link.role, staticKey, localCapabilities.encode())
            try {
                var peerPayload: ByteArray? = null
                withTimeout(config.noiseHandshakeTimeoutMs) {
                    while (!handshake.isComplete) {
                        if (handshake.needsWrite) {
                            link.send(LinkFrame(LinkFrameType.NOISE_HANDSHAKE, handshake.writeMessage()).encode())
                        } else if (handshake.needsRead) {
                            val frame = LinkFrame.decode(inbound.receive())
                            if (frame.type != LinkFrameType.NOISE_HANDSHAKE) {
                                throw MalformedInputException("expected Noise handshake, got ${frame.type}")
                            }
                            val payload = handshake.readMessage(frame.body)
                            if (payload.isNotEmpty()) peerPayload = payload
                        } else {
                            throw CryptoFailure("Noise handshake entered a failed state")
                        }
                    }
                }
                val capabilities = PeerCapabilities.decode(
                    peerPayload ?: throw MalformedInputException("peer sent no capability payload"),
                )
                if (capabilities.protocolMajor != config.protocolMajor) {
                    throw MalformedInputException("peer speaks protocol ${capabilities.protocolMajor}")
                }
                return SecureSession(link, handshake.finish(), inbound, capabilities, monotonicNs() - started)
            } catch (e: Exception) {
                handshake.destroy()
                inbound.cancel()
                if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                throw HandshakeFailedException("Noise XX failed: ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }
    }
}

class SessionTerminatedException(message: String) : LinkClosedException(message)
