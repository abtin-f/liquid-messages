package com.liquidglass.messages.data.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.liquidglass.messages.data.receiver.SmsStatusReceiver
import com.liquidglass.messages.util.SmsGuard

/**
 * Thin wrapper over [SmsManager] that handles the API-level differences in how
 * the manager is obtained, splits long bodies into multipart, and attaches the
 * "sent" / "delivered" PendingIntents that drive status updates.
 *
 * The PendingIntents target the manifest-declared [SmsStatusReceiver] by explicit
 * component, so a carrier result that arrives after our process was killed still
 * updates the provider row (a runtime-registered receiver would silently miss it
 * and the bubble would sit on "Sending…" forever).
 */
class SmsSender(
    context: Context,
    /** Whether to request carrier delivery reports (user setting). */
    private val deliveryReports: () -> Boolean = { true },
) {

    private val appContext: Context = context.applicationContext

    /** Backstop against abnormal / runaway SMS sending; applied to every send. */
    private val guard = SmsGuard()

    /**
     * Resolves the correct [SmsManager] for [subId].
     *
     * - API 31+: fetch via the system service, then narrow to the subscription
     *   when one is specified.
     * - Below 31: use the deprecated static accessors (the only option there).
     */
    @Suppress("DEPRECATION")
    private fun smsManagerFor(subId: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // getSystemService(Class) is @Nullable (e.g. non-telephony devices,
            // which we support via uses-feature required="false"); fall back so a
            // null service degrades to a clear send failure instead of an NPE.
            val base = appContext.getSystemService(SmsManager::class.java)
                ?: SmsManager.getDefault()
            if (subId >= 0) base.createForSubscriptionId(subId) else base
        } else {
            if (subId >= 0) {
                SmsManager.getSmsManagerForSubscriptionId(subId)
            } else {
                SmsManager.getDefault()
            }
        }
    }

    /**
     * Sends [body] to [address], splitting into multipart automatically. The
     * "sent" and "delivered" PendingIntents carry [messageUri] so the status
     * receiver can update the originating provider row.
     *
     * @return false when the send was refused (guard / invalid address) or the
     *         platform threw before handing it to the radio. The caller must then
     *         mark the row failed — no status broadcast will ever arrive for it.
     */
    fun sendTextMessage(
        address: String,
        body: String,
        subscriptionId: Int,
        messageUri: Uri?
    ): Boolean {
        if (!guard.allow(address)) {
            Log.w(TAG, "Outgoing SMS blocked by guard (rate/validation)")
            return false
        }
        return try {
            val manager = smsManagerFor(subscriptionId)
            val parts = manager.divideMessage(body)
            val requestCode = requestCodeFor(messageUri)

            if (parts.size <= 1) {
                manager.sendTextMessage(
                    address,
                    null,
                    body,
                    statusIntent(SmsStatusReceiver.ACTION_SENT, messageUri, 0, 1, requestCode),
                    statusIntent(SmsStatusReceiver.ACTION_DELIVERED, messageUri, 0, 1, requestCode),
                )
            } else {
                val sent = ArrayList<PendingIntent?>(parts.size)
                val delivered = ArrayList<PendingIntent?>(parts.size)
                for (i in parts.indices) {
                    sent.add(statusIntent(SmsStatusReceiver.ACTION_SENT, messageUri, i, parts.size, requestCode))
                    delivered.add(statusIntent(SmsStatusReceiver.ACTION_DELIVERED, messageUri, i, parts.size, requestCode))
                }
                manager.sendMultipartTextMessage(address, null, parts, sent, delivered)
            }
            true
        } catch (t: Throwable) {
            // IllegalArgumentException (empty/invalid address), SecurityException
            // (lost the SMS role mid-send), or an OEM telephony crash.
            Log.e(TAG, "SmsManager rejected the send", t)
            false
        }
    }

    /**
     * Sends a control message (e.g. a reaction-sync token) WITHOUT inserting any
     * provider row, so it never appears as a chat bubble, and without status
     * tracking. Guarded like any other send. Returns false if blocked or failed.
     */
    fun sendRawMessage(address: String, body: String, subscriptionId: Int): Boolean {
        if (!guard.allow(address)) {
            Log.w(TAG, "Control SMS blocked by guard")
            return false
        }
        return try {
            val manager = smsManagerFor(subscriptionId)
            val parts = manager.divideMessage(body)
            if (parts.size <= 1) {
                manager.sendTextMessage(address, null, body, null, null)
            } else {
                manager.sendMultipartTextMessage(address, null, parts, null, null)
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Raw control send failed", t)
            false
        }
    }

    /**
     * Builds an explicit broadcast to [SmsStatusReceiver]. The part index is
     * encoded into the Intent's data so every part of every message gets a
     * distinct PendingIntent (Intent.filterEquals compares data), without having
     * to invent collision-free request codes.
     */
    private fun statusIntent(
        action: String,
        messageUri: Uri?,
        part: Int,
        partCount: Int,
        requestCode: Int,
    ): PendingIntent? {
        if (messageUri == null) return null
        if (action == SmsStatusReceiver.ACTION_DELIVERED && !deliveryReports()) return null
        val intent = Intent(action)
            .setClass(appContext, SmsStatusReceiver::class.java)
            .setData(
                messageUri.buildUpon()
                    .appendQueryParameter(SmsStatusReceiver.PARAM_PART, part.toString())
                    .build()
            )
            .putExtra(SmsStatusReceiver.EXTRA_PART_COUNT, partCount)
        // MUTABLE is required for DELIVERED: the radio fills in the status-report
        // PDU as an extra. The intent is explicit, so mutability is safe.
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            mutability or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun requestCodeFor(messageUri: Uri?): Int =
        (messageUri?.lastPathSegment?.toLongOrNull() ?: 0L).toInt()

    private companion object {
        const val TAG = "SmsSender"
    }
}
