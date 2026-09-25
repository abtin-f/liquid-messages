package com.liquidglass.messages.data.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Applies carrier results for outgoing SMS to their provider rows.
 *
 * - SENT: the radio accepted (or rejected) one part of the message. Any failed
 *   part fails the whole message, and a later "OK" for another part never
 *   overwrites that failure.
 * - DELIVERED: the recipient's handset returned a status report. The report's
 *   TP-Status decides between delivered, still-pending and failed — a report
 *   arriving is NOT by itself proof of delivery.
 *
 * Manifest-declared and non-exported; [com.liquidglass.messages.data.sms.SmsSender]
 * targets it with explicit PendingIntents.
 */
class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val data = intent.data ?: return
        // Strip the ?part= query we added to make each PendingIntent unique.
        val rowUri = data.buildUpon().clearQuery().build()
        val resultCode = resultCode
        val action = intent.action

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                when (action) {
                    ACTION_SENT -> onSent(context, rowUri, resultCode)
                    ACTION_DELIVERED -> onDelivered(context, rowUri, intent)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to apply SMS status", t)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    private fun onSent(context: Context, uri: Uri, resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            val values = ContentValues().apply {
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            // Never resurrect a message another part already failed.
            update(
                context, uri, values,
                "${Telephony.Sms.TYPE} != ?",
                arrayOf(Telephony.Sms.MESSAGE_TYPE_FAILED.toString()),
            )
        } else {
            Log.w(TAG, "Send failed with result code $resultCode")
            val values = ContentValues().apply {
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                put(Telephony.Sms.ERROR_CODE, resultCode)
            }
            update(context, uri, values, null, null)
        }
    }

    private fun onDelivered(context: Context, uri: Uri, intent: Intent) {
        val status = deliveryStatus(intent)
        val values = ContentValues().apply { put(Telephony.Sms.STATUS, status) }
        // A status report can only upgrade a sent row; ignore it for failed rows.
        update(
            context, uri, values,
            "${Telephony.Sms.TYPE} = ?",
            arrayOf(Telephony.Sms.MESSAGE_TYPE_SENT.toString()),
        )
    }

    /**
     * Maps the status-report PDU to a Telephony STATUS value.
     *
     * GSM (3GPP TS 23.040 TP-Status): 0x00–0x1F completed, 0x20–0x3F temporary
     * error (SC still trying), 0x40+ permanent failure.
     * CDMA (3GPP2): error class in the top byte — 0 ok, 2 temporary, 3 permanent.
     */
    private fun deliveryStatus(intent: Intent): Int {
        val pdu = intent.getByteArrayExtra("pdu") ?: return Telephony.Sms.STATUS_COMPLETE
        val format = intent.getStringExtra("format") ?: FORMAT_3GPP
        val raw = runCatching { SmsMessage.createFromPdu(pdu, format)?.status }.getOrNull()
            ?: return Telephony.Sms.STATUS_COMPLETE
        return if (format == FORMAT_3GPP2) {
            when ((raw ushr 24) and 0x03) {
                0 -> Telephony.Sms.STATUS_COMPLETE
                2 -> Telephony.Sms.STATUS_PENDING
                else -> Telephony.Sms.STATUS_FAILED
            }
        } else {
            when {
                raw < 0x20 -> Telephony.Sms.STATUS_COMPLETE
                raw < 0x40 -> Telephony.Sms.STATUS_PENDING
                else -> Telephony.Sms.STATUS_FAILED
            }
        }
    }

    private fun update(
        context: Context,
        uri: Uri,
        values: ContentValues,
        where: String?,
        args: Array<String>?,
    ) {
        try {
            context.contentResolver.update(uri, values, where, args)
        } catch (_: SecurityException) {
            // Lost the default-SMS role: we can no longer write, nothing to do.
        }
    }

    companion object {
        const val ACTION_SENT = "com.liquidglass.messages.SMS_SENT"
        const val ACTION_DELIVERED = "com.liquidglass.messages.SMS_DELIVERED"
        const val PARAM_PART = "part"
        const val EXTRA_PART_COUNT = "com.liquidglass.messages.extra.PART_COUNT"

        private const val TAG = "SmsStatusReceiver"
        private const val FORMAT_3GPP = "3gpp"
        private const val FORMAT_3GPP2 = "3gpp2"
    }
}
