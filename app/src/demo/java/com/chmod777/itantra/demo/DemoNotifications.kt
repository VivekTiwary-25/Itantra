package com.chmod777.itantra.demo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chmod777.itantra.R

/** Real Android system notifications for filmed incoming events (never drawn in-app). */
object DemoNotifications {

    private const val CHANNEL_MESSAGES = "itantra_messages"
    private const val CHANNEL_SOS = "itantra_sos"
    private const val GOLD = 0xFFF8AD3C.toInt()
    private const val SOS_RED = 0xFFE0604F.toInt()

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Messages from trusted contacts"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SOS, "SOS", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Help requests from people nearby and SOS replies"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 220, 140, 220)
            }
        )
    }

    fun permissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** False when the app or one of its channels is switched off in system settings. */
    fun enabledInSettings(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return false
        ensureChannels(context)
        return listOf(CHANNEL_MESSAGES, CHANNEL_SOS).all {
            (nm.getNotificationChannel(it)?.importance ?: NotificationManager.IMPORTANCE_NONE) !=
                NotificationManager.IMPORTANCE_NONE
        }
    }

    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationId(messageId: String): Int = messageId.hashCode()

    fun post(context: Context, message: DemoMessage) {
        if (!permissionGranted(context)) return
        ensureChannels(context)
        val notifId = notificationId(message.id)
        val sender = message.peer ?: "Someone nearby"
        val isSosRequest = message.mode == MessageMode.SOS

        val builder = NotificationCompat.Builder(context, if (message.mode.isSos) CHANNEL_SOS else CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_itantra)
            .setColor(if (message.mode.isSos) SOS_RED else GOLD)
            .setContentTitle(if (isSosRequest) "SOS · $sender" else sender)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setWhen(message.timestamp)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(activityIntent(context, DemoActivity.ACTION_OPEN_LOGS, message.id, notifId))

        when (message.mode) {
            MessageMode.SOS -> builder
                .addAction(0, "Accept", activityIntent(context, DemoActivity.ACTION_ACCEPT_SOS, message.id, notifId + 1))
                .addAction(0, "Decline", declineIntent(context, message.id, notifId + 2))
            MessageMode.SOS_REPLY -> builder.setSubText("SOS")
            MessageMode.URGENT -> builder.setSubText("Urgent")
            MessageMode.NORMAL -> Unit
        }

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; the Logs row still exists.
        }
    }

    fun cancel(context: Context, messageId: String) =
        NotificationManagerCompat.from(context).cancel(notificationId(messageId))

    fun cancelAll(context: Context) = NotificationManagerCompat.from(context).cancelAll()

    private fun activityIntent(context: Context, action: String, messageId: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, DemoActivity::class.java)
                .setAction(action)
                .putExtra(DemoEvents.EXTRA_MESSAGE_ID, messageId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun declineIntent(context: Context, messageId: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, DemoEventReceiver::class.java)
                .setAction(DemoEvents.ACTION_DECLINE_SOS)
                .putExtra(DemoEvents.EXTRA_MESSAGE_ID, messageId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
