package com.liquidglass.messages.data.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.liquidglass.messages.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** A message the user asked to "Send Later". */
data class ScheduledMessage(
    val id: Long,
    val address: String,
    val body: String,
    val sendAt: Long,
    val subscriptionId: Int = -1,
)

/**
 * iOS 18-style "Send Later": persists scheduled messages and arms an alarm for
 * each. The alarm fires [ScheduledSendReceiver], which sends and forgets it.
 *
 * Alarms are exact when the user allows it (Settings › Alarms & reminders),
 * otherwise Doze-aware inexact (still delivered, possibly a few minutes late).
 * Alarms don't survive a reboot, so [rescheduleAll] runs on boot and app start.
 */
class ScheduledMessageStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("scheduled_messages", Context.MODE_PRIVATE)
    private val lock = Any()

    private val _messages = MutableStateFlow(load())
    val messages: StateFlow<List<ScheduledMessage>> = _messages.asStateFlow()

    fun schedule(address: String, body: String, sendAt: Long, subscriptionId: Int): ScheduledMessage {
        val msg = ScheduledMessage(
            id = System.currentTimeMillis(),
            address = address,
            body = body,
            sendAt = sendAt,
            subscriptionId = subscriptionId,
        )
        synchronized(lock) { save(_messages.value + msg) }
        arm(msg)
        return msg
    }

    fun cancel(id: Long) {
        synchronized(lock) { save(_messages.value.filterNot { it.id == id }) }
        alarmManager()?.cancel(pendingIntent(id))
    }

    fun find(id: Long): ScheduledMessage? = _messages.value.firstOrNull { it.id == id }

    /** Re-arms every pending message (after boot / app update / app start). */
    fun rescheduleAll() = _messages.value.forEach(::arm)

    private fun arm(msg: ScheduledMessage) {
        val am = alarmManager() ?: return
        val pi = pendingIntent(msg.id)
        val at = msg.sendAt.coerceAtLeast(System.currentTimeMillis() + 1_000)
        try {
            val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exactAllowed) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun alarmManager() = appContext.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        (id % Int.MAX_VALUE).toInt(),
        Intent(appContext, ScheduledSendReceiver::class.java)
            .setAction(ACTION_SEND_SCHEDULED)
            .putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun save(list: List<ScheduledMessage>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("address", it.address).put("body", it.body)
                    .put("sendAt", it.sendAt).put("sub", it.subscriptionId),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
        _messages.value = list
    }

    private fun load(): List<ScheduledMessage> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ScheduledMessage(o.getLong("id"), o.getString("address"), o.getString("body"), o.getLong("sendAt"), o.optInt("sub", -1))
        }
    }.getOrDefault(emptyList())

    companion object {
        const val ACTION_SEND_SCHEDULED = "com.liquidglass.messages.SEND_SCHEDULED"
        const val EXTRA_ID = "com.liquidglass.messages.extra.SCHEDULED_ID"
        private const val KEY = "items"
    }
}

/** Fires when a scheduled message is due: sends it like a normal message. */
class ScheduledSendReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScheduledMessageStore.ACTION_SEND_SCHEDULED) return
        val id = intent.getLongExtra(ScheduledMessageStore.EXTRA_ID, -1L)
        val store = context.appContainer.scheduledMessages
        val msg = store.find(id) ?: return

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // Removed first so a crash mid-send can't make it send twice.
                store.cancel(msg.id)
                context.appContainer.smsRepository.sendMessage(msg.address, msg.body, msg.subscriptionId)
            } catch (t: Throwable) {
                Log.e("ScheduledSend", "Scheduled send failed", t)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }
}

/** Alarms are wiped by reboots and app updates — put them back. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> context.appContainer.scheduledMessages.rescheduleAll()
        }
    }
}
