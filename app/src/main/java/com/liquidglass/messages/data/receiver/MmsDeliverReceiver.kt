package com.liquidglass.messages.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.liquidglass.messages.appContainer

/**
 * Receives [Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION] for MMS — delivered
 * ONLY to the default SMS app (guarded by BROADCAST_WAP_PUSH). The push carries
 * an M-Notification.ind; the actual message is downloaded by the platform MMS
 * service and handled in [com.liquidglass.messages.data.mms.MmsDownloadedReceiver].
 */
class MmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        if (intent.type != MMS_MIME_TYPE) return
        val pdu = intent.getByteArrayExtra("data") ?: return
        // Dual-SIM: the subscription arrives under different keys on different ROMs.
        val sub = intent.getIntExtra("subscription", intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1))
        try {
            context.appContainer.mms.onWapPush(pdu, sub)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start MMS download", t)
        }
    }

    private companion object {
        const val TAG = "MmsDeliverReceiver"
        const val MMS_MIME_TYPE = "application/vnd.wap.mms-message"
    }
}
