package com.chmod777.itantra.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.DataInputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A Bluetooth Classic RFCOMM transport that can maintain multiple peer sockets.
 *
 * Its fixed [SERVICE_UUID] is the shared protocol identifier. Every iTantra
 * phone must use this exact UUID or client connections will fail.
 */
class BluetoothRfcommTransport(context: Context) {
    private val appContext = context.applicationContext
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var serverSocket: BluetoothServerSocket? = null

    @Volatile
    private var socket: BluetoothSocket? = null

    private val connectedSockets = ConcurrentHashMap<String, BluetoothSocket>()

    @Volatile
    private var isListening = false

    @Volatile
    private var onMessageReceived: ((ReceivedTransportMessage) -> Unit)? = null

    @Volatile
    private var onAcknowledgementReceived: ((Long) -> Unit)? = null

    @Volatile
    private var onRelayEvent: ((RelayEvent) -> Unit)? = null

    @Volatile
    private var connectionStateCallback: ((RfcommConnectionState) -> Unit)? = null

    private val writeLock = Any()
    private val relayPolicy = TransportRelayPolicy()
    private val pendingRelays = ConcurrentLinkedQueue<PendingRelay>()

    @SuppressLint("MissingPermission")
    fun listen(onStateChanged: (RfcommConnectionState) -> Unit) {
        connectionStateCallback = onStateChanged
        executor.execute {
            val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null) {
                emit(onStateChanged, RfcommConnectionState.Error("Bluetooth is not supported."))
                return@execute
            }
            if (!adapter.isEnabled) {
                emit(onStateChanged, RfcommConnectionState.Error("Turn Bluetooth on before listening."))
                return@execute
            }

            var listeningSocket: BluetoothServerSocket? = null
            try {
                closeServerSocket()
                val newServerSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                listeningSocket = newServerSocket
                serverSocket = newServerSocket
                isListening = true
                emit(onStateChanged, RfcommConnectionState.Listening)

                // Keep accepting so a middle phone can hold two live peer links.
                while (isListening && serverSocket === newServerSocket) {
                    val connectedSocket = newServerSocket.accept()
                    registerConnectedSocket(connectedSocket, onStateChanged)
                }
            } catch (exception: IOException) {
                if (isListening) {
                    emit(onStateChanged, RfcommConnectionState.Error("Listener error: ${exception.message ?: "unknown error"}"))
                }
            } finally {
                if (serverSocket === listeningSocket) closeServerSocket()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, onStateChanged: (RfcommConnectionState) -> Unit) {
        connectionStateCallback = onStateChanged
        executor.execute {
            val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null) {
                emit(onStateChanged, RfcommConnectionState.Error("Bluetooth is not supported."))
                return@execute
            }
            if (!adapter.isEnabled) {
                emit(onStateChanged, RfcommConnectionState.Error("Turn Bluetooth on before connecting."))
                return@execute
            }

            var newSocket: BluetoothSocket? = null
            try {
                // Discovery competes for radio time and makes RFCOMM setup unreliable.
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                val device = adapter.getRemoteDevice(address)
                emit(onStateChanged, RfcommConnectionState.Connecting(device.name ?: "Unnamed device"))

                val socketToConnect = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                newSocket = socketToConnect
                socketToConnect.connect() // Blocking; always stays off the main thread.
                registerConnectedSocket(socketToConnect, onStateChanged)
            } catch (exception: IOException) {
                try {
                    newSocket?.close()
                } catch (_: IOException) {
                    // The socket never completed setup or has already closed.
                }
                emit(onStateChanged, RfcommConnectionState.Error("Connection error: ${exception.message ?: "unknown error"}"))
            }
        }
    }

    /** Registers a main-thread callback for complete, parsed protocol messages. */
    fun onMessageReceived(callback: (ReceivedTransportMessage) -> Unit) {
        onMessageReceived = callback
    }

    /** Registers a main-thread callback for acknowledgement message IDs. */
    fun onAcknowledgementReceived(callback: (Long) -> Unit) {
        onAcknowledgementReceived = callback
    }

    /** Reports when this phone forwards a message or discards a duplicate. */
    fun onRelayEvent(callback: (RelayEvent) -> Unit) {
        onRelayEvent = callback
    }

    /** Sends the small T9 acknowledgement control frame for an accepted message. */
    fun sendAcknowledgement(messageId: Long, peerAddress: String? = null) {
        val connectedSocket = peerAddress?.let { connectedSockets[it] } ?: socket
        if (connectedSocket == null || !connectedSocket.isConnected) return

        executor.execute {
            try {
                synchronized(writeLock) {
                    val output = connectedSocket.outputStream
                    // 0 identifies an ACK. It is followed by the original 4-byte msgId.
                    output.write(ACK_FRAME_MARKER)
                    output.write((messageId shr 24).toInt())
                    output.write((messageId shr 16).toInt())
                    output.write((messageId shr 8).toInt())
                    output.write(messageId.toInt())
                    output.flush()
                }
            } catch (_: IOException) {
                handleDisconnected(connectedSocket, "Connection lost while sending acknowledgement.")
            }
        }
    }

    /** Sends a versioned T8 protocol message. */
    fun sendMessage(
        text: String,
        languageCode: String = "en",
        ttl: Int = DEFAULT_TTL,
        onResult: (SendMessageResult) -> Unit,
    ) {
        val encoded = text.toByteArray(Charsets.UTF_8)
        if (encoded.isEmpty()) {
            emitSendMessageResult(onResult, SendMessageResult.Error("Text cannot be empty."))
            return
        }
        if (encoded.size > MAX_TRANSPORT_PAYLOAD_BYTES) {
            emitSendMessageResult(
                onResult,
                SendMessageResult.Error("Text is limited to $MAX_TRANSPORT_PAYLOAD_BYTES UTF-8 bytes."),
            )
            return
        }
        if (!isSupportedTransportLanguageCode(languageCode)) {
            emitSendMessageResult(onResult, SendMessageResult.Error("Unsupported language code: $languageCode."))
            return
        }
        if (ttl !in 0..MAX_TTL) {
            emitSendMessageResult(onResult, SendMessageResult.Error("TTL must be between 0 and $MAX_TTL."))
            return
        }

        val connectedSocket = socket
        if (connectedSocket == null || !connectedSocket.isConnected) {
            emitSendMessageResult(onResult, SendMessageResult.NotConnected)
            return
        }

        val message = TransportMessage(
            version = PROTOCOL_VERSION,
            messageId = random.nextInt().toUInt().toLong(),
            ttl = ttl,
            languageCode = languageCode,
            text = text,
        )
        relayPolicy.rememberOutgoing(message.messageId)
        executor.execute {
            try {
                writeMessage(connectedSocket, message)
                emitSendMessageResult(onResult, SendMessageResult.Sent(message))
            } catch (exception: IOException) {
                handleDisconnected(connectedSocket, "Connection lost while sending a message.")
                emitSendMessageResult(
                    onResult,
                    SendMessageResult.Error(exception.message ?: "could not write to the RFCOMM stream"),
                )
            }
        }
    }

    fun close() {
        closeServerSocket()
        connectedSockets.values.forEach { connectedSocket ->
            try {
                connectedSocket.close()
            } catch (_: IOException) {
                // The connection is already unusable; there is nothing further to do.
            }
        }
        connectedSockets.clear()
        socket = null
        executor.shutdownNow()
    }

    private fun closeServerSocket() {
        try {
            serverSocket?.close()
        } catch (_: IOException) {
            // The listener is already closed.
        } finally {
            serverSocket = null
            isListening = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerConnectedSocket(
        connectedSocket: BluetoothSocket,
        onStateChanged: (RfcommConnectionState) -> Unit,
    ) {
        val address = connectedSocket.remoteDevice.address
        val previousSocket = connectedSockets.put(address, connectedSocket)
        if (previousSocket != null && previousSocket !== connectedSocket) {
            try {
                previousSocket.close()
            } catch (_: IOException) {
                // The previous connection was already closed.
            }
        }
        // The latest connection remains the default target for the existing send UI.
        socket = connectedSocket
        emitConnectionSummary(onStateChanged)
        startReading(connectedSocket, onStateChanged)
        flushPendingRelays(connectedSocket)
    }

    private fun startReading(
        connectedSocket: BluetoothSocket,
        onStateChanged: (RfcommConnectionState) -> Unit,
    ) {
        executor.execute {
            try {
                val input = DataInputStream(connectedSocket.inputStream)
                while (true) {
                    val value = input.read()
                    if (value == -1) {
                        handleDisconnected(connectedSocket, "Peer disconnected.", onStateChanged)
                        return@execute
                    }
                    if (value == ACK_FRAME_MARKER) {
                        val acknowledgedMessageId = input.readInt().toUInt().toLong()
                        mainHandler.post { onAcknowledgementReceived?.invoke(acknowledgedMessageId) }
                        continue
                    }
                    if (value != PROTOCOL_VERSION) {
                        throw IOException("Unsupported protocol version: $value.")
                    }
                    val message = readTransportMessage(input, value)
                    val sourceAddress = connectedSocket.remoteDevice.address
                    when (val decision = relayPolicy.decideForIncoming(message)) {
                        RelayDecision.Duplicate -> {
                            mainHandler.post { onRelayEvent?.invoke(RelayEvent.DuplicateIgnored(message.messageId)) }
                            continue
                        }
                        RelayDecision.TtlExpired -> {
                            mainHandler.post { onRelayEvent?.invoke(RelayEvent.TtlExpired(message.messageId)) }
                        }
                        is RelayDecision.Forward -> {
                            // Forward only to other peers. The original ID survives, but each
                            // hop spends one TTL so a loop naturally stops even without deduplication.
                            relayToOtherPeers(message, decision.message, sourceAddress)
                        }
                    }
                    mainHandler.post {
                        onMessageReceived?.invoke(
                            ReceivedTransportMessage(
                                message = message,
                                sourcePeerAddress = sourceAddress,
                                sourcePeerName = connectedSocket.remoteDevice.name ?: "Unnamed device",
                            ),
                        )
                    }
                }
            } catch (exception: IOException) {
                handleDisconnected(
                    connectedSocket,
                    "Connection lost: ${exception.message ?: "connection closed"}",
                    onStateChanged,
                )
            }
        }
    }

    /**
     * Clears a dead session without shutting down the executor, so the caller can
     * immediately listen or connect again without restarting the app.
     */
    private fun handleDisconnected(
        disconnectedSocket: BluetoothSocket,
        reason: String,
        callback: ((RfcommConnectionState) -> Unit)? = connectionStateCallback,
    ) {
        val address = disconnectedSocket.remoteDevice.address
        if (!connectedSockets.remove(address, disconnectedSocket)) return
        try {
            disconnectedSocket.close()
        } catch (_: IOException) {
            // It is already closed.
        }
        if (socket === disconnectedSocket) socket = connectedSockets.values.firstOrNull()
        callback?.let {
            if (connectedSockets.isEmpty() && !isListening) {
                emit(it, RfcommConnectionState.Disconnected(reason))
            } else {
                emitConnectionSummary(it)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun relayToOtherPeers(
        message: TransportMessage,
        forwardedMessage: TransportMessage,
        sourceAddress: String,
    ) {
        val targets = connectedSockets.entries.filter { (address, connectedSocket) ->
            address != sourceAddress && connectedSocket.isConnected
        }
        if (targets.isEmpty()) {
            queueRelay(forwardedMessage, sourceAddress)
            return
        }
        executor.execute {
            var forwardedCount = 0
            targets.forEach { (address, connectedSocket) ->
                try {
                    writeMessage(connectedSocket, forwardedMessage)
                    forwardedCount++
                } catch (_: IOException) {
                    handleDisconnected(connectedSocket, "Connection lost while relaying a message.")
                }
            }
            val targetNames = targets.filter { (address, _) -> connectedSockets.containsKey(address) }
                .map { (_, connectedSocket) -> connectedSocket.remoteDevice.name ?: "Unnamed device" }
            mainHandler.post {
                onRelayEvent?.invoke(
                    RelayEvent.Forwarded(
                        messageId = message.messageId,
                        ttlAfterRelay = forwardedMessage.ttl,
                        peerNames = targetNames,
                        forwardedCount = forwardedCount,
                    ),
                )
            }
        }
    }

    /** Keeps an already-relayed frame in memory until another peer connects. */
    private fun queueRelay(message: TransportMessage, sourceAddress: String) {
        if (pendingRelays.size >= MAX_PENDING_RELAYS) pendingRelays.poll()
        pendingRelays.offer(PendingRelay(message, sourceAddress))
        mainHandler.post {
            onRelayEvent?.invoke(RelayEvent.Queued(message.messageId, pendingRelays.size))
        }
    }

    @SuppressLint("MissingPermission")
    private fun flushPendingRelays(newPeerSocket: BluetoothSocket) {
        executor.execute {
            val newPeerAddress = newPeerSocket.remoteDevice.address
            val newPeerName = newPeerSocket.remoteDevice.name ?: "Unnamed device"
            val delivered = mutableListOf<PendingRelay>()
            pendingRelays.forEach { pending ->
                // Never echo the relay back to the phone that originally sent it.
                if (pending.sourceAddress != newPeerAddress) {
                    try {
                        writeMessage(newPeerSocket, pending.message)
                        delivered += pending
                        pendingRelays.remove(pending)
                    } catch (_: IOException) {
                        handleDisconnected(newPeerSocket, "Connection lost while delivering a queued message.")
                        return@execute
                    }
                }
            }
            delivered.forEach { pending ->
                mainHandler.post {
                    onRelayEvent?.invoke(RelayEvent.DeliveredFromQueue(pending.message.messageId, newPeerName))
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun writeMessage(targetSocket: BluetoothSocket, message: TransportMessage) {
        synchronized(writeLock) {
            writeTransportMessage(targetSocket.outputStream, message)
        }
    }

    @SuppressLint("MissingPermission")
    private fun emitConnectionSummary(onStateChanged: (RfcommConnectionState) -> Unit) {
        val peers = connectedSockets.values.map { connectedSocket ->
            RfcommPeer(
                name = connectedSocket.remoteDevice.name ?: "Unnamed device",
                address = connectedSocket.remoteDevice.address,
            )
        }.sortedBy { it.name }
        emit(onStateChanged, RfcommConnectionState.Connections(peers, isListening))
    }

    private fun emit(
        onStateChanged: (RfcommConnectionState) -> Unit,
        state: RfcommConnectionState,
    ) {
        mainHandler.post { onStateChanged(state) }
    }

    private fun emitSendMessageResult(onResult: (SendMessageResult) -> Unit, result: SendMessageResult) {
        mainHandler.post { onResult(result) }
    }

    companion object {
        const val SERVICE_NAME = "iTantraRfcomm"
        const val PROTOCOL_VERSION = TRANSPORT_PROTOCOL_VERSION
        const val DEFAULT_TTL = 3
        private const val MAX_TTL = 255
        private const val MAX_PENDING_RELAYS = 100
        private val random = SecureRandom()
        val SERVICE_UUID: UUID = UUID.fromString("56d5cd5e-3d02-4f41-8d47-2a3de7b6bc10")
    }
}

sealed interface RfcommConnectionState {
    data object Idle : RfcommConnectionState
    data object Listening : RfcommConnectionState
    data class Connecting(val peerName: String) : RfcommConnectionState
    data class Connected(val peerName: String, val peerAddress: String) : RfcommConnectionState
    data class Connections(val peers: List<RfcommPeer>, val isListening: Boolean) : RfcommConnectionState
    data class Disconnected(val reason: String) : RfcommConnectionState
    data class Error(val message: String) : RfcommConnectionState
}

data class RfcommPeer(val name: String, val address: String)

data class TransportMessage(
    val version: Int,
    val messageId: Long,
    val ttl: Int,
    val languageCode: String,
    val text: String,
)

data class ReceivedTransportMessage(
    val message: TransportMessage,
    val sourcePeerAddress: String,
    val sourcePeerName: String,
)

private data class PendingRelay(val message: TransportMessage, val sourceAddress: String)

sealed interface RelayEvent {
    data class Forwarded(
        val messageId: Long,
        val ttlAfterRelay: Int,
        val peerNames: List<String>,
        val forwardedCount: Int,
    ) : RelayEvent
    data class DuplicateIgnored(val messageId: Long) : RelayEvent
    data class TtlExpired(val messageId: Long) : RelayEvent
    data class NoOtherPeer(val messageId: Long) : RelayEvent
    data class Queued(val messageId: Long, val pendingCount: Int) : RelayEvent
    data class DeliveredFromQueue(val messageId: Long, val peerName: String) : RelayEvent
}

sealed interface SendMessageResult {
    data class Sent(val message: TransportMessage) : SendMessageResult
    data object NotConnected : SendMessageResult
    data class Error(val message: String) : SendMessageResult
}
