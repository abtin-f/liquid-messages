package com.liquidglass.messages.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Central definitions and [PendingIntent] builders for the actions attached to
 * incoming-message notifications (inline Reply and Mark-as-read).
 *
 * Both actions are delivered to the manifest-declared, non-exported
 * [NotificationActionReceiver] via `PendingIntent.getBroadcast`. We deliberately
 * do NOT route them through [com.liquidglass.messages.data.service.HeadlessSmsSendService]:
 * that service is guarded by `SEND_RESPOND_VIA_MESSAGE`, a permission this app
 * does not hold, so a self-sent `getService` would be rejected with
 * `SecurityException`. An explicit broadcast to our own `exported="false"`
 * receiver is delivered reliably even from the background, with no permission.
 *
 *  - **Reply**  -> [replyPendingIntent]: a MUTABLE broadcast carrying a
 *    [androidx.core.app.RemoteInput]; the receiver reads the typed text + target
 *    address and sends an SMS.
 *  - **Mark as read** -> [markReadPendingIntent]: an IMMUTABLE broadcast the
 *    receiver interprets as "mark this thread read and dismiss its notification".
 */
object NotificationActions {

    /* ----------------------------- Action ids ----------------------------- */

    /** Service intent action: send an inline reply pulled from RemoteInput. */
    const val ACTION_REPLY = "com.liquidglass.messages.action.REPLY"

    /** Service intent action: mark the originating thread as read. */
    const val ACTION_MARK_READ = "com.liquidglass.messages.action.MARK_READ"

    /* ------------------------------- Extras ------------------------------- */

    /** RemoteInput result key carrying the typed reply text. */
    const val KEY_REPLY_TEXT = "com.liquidglass.messages.extra.REPLY_TEXT"

    /** Recipient phone number / thread address (String). */
    const val EXTRA_ADDRESS = "com.liquidglass.messages.extra.ADDRESS"

    /** Telephony thread id (Long). */
    const val EXTRA_THREAD_ID = "com.liquidglass.messages.extra.THREAD_ID"

    /** Notification id to dismiss once the action is handled (Int). */
    const val EXTRA_NOTIFICATION_ID = "com.liquidglass.messages.extra.NOTIFICATION_ID"

    /**
     * Base for distinct request codes so the Reply and Mark-as-read intents of
     * the *same* thread never collide while remaining stable across rebuilds.
     */
    private const val REQUEST_REPLY_BASE = 0x1000_0000
    private const val REQUEST_MARK_READ_BASE = 0x2000_0000

    /** Mutable flag for the RemoteInput reply; immutable for everything else. */
    private val replyFlags: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    private val immutableFlags: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    /**
     * PendingIntent for the inline Reply action. Targets the manifest-declared,
     * non-exported [NotificationActionReceiver]; the wrapping RemoteInput supplies
     * [KEY_REPLY_TEXT]. Must be MUTABLE so the system can inject the typed text.
     */
    fun replyPendingIntent(
        context: Context,
        address: String,
        threadId: Long,
        notificationId: Int
    ): PendingIntent {
        val intent = actionIntent(context, ACTION_REPLY, address, threadId, notificationId)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_REPLY_BASE + notificationId,
            intent,
            replyFlags
        )
    }

    /**
     * PendingIntent for the Mark-as-read action. Targets [NotificationActionReceiver]
     * with [ACTION_MARK_READ]; immutable, since nothing needs to be injected.
     */
    fun markReadPendingIntent(
        context: Context,
        address: String,
        threadId: Long,
        notificationId: Int
    ): PendingIntent {
        val intent = actionIntent(context, ACTION_MARK_READ, address, threadId, notificationId)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_MARK_READ_BASE + notificationId,
            intent,
            immutableFlags
        )
    }

    /**
     * PendingIntent that opens the conversation when the notification body is
     * tapped. Routed through the declared `MainActivity` via its `smsto:` deep
     * link, so it works without referencing the activity class directly.
     */
    fun openThreadPendingIntent(
        context: Context,
        address: String,
        notificationId: Int
    ): PendingIntent {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$address")).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_ADDRESS, address)
        }
        return PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            immutableFlags
        )
    }

    private fun actionIntent(
        context: Context,
        action: String,
        address: String,
        threadId: Long,
        notificationId: Int
    ): Intent = Intent(context, NotificationActionReceiver::class.java).apply {
        this.action = action
        // A distinct data uri keeps PendingIntents for different threads/actions
        // from being treated as "equal" and collapsed by the framework.
        data = Uri.parse("liquidmsg://$action/$threadId")
        putExtra(EXTRA_ADDRESS, address)
        putExtra(EXTRA_THREAD_ID, threadId)
        putExtra(EXTRA_NOTIFICATION_ID, notificationId)
    }
}
