package com.chmod777.itantra.transport.rfcomm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.chmod777.itantra.crypto.Primitives
import com.chmod777.itantra.crypto.toHex
import com.chmod777.itantra.metrics.MetricsSink
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.transport.LinkClosedException
import com.chmod777.itantra.transport.LinkRole
import com.chmod777.itantra.transport.PeerLink
import com.chmod777.itantra.transport.TransportKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

/**
 * OPTIONAL v1 adapter (spec §5, §6): Bluetooth Classic RFCOMM behind [PeerLink].
 * Frames are `u32 length || bytes`, bounded by [ProtocolConfig.maxLinkFrameBytes].
 * It is never required for SOS and is always wrapped in the same Noise session.
 *
 * Uses its own service UUID so the legacy demo transport (`BluetoothRfcommTransport`)
 * keeps working unchanged. [peerSessionId] is a random per-link ID, never the MAC.
 */
class RfcommPeerLink internal constructor(
    private val socket: BluetoothSocket,
    override val role: LinkRole,
    private val config: ProtocolConfig,
    private val scope: CoroutineScope,
) : PeerLink {
    override val peerSessionId: ByteArray = Primitives.randomBytes(8)
    override val transportKind = TransportKind.RFCOMM
    override val linkId: String = "rfc-" + Primitives.randomBytes(3).toHex()
    override val maxFrameBytes: Int get() = config.maxLinkFrameBytes
    private val mutableState = MutableStateFlow(PeerLink.State.READY)
    override val stateFlow: StateFlow<PeerLink.State> = mutableState
    private val inbound = Channel<ByteArray>(64)
    private val writeMutex = Mutex()

    init {
        scope.launch(Dispatchers.IO) {
            try {
                val input = DataInputStream(socket.inputStream)
                while (true) {
                    val length = input.readInt()
                    if (length <= 0 || length > config.maxLinkFrameBytes) throw IOException("bad RFCOMM frame length $length")
                    val frame = ByteArray(length)
                    input.readFully(frame)
                    if (inbound.trySend(frame).isFailure) throw IOException("inbound overflow")
                }
            } catch (_: IOException) {
                closeQuietly()
            }
        }
    }

    override suspend fun connect() = Unit

    override suspend fun send(frame: ByteArray) {
        if (state != PeerLink.State.READY) throw LinkClosedException("RFCOMM link closed")
        require(frame.size in 1..config.maxLinkFrameBytes)
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    DataOutputStream(socket.outputStream).apply {
                        writeInt(frame.size)
                        write(frame)
                        flush()
                    }
                } catch (e: IOException) {
                    closeQuietly()
                    throw LinkClosedException("RFCOMM write failed", e)
                }
            }
        }
    }

    override fun incomingFrames(): Flow<ByteArray> = inbound.receiveAsFlow()

    override suspend fun close() = closeQuietly()

    private fun closeQuietly() {
        if (mutableState.value == PeerLink.State.CLOSED) return
        mutableState.value = PeerLink.State.CLOSED
        inbound.close()
        runCatching { socket.close() }
    }
}

/** Listens for and opens v1 RFCOMM links to already-bonded phones (optional path). */
@SuppressLint("MissingPermission")
class RfcommLinkConnector(
    private val context: Context,
    private val scope: CoroutineScope,
    private val config: ProtocolConfig,
    private val metrics: MetricsSink,
    private val onLinkReady: (PeerLink) -> Unit,
) {
    @Volatile private var serverSocket: BluetoothServerSocket? = null

    fun listen() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val server = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                serverSocket = server
                while (true) {
                    val socket = server.accept()
                    metrics.record("rfcomm_accepted", emptyMap())
                    onLinkReady(RfcommPeerLink(socket, LinkRole.RESPONDER, config, scope))
                }
            } catch (_: IOException) {
                serverSocket = null
            }
        }
    }

    /** Bonded-device list for the lab UI; names only leave this class for display, never as identity. */
    fun bondedDevices(): List<Pair<String, String>> =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices
            ?.map { (it.name ?: "Unnamed device") to it.address } ?: emptyList()

    fun connect(address: String) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        scope.launch(Dispatchers.IO) {
            try {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                val socket = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket.connect()
                metrics.record("rfcomm_connected", emptyMap())
                onLinkReady(RfcommPeerLink(socket, LinkRole.INITIATOR, config, scope))
            } catch (e: IOException) {
                metrics.record("rfcomm_connect_failed", mapOf("cause" to e.message))
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    companion object {
        const val SERVICE_NAME = "iTantraV1Rfcomm"
        val SERVICE_UUID: UUID = UUID.fromString("7a1c0010-5e7d-4c9b-9f2a-1a7a47a10001")
    }
}
