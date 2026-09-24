package com.chmod777.itantra.crypto

import com.chmod777.itantra.transport.LinkRole
import com.southernstorm.noise.protocol.CipherState
import com.southernstorm.noise.protocol.HandshakeState
import com.southernstorm.noise.protocol.Noise

/**
 * Fresh per-Emergency-mode-session Noise static key (audit #6). Never derived from
 * or linked to the long-term Ed25519/X25519 identity.
 */
class NoiseStaticKey private constructor(internal val privateKey: ByteArray) {
    val publicKey: ByteArray = Primitives.x25519PublicKey(privateKey)

    companion object {
        fun generate() = NoiseStaticKey(Primitives.x25519GeneratePrivate())
    }
}

/**
 * `Noise_XX_25519_ChaChaPoly_SHA256` handshake driven by the pinned
 * `org.signal.forks:noise-java` library. This class only sequences library calls.
 *
 * Message 1 (→ e) carries no payload because it is unencrypted. Messages 2 and 3
 * carry each side's capability payload, encrypted by the handshake.
 */
class NoiseHandshake(role: LinkRole, staticKey: NoiseStaticKey, private val localPayload: ByteArray) {
    private val state = HandshakeState(
        PROTOCOL_NAME,
        if (role == LinkRole.INITIATOR) HandshakeState.INITIATOR else HandshakeState.RESPONDER,
    )
    private var messagesProcessed = 0

    init {
        require(localPayload.size <= MAX_HANDSHAKE_PAYLOAD)
        state.localKeyPair.setPrivateKey(staticKey.privateKey, 0)
        state.setPrologue(Domains.NOISE_PROLOGUE, 0, Domains.NOISE_PROLOGUE.size)
        state.start()
    }

    val needsWrite: Boolean get() = state.action == HandshakeState.WRITE_MESSAGE
    val needsRead: Boolean get() = state.action == HandshakeState.READ_MESSAGE
    val isComplete: Boolean get() = state.action == HandshakeState.SPLIT

    fun writeMessage(): ByteArray = guarded("write") {
        check(needsWrite) { "handshake is not expecting a write" }
        // The first XX message is sent before any key exists, so it must not carry data.
        val payload = if (messagesProcessed == 0) ByteArray(0) else localPayload
        val buffer = ByteArray(Noise.MAX_PACKET_LEN)
        val length = state.writeMessage(buffer, 0, payload, 0, payload.size)
        messagesProcessed++
        buffer.copyOf(length)
    }

    /** Returns the peer's handshake payload (empty for message 1). */
    fun readMessage(message: ByteArray): ByteArray = guarded("read") {
        check(needsRead) { "handshake is not expecting a read" }
        if (message.size > Noise.MAX_PACKET_LEN) throw CryptoFailure("handshake message too large")
        val payload = ByteArray(message.size)
        val length = state.readMessage(message, 0, message.size, payload, 0)
        messagesProcessed++
        if (length > MAX_HANDSHAKE_PAYLOAD) throw CryptoFailure("handshake payload too large")
        payload.copyOf(length)
    }

    fun finish(): NoiseTransport = guarded("split") {
        check(isComplete) { "handshake not complete" }
        val hash = state.handshakeHash.copyOf()
        val remoteStatic = ByteArray(state.remotePublicKey.publicKeyLength).also { state.remotePublicKey.getPublicKey(it, 0) }
        val pair = state.split()
        state.destroy()
        NoiseTransport(pair.sender, pair.receiver, hash, remoteStatic)
    }

    fun destroy() = state.destroy()

    private inline fun <T> guarded(operation: String, block: () -> T): T = try {
        block()
    } catch (e: CryptoFailure) {
        throw e
    } catch (e: Exception) {
        // BadPaddingException, ShortBufferException, IllegalStateException...
        throw CryptoFailure("Noise handshake $operation failed: ${e.javaClass.simpleName}", e)
    }

    companion object {
        const val PROTOCOL_NAME = "Noise_XX_25519_ChaChaPoly_SHA256"
        const val MAX_HANDSHAKE_PAYLOAD = 256
    }
}

/**
 * Post-handshake transport ciphers. Nonces are implicit and strictly sequential,
 * so a replayed, reordered, dropped or modified frame fails authentication and
 * the session must be torn down.
 */
class NoiseTransport internal constructor(
    private val sender: CipherState,
    private val receiver: CipherState,
    val handshakeHash: ByteArray,
    val remoteStaticKey: ByteArray,
) {
    @Synchronized
    fun encrypt(plaintext: ByteArray): ByteArray {
        require(plaintext.size <= MAX_PLAINTEXT) { "plaintext exceeds one Noise message" }
        val out = ByteArray(plaintext.size + sender.macLength)
        val length = sender.encryptWithAd(null, plaintext, 0, out, 0, plaintext.size)
        return out.copyOf(length)
    }

    @Synchronized
    fun decrypt(ciphertext: ByteArray): ByteArray {
        if (ciphertext.size < receiver.macLength || ciphertext.size > Noise.MAX_PACKET_LEN) {
            throw CryptoFailure("Noise transport message has invalid length")
        }
        return try {
            val out = ByteArray(ciphertext.size)
            val length = receiver.decryptWithAd(null, ciphertext, 0, out, 0, ciphertext.size)
            out.copyOf(length)
        } catch (e: Exception) {
            throw CryptoFailure("Noise transport authentication failed", e)
        }
    }

    fun destroy() {
        sender.destroy()
        receiver.destroy()
    }

    companion object {
        const val MAX_PLAINTEXT = Noise.MAX_PACKET_LEN - 16
    }
}
