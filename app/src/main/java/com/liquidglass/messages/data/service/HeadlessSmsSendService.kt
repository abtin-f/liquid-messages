package com.liquidglass.messages.data.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import com.liquidglass.messages.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Headless service that performs SMS sends without UI.
 *
 * Handles **RESPOND_VIA_MESSAGE** ([TelephonyManager.ACTION_RESPOND_VIA_MESSAGE]) —
 * the "quick reply" the in-call screen fires when the user declines a call with a
 * canned text. This is the fourth component required to be eligible as the default
 * SMS app (declared in the manifest, guarded by SEND_RESPOND_VIA_MESSAGE).
 *
 * The incoming-message notification's inline Reply / Mark-as-read actions are
 * handled separately by the non-guarded
 * [com.liquidglass.messages.notification.NotificationActionReceiver].
 *
 * All work runs on an IO coroutine; the service stops itself per start id once the
 * work completes, and returns [START_NOT_STICKY] so it is never auto-restarted.
 */
class HeadlessSmsSendService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Not a bound service. */
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when (intent.action) {
            TelephonyManager.ACTION_RESPOND_VIA_MESSAGE,
            Intent.ACTION_SENDTO,
            Intent.ACTION_SEND ->
                handleRespondViaMessage(intent, startId)

            else -> {
                Log.w(TAG, "Unhandled action: ${intent.action}")
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Handles RESPOND_VIA_MESSAGE: extract recipients from the intent data
     * (`sms:`/`smsto:`/`mms:`/`mmsto:` uri, possibly multiple, comma/`;`
     * separated) and the body from [Intent.EXTRA_TEXT] or the respond-via-message
     * extra, then send to each recipient.
     */
    private fun handleRespondViaMessage(intent: Intent, startId: Int) {
        val recipients = extractRecipients(intent)
        val body = extractBody(intent)

        if (recipients.isEmpty() || body.isBlank()) {
            Log.w(TAG, "RESPOND_VIA_MESSAGE missing recipients or body; ignoring")
            stopSelf(startId)
            return
        }

        scope.launch {
            try {
                val repo = applicationContext.appContainer.smsRepository
                for (recipient in recipients) {
                    repo.sendMessage(address = recipient, body = body)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "RESPOND_VIA_MESSAGE send failed", t)
            } finally {
                stopSelf(startId)
            }
        }
    }

    /** Pulls recipient numbers out of the intent's `sms:`/`smsto:` style uri. */
    private fun extractRecipients(intent: Intent): List<String> {
        val data = intent.data ?: return emptyList()
        // scheme-specific part for "smsto:+15551234,+15555678" is the number list.
        val raw = data.schemeSpecificPart ?: data.toString()
        return raw
            .split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Reads the message body. The platform respond-via-message intent carries the
     * canned text under [Intent.EXTRA_TEXT]; the legacy key is also checked.
     */
    private fun extractBody(intent: Intent): String {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { if (it.isNotEmpty()) return it }
        // Legacy / vendor key used by some dialers for respond-via-message.
        intent.getStringExtra(EXTRA_RESPOND_VIA_MESSAGE_TEXT)
            ?.let { if (it.isNotEmpty()) return it }
        return ""
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "HeadlessSmsSendService"

        /**
         * Some in-call UIs put the canned reply under this extra instead of
         * EXTRA_TEXT. Mirrors the platform's internal key.
         */
        const val EXTRA_RESPOND_VIA_MESSAGE_TEXT =
            "android.intent.extra.respond_via_message_text"
    }
}
