package com.liquidglass.messages.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.liquidglass.messages.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Receives [Telephony.Sms.Intents.SMS_DELIVER_ACTION] — delivered ONLY to the
 * default SMS app. As the default app we are responsible for persisting the
 * incoming message into the Telephony provider and surfacing a notification.
 *
 * Declared in the manifest as an exported receiver guarded by BROADCAST_SMS.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        // Reassemble the (possibly multipart) PDU into a single message.
        val parts: Array<SmsMessage> =
            Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        val first = parts.first()
        val address = first.originatingAddress ?: first.displayOriginatingAddress
        if (address.isNullOrBlank()) return

        val body = buildString {
            for (part in parts) {
                append(part.displayMessageBody ?: part.messageBody ?: "")
            }
        }
        // The provider records receipt time in millis; the PDU carries the
        // sender's timestamp which can be skewed, so prefer "now" if absent.
        val timestamp = first.timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        // Dual-SIM: the subscription id arrives as an int extra, but the key differs
        // across OEM ROMs, so try the common ones (-1 = unknown / system default).
        val subId = intent.getIntExtra(
            "subscription",
            intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)
        )

        // SMS_DELIVER must complete quickly; offload to IO and keep the broadcast
        // alive with goAsync() until persistence + notification finish.
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // Reaction-sync control messages are applied and suppressed here —
                // they must never become a visible inbox row or a notification.
                if (context.appContainer.reactionSyncManager.handleIncoming(address, body)) {
                    return@launch
                }
                val repo = context.appContainer.smsRepository
                repo.persistIncomingSms(
                    address = address,
                    body = body,
                    timestampMillis = timestamp,
                    subscriptionId = subId
                )

                // Build the notification off the persisted message + resolved contact.
                val contact = repo.resolveContact(address)
                val threadId = repo.getOrCreateThreadId(address)
                val message = com.liquidglass.messages.data.model.Message(
                    id = 0L,
                    threadId = threadId,
                    address = address,
                    // Notification shows the text without an "(Sent with … effect)" line.
                    body = com.liquidglass.messages.data.model.MessageText.visible(body),
                    timestamp = timestamp,
                    isOutgoing = false,
                    status = com.liquidglass.messages.data.model.MessageStatus.RECEIVED,
                    subscriptionId = subId,
                    read = false
                )
                context.appContainer.notificationHelper.notifyIncoming(message, contact)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to handle incoming SMS", t)
            } finally {
                // ALWAYS release the broadcast, even on failure, or the system
                // will eventually ANR/kill us. Cancel the scope too so the work is
                // bounded to the (timed) broadcast and can't outlive it.
                pending.finish()
                scope.cancel()
            }
        }
    }

    private companion object {
        const val TAG = "SmsDeliverReceiver"
    }
}
