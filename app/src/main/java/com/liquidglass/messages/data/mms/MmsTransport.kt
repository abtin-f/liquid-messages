package com.liquidglass.messages.data.mms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.FileProvider
import com.liquidglass.messages.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Moves MMS PDUs between the app and the carrier through the platform's
 * MmsService ([SmsManager.downloadMultimediaMessage] /
 * [SmsManager.sendMultimediaMessage]). The platform brings up the carrier's MMS
 * APN itself, so this works even with mobile data switched off on most
 * carriers — no APN tables or HTTP code in the app.
 *
 * PDUs travel through files in cache/mms exposed by a FileProvider, with
 * read/write granted to the MMS service process.
 */
class MmsTransport(context: Context) {

    private val appContext = context.applicationContext

    /** Starts downloading the message a WAP-push notification announced. */
    fun download(notification: MmsPdu.Notification, subscriptionId: Int) {
        val file = newPduFile("dl")
        val uri = shareFile(file, write = true)
        val intent = Intent(appContext, MmsDownloadedReceiver::class.java)
            .setAction(ACTION_DOWNLOADED)
            .putExtra(EXTRA_FILE, file.absolutePath)
            .putExtra(EXTRA_LOCATION, notification.contentLocation)
            .putExtra(EXTRA_TRANSACTION, notification.transactionId)
            .putExtra(EXTRA_SUB, subscriptionId)
        val pi = PendingIntent.getBroadcast(appContext, requestCode(), intent, mutableFlags())
        try {
            smsManager(subscriptionId).downloadMultimediaMessage(appContext, notification.contentLocation, uri, null, pi)
        } catch (e: Exception) {
            MmsLog.log("RECV downloadMultimediaMessage threw ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "downloadMultimediaMessage failed", e)
            file.delete()
        }
    }

    /**
     * Sends an already-persisted outgoing MMS. [rowUri] is its content://mms row,
     * moved to sent/failed by [MmsSentReceiver] when the carrier answers.
     */
    fun send(pdu: ByteArray, rowUri: Uri?, subscriptionId: Int): Boolean {
        val file = newPduFile("send")
        return try {
            file.writeBytes(pdu)
            val uri = shareFile(file, write = false)
            val intent = Intent(appContext, MmsSentReceiver::class.java)
                .setAction(ACTION_SENT)
                .putExtra(EXTRA_FILE, file.absolutePath)
                .putExtra(EXTRA_ROW, rowUri?.toString())
            val pi = PendingIntent.getBroadcast(appContext, requestCode(), intent, mutableFlags())
            smsManager(subscriptionId).sendMultimediaMessage(appContext, uri, null, null, pi)
            true
        } catch (e: Exception) {
            MmsLog.log("SEND sendMultimediaMessage threw ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "sendMultimediaMessage failed", e)
            file.delete()
            false
        }
    }

    /** Fire-and-forget M-NotifyResp.ind so the MMSC stops re-announcing the message. */
    fun acknowledge(transactionId: String, subscriptionId: Int) {
        if (transactionId.isBlank()) return
        val file = newPduFile("ack")
        runCatching {
            file.writeBytes(MmsPdu.encodeNotifyResp(transactionId))
            val intent = Intent(appContext, MmsSentReceiver::class.java)
                .setAction(ACTION_ACK)
                .putExtra(EXTRA_FILE, file.absolutePath)
            val pi = PendingIntent.getBroadcast(appContext, requestCode(), intent, mutableFlags())
            smsManager(subscriptionId).sendMultimediaMessage(appContext, shareFile(file, write = false), null, null, pi)
        }.onFailure { file.delete() }
    }

    /**
     * Largest MMS the carrier accepts, from carrier config (typical: 300 KB – 1 MB).
     * Used to shrink photos before sending.
     */
    @Suppress("DEPRECATION")
    fun maxMessageSize(subscriptionId: Int): Int = runCatching {
        val cfg = smsManager(subscriptionId).carrierConfigValues
        cfg.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, DEFAULT_MAX_SIZE)
    }.getOrDefault(DEFAULT_MAX_SIZE).takeIf { it > 50_000 } ?: DEFAULT_MAX_SIZE

    private fun newPduFile(prefix: String): File {
        val dir = File(appContext.cacheDir, "mms").apply { mkdirs() }
        return File(dir, "$prefix-${UUID.randomUUID()}.pdu")
    }

    private fun shareFile(file: File, write: Boolean): Uri {
        if (!file.exists()) file.createNewFile()
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}$AUTHORITY_SUFFIX", file)
        val flags = if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        else Intent.FLAG_GRANT_READ_URI_PERMISSION
        // The platform MMS service runs in one of these processes depending on OEM.
        MMS_SERVICE_PACKAGES.forEach { pkg -> runCatching { appContext.grantUriPermission(pkg, uri, flags) } }
        return uri
    }

    @Suppress("DEPRECATION")
    private fun smsManager(subId: Int): SmsManager {
        val base = if (Build.VERSION.SDK_INT >= 31) {
            appContext.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            SmsManager.getDefault()
        }
        return if (subId >= 0) {
            if (Build.VERSION.SDK_INT >= 31) base.createForSubscriptionId(subId) else SmsManager.getSmsManagerForSubscriptionId(subId)
        } else {
            base
        }
    }

    private fun mutableFlags(): Int =
        PendingIntent.FLAG_ONE_SHOT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0

    private fun requestCode(): Int = (System.nanoTime() and 0x7FFFFFFF).toInt()

    companion object {
        private const val TAG = "MmsTransport"
        const val AUTHORITY_SUFFIX = ".files"
        private const val DEFAULT_MAX_SIZE = 300 * 1024
        private val MMS_SERVICE_PACKAGES = listOf("com.android.mms.service", "com.android.phone")

        const val ACTION_DOWNLOADED = "com.liquidglass.messages.MMS_DOWNLOADED"
        const val ACTION_SENT = "com.liquidglass.messages.MMS_SENT"
        const val ACTION_ACK = "com.liquidglass.messages.MMS_ACK"
        const val EXTRA_FILE = "file"
        const val EXTRA_LOCATION = "location"
        const val EXTRA_TRANSACTION = "transaction"
        const val EXTRA_SUB = "sub"
        const val EXTRA_ROW = "row"

        fun newTransactionId(): String = "T" + UUID.randomUUID().toString().replace("-", "").take(20)
    }
}

/**
 * The retrieve-conf has landed in our cache file: parse it, store it in the
 * provider, notify, acknowledge the MMSC and clean up.
 */
class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MmsTransport.ACTION_DOWNLOADED) return
        val path = intent.getStringExtra(MmsTransport.EXTRA_FILE) ?: return
        val code = resultCode
        val ok = code == Activity.RESULT_OK
        MmsLog.log(
            "RECV download result=${MmsLog.resultName(code)} http=${intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, 0)} " +
                MmsLog.networkState(context),
        )
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val file = File(path)
            try {
                if (!ok) {
                    Log.w("MmsDownloaded", "MMS download failed (result $code)")
                    return@launch
                }
                val bytes = file.takeIf { it.exists() }?.readBytes() ?: return@launch
                val retrieved = MmsPdu.parseRetrieveConf(bytes) ?: run {
                    Log.w("MmsDownloaded", "Unparseable retrieve-conf (${bytes.size} bytes)")
                    return@launch
                }
                val sub = intent.getIntExtra(MmsTransport.EXTRA_SUB, -1)
                context.appContainer.mms.onRetrieved(
                    retrieved = retrieved,
                    contentLocation = intent.getStringExtra(MmsTransport.EXTRA_LOCATION),
                    transactionId = intent.getStringExtra(MmsTransport.EXTRA_TRANSACTION),
                    subscriptionId = sub,
                )
            } catch (t: Throwable) {
                Log.e("MmsDownloaded", "Failed to handle downloaded MMS", t)
            } finally {
                file.delete()
                pending.finish()
                scope.cancel()
            }
        }
    }
}

/** Result of an MMS send (or of an acknowledgement, which only needs cleanup). */
class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(MmsTransport.EXTRA_FILE)?.let { File(it).delete() }
        if (intent.action != MmsTransport.ACTION_SENT) return
        val row = intent.getStringExtra(MmsTransport.EXTRA_ROW)?.let(Uri::parse) ?: return
        val conf = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA)?.let(MmsPdu::parseSendConf)
        val ok = resultCode == Activity.RESULT_OK && (conf == null || conf.responseStatus == MmsPdu.RESPONSE_OK)
        MmsLog.log(
            "SEND result=${MmsLog.resultName(resultCode)} http=${intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, 0)} " +
                "mmscStatus=${conf?.responseStatus} row=$row ${MmsLog.networkState(context)}",
        )
        val box = if (ok) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_FAILED
        if (!ok) Log.w("MmsSent", "MMS send failed: result=$resultCode status=${conf?.responseStatus}")
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                context.appContainer.mmsStore.setBox(row, box, conf?.messageId)
                // Diagnostics: some OEM services remove rows they didn't write.
                val exists = runCatching {
                    context.contentResolver.query(row, arrayOf(Telephony.Mms._ID), null, null, null)?.use { it.count > 0 }
                }.getOrNull()
                MmsLog.log("SEND row after result: exists=$exists box=$box")
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }
}
