package com.chmod777.itantra.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chmod777.itantra.MainActivity
import com.chmod777.itantra.R
import com.chmod777.itantra.transport.BluetoothPermissions
import kotlinx.coroutines.launch

/**
 * User-started foreground service for Emergency mode (spec §44). Owns BLE
 * advertising/scanning, the GATT server, links, sessions and routing triggers.
 * The notification makes emergency operation obvious; stopping it tears down
 * all radio work. Process death is survived only by the durable DTN store.
 */
class EmergencyModeService : Service() {
    private var bluetoothReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NetworkingRuntime.init(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: if (NetworkingRuntime.wasEmergencyEnabled()) ACTION_START else ACTION_STOP) {
            ACTION_STOP -> {
                NetworkingRuntime.stopEmergency(userRequested = true)
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (!BluetoothPermissions.areGranted(this)) {
                    NetworkingRuntime.reportError("Bluetooth permissions are missing. Open iTantra and grant them.")
                    stopSelf()
                    return START_NOT_STICKY
                }
                try {
                    startInForeground()
                } catch (e: Exception) {
                    // Android 12+ can refuse background foreground-service starts; report, don't crash.
                    NetworkingRuntime.reportError("Android refused to start Emergency mode: ${e.javaClass.simpleName}")
                    stopSelf()
                    return START_NOT_STICKY
                }
                registerBluetoothReceiver()
                NetworkingRuntime.scope.launch { NetworkingRuntime.startEmergency(this@EmergencyModeService) }
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerBluetoothReceiver() {
        if (bluetoothReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_OFF -> {
                        NetworkingRuntime.stopEmergency(userRequested = false)
                        NetworkingRuntime.reportError("Bluetooth turned off. Emergency radio work paused.")
                    }
                    BluetoothAdapter.STATE_ON -> NetworkingRuntime.scope.launch { NetworkingRuntime.startEmergency(this@EmergencyModeService) }
                }
            }
        }
        ContextCompat.registerReceiver(this, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        bluetoothReceiver = receiver
    }

    override fun onDestroy() {
        bluetoothReceiver?.let { runCatching { unregisterReceiver(it) } }
        bluetoothReceiver = null
        NetworkingRuntime.stopEmergency(userRequested = false)
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Emergency mode", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while iTantra is relaying messages over Bluetooth"
            },
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, EmergencyModeService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("iTantra Emergency mode is ON")
            .setContentText("Finding nearby phones and carrying encrypted messages over Bluetooth.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        const val ACTION_START = "com.chmod777.itantra.action.EMERGENCY_START"
        const val ACTION_STOP = "com.chmod777.itantra.action.EMERGENCY_STOP"
        private const val CHANNEL_ID = "emergency_mode"
        private const val NOTIFICATION_ID = 7001

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, EmergencyModeService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, EmergencyModeService::class.java).setAction(ACTION_STOP))
        }
    }
}
