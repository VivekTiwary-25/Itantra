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
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A one-connection Bluetooth Classic RFCOMM transport.
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

    @Volatile
    private var onByteReceived: ((Int) -> Unit)? = null

    @Volatile
    private var onTextReceived: ((String) -> Unit)? = null

    private val writeLock = Any()

    @SuppressLint("MissingPermission")
    fun listen(onStateChanged: (RfcommConnectionState) -> Unit) {
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

            try {
                closeServerSocket()
                val newServerSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                serverSocket = newServerSocket
                emit(onStateChanged, RfcommConnectionState.Listening)

                // Blocks until a peer connects. This is intentionally on an I/O thread.
                val connectedSocket = newServerSocket.accept()
                socket = connectedSocket
                startReading(connectedSocket, onStateChanged)
                emit(
                    onStateChanged,
                    RfcommConnectionState.Connected(
                        peerName = connectedSocket.remoteDevice.name ?: "Unnamed device",
                        peerAddress = connectedSocket.remoteDevice.address,
                    ),
                )
            } catch (exception: IOException) {
                emit(onStateChanged, RfcommConnectionState.Error("Listener error: ${exception.message ?: "unknown error"}"))
            } finally {
                closeServerSocket()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, onStateChanged: (RfcommConnectionState) -> Unit) {
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

            try {
                // Discovery competes for radio time and makes RFCOMM setup unreliable.
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                val device = adapter.getRemoteDevice(address)
                emit(onStateChanged, RfcommConnectionState.Connecting(device.name ?: "Unnamed device"))

                val newSocket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                newSocket.connect() // Blocking; always stays off the main thread.
                socket = newSocket
                startReading(newSocket, onStateChanged)
                emit(
                    onStateChanged,
                    RfcommConnectionState.Connected(
                        peerName = device.name ?: "Unnamed device",
                        peerAddress = device.address,
                    ),
                )
            } catch (exception: IOException) {
                emit(onStateChanged, RfcommConnectionState.Error("Connection error: ${exception.message ?: "unknown error"}"))
            }
        }
    }

    /** Registers a main-thread callback for every byte received from the peer. */
    fun onByteReceived(callback: (Int) -> Unit) {
        onByteReceived = callback
    }

    /** Registers a main-thread callback for each complete UTF-8 text message. */
    fun onTextReceived(callback: (String) -> Unit) {
        onTextReceived = callback
    }

    /** Writes one byte to the connected peer. */
    fun sendByte(value: Int, onResult: (SendByteResult) -> Unit) {
        require(value in 0..255) { "A byte value must be between 0 and 255." }

        val connectedSocket = socket
        if (connectedSocket == null || !connectedSocket.isConnected) {
            emitSendResult(onResult, SendByteResult.NotConnected)
            return
        }

        executor.execute {
            try {
                synchronized(writeLock) {
                    connectedSocket.outputStream.write(value)
                    connectedSocket.outputStream.flush()
                }
                emitSendResult(onResult, SendByteResult.Sent(value))
            } catch (exception: IOException) {
                emitSendResult(
                    onResult,
                    SendByteResult.Error(exception.message ?: "could not write to the RFCOMM stream"),
                )
            }
        }
    }

    /**
     * Sends one UTF-8 string as: marker (0xFF), 2-byte byte-length, then text.
     * This temporary T7 frame gives a byte stream message boundaries; T8 will
     * replace it with the shared version/msgId/TTL protocol frame.
     */
    fun sendText(text: String, onResult: (SendTextResult) -> Unit) {
        val encoded = text.toByteArray(StandardCharsets.UTF_8)
        if (encoded.isEmpty()) {
            emitSendTextResult(onResult, SendTextResult.Error("Text cannot be empty."))
            return
        }
        if (encoded.size > MAX_TEXT_BYTES) {
            emitSendTextResult(onResult, SendTextResult.Error("Text is limited to $MAX_TEXT_BYTES UTF-8 bytes."))
            return
        }

        val connectedSocket = socket
        if (connectedSocket == null || !connectedSocket.isConnected) {
            emitSendTextResult(onResult, SendTextResult.NotConnected)
            return
        }

        executor.execute {
            try {
                synchronized(writeLock) {
                    val output = connectedSocket.outputStream
                    output.write(TEXT_FRAME_MARKER)
                    output.write(encoded.size ushr 8)
                    output.write(encoded.size and 0xFF)
                    output.write(encoded)
                    output.flush()
                }
                emitSendTextResult(onResult, SendTextResult.Sent(text))
            } catch (exception: IOException) {
                emitSendTextResult(
                    onResult,
                    SendTextResult.Error(exception.message ?: "could not write to the RFCOMM stream"),
                )
            }
        }
    }

    fun close() {
        closeServerSocket()
        try {
            socket?.close()
        } catch (_: IOException) {
            // The connection is already unusable; there is nothing further to do.
        } finally {
            socket = null
        }
        executor.shutdownNow()
    }

    private fun closeServerSocket() {
        try {
            serverSocket?.close()
        } catch (_: IOException) {
            // The listener is already closed.
        } finally {
            serverSocket = null
        }
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
                        emit(onStateChanged, RfcommConnectionState.Error("Peer disconnected."))
                        return@execute
                    }
                    if (value == TEXT_FRAME_MARKER) {
                        val length = input.readUnsignedShort()
                        if (length == 0 || length > MAX_TEXT_BYTES) {
                            emit(onStateChanged, RfcommConnectionState.Error("Invalid text frame length: $length."))
                            return@execute
                        }
                        val textBytes = ByteArray(length)
                        input.readFully(textBytes)
                        val text = String(textBytes, StandardCharsets.UTF_8)
                        mainHandler.post { onTextReceived?.invoke(text) }
                    } else {
                        mainHandler.post { onByteReceived?.invoke(value) }
                    }
                }
            } catch (exception: IOException) {
                emit(
                    onStateChanged,
                    RfcommConnectionState.Error("Read error: ${exception.message ?: "connection closed"}"),
                )
            }
        }
    }

    private fun emit(
        onStateChanged: (RfcommConnectionState) -> Unit,
        state: RfcommConnectionState,
    ) {
        mainHandler.post { onStateChanged(state) }
    }

    private fun emitSendResult(onResult: (SendByteResult) -> Unit, result: SendByteResult) {
        mainHandler.post { onResult(result) }
    }

    private fun emitSendTextResult(onResult: (SendTextResult) -> Unit, result: SendTextResult) {
        mainHandler.post { onResult(result) }
    }

    companion object {
        const val SERVICE_NAME = "iTantraRfcomm"
        private const val TEXT_FRAME_MARKER = 0xFF
        private const val MAX_TEXT_BYTES = 4_096
        val SERVICE_UUID: UUID = UUID.fromString("56d5cd5e-3d02-4f41-8d47-2a3de7b6bc10")
    }
}

sealed interface RfcommConnectionState {
    data object Idle : RfcommConnectionState
    data object Listening : RfcommConnectionState
    data class Connecting(val peerName: String) : RfcommConnectionState
    data class Connected(val peerName: String, val peerAddress: String) : RfcommConnectionState
    data class Error(val message: String) : RfcommConnectionState
}

sealed interface SendByteResult {
    data class Sent(val value: Int) : SendByteResult
    data object NotConnected : SendByteResult
    data class Error(val message: String) : SendByteResult
}

sealed interface SendTextResult {
    data class Sent(val text: String) : SendTextResult
    data object NotConnected : SendTextResult
    data class Error(val message: String) : SendTextResult
}
