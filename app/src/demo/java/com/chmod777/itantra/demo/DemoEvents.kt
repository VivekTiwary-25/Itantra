package com.chmod777.itantra.demo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.UUID

/**
 * Director-console timers for filmed incoming events.
 *
 * A 3–10 s timer must survive the actor pressing Home and opening Instagram, so
 * the primary mechanism is an exact AlarmManager alarm delivered to
 * [DemoEventReceiver] (works even if the process is killed). A main-looper
 * backup covers devices that deny exact alarms while the process is alive.
 * Both race to [DemoStore.claimArmed]; only the first delivers.
 */
object DemoEvents {

    const val ACTION_FIRE = "com.chmod777.itantra.demo.FIRE"
    const val ACTION_DECLINE_SOS = "com.chmod777.itantra.demo.DECLINE_SOS"
    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_MESSAGE_ID = "message_id"

    private const val ALARM_REQUEST_CODE = 7001
    private const val TRIGGER_DEBOUNCE_MS = 1500L

    private val handler = Handler(Looper.getMainLooper())
    private var backup: Runnable? = null
    private var lastTriggerAt = 0L

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Replaces any previously armed event (one pending take at a time). */
    fun arm(context: Context, spec: IncomingSpec): ArmedEvent {
        val app = context.applicationContext
        cancel(app, clearShownNotifications = false)
        val delayMs = spec.delaySeconds.coerceAtLeast(0) * 1000L
        val event = ArmedEvent(UUID.randomUUID().toString(), System.currentTimeMillis() + delayMs, spec)
        DemoStore.setArmed(event)

        val alarms = app.getSystemService(AlarmManager::class.java)
        val pi = firePendingIntent(app, event.id)
        val at = SystemClock.elapsedRealtime() + delayMs
        if (canScheduleExact(app)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        }
        backup = Runnable { fire(app, event.id) }.also { handler.postDelayed(it, delayMs) }
        return event
    }

    /** Immediate delivery. Accidental double taps within 1.5 s are ignored. */
    fun triggerNow(context: Context, spec: IncomingSpec): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerAt < TRIGGER_DEBOUNCE_MS) return false
        lastTriggerAt = now
        deliver(context.applicationContext, UUID.randomUUID().toString(), spec)
        return true
    }

    fun cancel(context: Context, clearShownNotifications: Boolean) {
        val app = context.applicationContext
        backup?.let(handler::removeCallbacks)
        backup = null
        DemoStore.armed.value?.let { app.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(app, it.id)) }
        DemoStore.setArmed(null)
        if (clearShownNotifications) DemoNotifications.cancelAll(app)
    }

    fun fire(context: Context, eventId: String) {
        val event = DemoStore.claimArmed(eventId) ?: return
        backup = null
        deliver(context.applicationContext, event.id, event.spec)
    }

    /** Logs first, then the system notification, so tapping it always finds the row. */
    private fun deliver(context: Context, id: String, spec: IncomingSpec) {
        val message = DemoStore.addIncoming(spec, id)
        if (spec.notify) DemoNotifications.post(context, message)
    }

    private fun firePendingIntent(context: Context, eventId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, DemoEventReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_EVENT_ID, eventId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

class DemoEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DemoStore.init(context)
        when (intent.action) {
            DemoEvents.ACTION_FIRE ->
                intent.getStringExtra(DemoEvents.EXTRA_EVENT_ID)?.let { DemoEvents.fire(context, it) }
            DemoEvents.ACTION_DECLINE_SOS -> {
                val id = intent.getStringExtra(DemoEvents.EXTRA_MESSAGE_ID) ?: return
                DemoStore.setSosResponse(id, SosResponse.DECLINED)
                DemoNotifications.cancel(context, id)
            }
        }
    }
}
