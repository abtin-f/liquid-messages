package com.liquidglass.messages.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.liquidglass.messages.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Manifest-declared, non-exported receiver for the incoming-message
 * notification's inline **Reply** and **Mark-as-read** actions.
 *
 * ## Why a manifest BroadcastReceiver (and not the headless service)
 * - The RESPOND_VIA_MESSAGE service ([com.liquidglass.messages.data.service.HeadlessSmsSendService])
 *   is guarded by `SEND_RESPOND_VIA_MESSAGE` — a signature/privileged permission
 *   this ordinary app does not (and cannot) hold. A self-sent
 *   `PendingIntent.getService` into it is rejected with `SecurityException`, so
 *   the actions would silently never run.
 * - A runtime-registered receiver would only be alive while a Compose screen is
 *   foregrounded — useless for a notification tapped from the shade.
 * - A manifest receiver declared `android:exported="false"` reliably receives our
 *   explicit `PendingIntent.getBroadcast` even when the app is in the background,
 *   with no special permission. This is the standard quick-reply pattern.
 *
 * The matching `PendingIntent` builders live in [NotificationActions].
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != NotificationActions.ACTION_REPLY &&
            action != NotificationActions.ACTION_MARK_READ
        ) {
            return
        }

        val appContext = context.applicationContext
        val address = intent.getStringExtra(NotificationActions.EXTRA_ADDRESS)
        val threadId = intent.getLongExtra(NotificationActions.EXTRA_THREAD_ID, -1L)
        val notificationId = intent.getIntExtra(NotificationActions.EXTRA_NOTIFICATION_ID, -1)
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationActions.KEY_REPLY_TEXT)
            ?.toString()

        // Keep the broadcast alive while the suspending provider work runs.
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val repo = appContext.appContainer.smsRepository
                when (action) {
                    NotificationActions.ACTION_REPLY -> {
                        if (!address.isNullOrBlank() && !replyText.isNullOrBlank()) {
                            repo.sendMessage(address, replyText)
                            if (threadId > 0) repo.markThreadRead(threadId)
                        }
                    }
                    NotificationActions.ACTION_MARK_READ -> {
                        if (threadId > 0) repo.markThreadRead(threadId)
                    }
                }
                if (notificationId >= 0) {
                    NotificationManagerCompat.from(appContext).cancel(notificationId)
                }
            } catch (_: Throwable) {
                // Never let a notification action crash the app.
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }
}
