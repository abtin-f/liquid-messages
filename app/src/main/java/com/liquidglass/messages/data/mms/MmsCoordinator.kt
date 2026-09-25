package com.liquidglass.messages.data.mms

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Telephony
import android.util.Log
import android.webkit.MimeTypeMap
import com.liquidglass.messages.data.model.Message
import com.liquidglass.messages.data.model.MessageStatus
import com.liquidglass.messages.data.sms.ContactsHelper
import com.liquidglass.messages.notification.NotificationHelper
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The MMS pipeline, end to end:
 *
 *  receive: WAP push → [onWapPush] → download (platform) → [onRetrieved] →
 *           store in content://mms → notification → M-NotifyResp.ind
 *  send:    [send] → build parts (photos shrunk to the carrier limit) →
 *           store in outbox → M-Send.req (platform) → sent / failed
 */
class MmsCoordinator(
    context: Context,
    private val store: MmsStore,
    private val transport: MmsTransport,
    private val contacts: ContactsHelper,
    private val notifications: NotificationHelper,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("mms", Context.MODE_PRIVATE)

    // =============================== Receiving ===============================

    /** Handles the WAP-push PDU delivered to the default SMS app. */
    fun onWapPush(pdu: ByteArray, subscriptionId: Int) {
        val n = MmsPdu.parseNotification(pdu) ?: run {
            Log.w(TAG, "WAP push is not an MMS notification (${pdu.size} bytes)")
            return
        }
        if (store.alreadyStored(n.transactionId, n.contentLocation)) {
            Log.i(TAG, "Duplicate MMS notification ignored")
            transport.acknowledge(n.transactionId, subscriptionId)
            return
        }
        transport.download(n, subscriptionId)
    }

    suspend fun onRetrieved(
        retrieved: MmsPdu.Retrieved,
        contentLocation: String?,
        transactionId: String?,
        subscriptionId: Int,
    ) {
        if (store.alreadyStored(retrieved.transactionId, contentLocation)) return
        val recipients = participantsOf(retrieved)
        val uri = store.persistIncoming(retrieved, recipients, subscriptionId, contentLocation) ?: return

        val from = retrieved.from ?: recipients.firstOrNull().orEmpty()
        val text = retrieved.parts.firstOrNull { it.contentType.startsWith("text/plain") }?.text()
        val media = retrieved.parts.firstOrNull { it.contentType != MmsPdu.CT_SMIL && !it.contentType.startsWith("text/") }
        val preview = text?.takeIf { it.isNotBlank() } ?: when {
            media == null -> retrieved.subject ?: "MMS"
            media.contentType.startsWith("image/") -> "📷 Photo"
            media.contentType.startsWith("video/") -> "🎬 Video"
            media.contentType.startsWith("audio/") -> "🎵 Audio"
            else -> "📎 Attachment"
        }
        val threadId = runCatching { Telephony.Threads.getOrCreateThreadId(appContext, recipients) }.getOrDefault(-1L)
        notifications.notifyIncoming(
            Message(
                id = ContentUris.parseId(uri) + Message.MMS_ID_OFFSET,
                threadId = threadId,
                address = from,
                body = preview,
                timestamp = System.currentTimeMillis(),
                isOutgoing = false,
                status = MessageStatus.RECEIVED,
                subscriptionId = subscriptionId,
                read = false,
                isMms = true,
            ),
            contacts.resolveContact(from),
        )
        transport.acknowledge(transactionId ?: retrieved.transactionId.orEmpty(), subscriptionId)
    }

    /**
     * The conversation's other participants. A single To means a 1:1 message —
     * and that To is *our* number, which we remember so group messages can
     * leave us out of their participant list.
     */
    internal fun participantsOf(r: MmsPdu.Retrieved): Set<String> {
        val recipients = (r.to + r.cc).filter { it.isNotBlank() }
        val from = r.from?.takeIf { it.isNotBlank() }
        if (recipients.size <= 1) {
            recipients.firstOrNull()?.let(::rememberOwnNumber)
            return setOfNotNull(from).ifEmpty { recipients.toSet() }
        }
        val me = ownNumbers()
        val others = recipients.filterNot { addr -> me.any { samePhone(it, addr) } }
        return (listOfNotNull(from) + others).toCollection(LinkedHashSet())
    }

    private fun rememberOwnNumber(number: String) {
        val set = ownNumbers() + number
        prefs.edit().putStringSet(KEY_OWN, set.toList().takeLast(4).toSet()).apply()
    }

    private fun ownNumbers(): Set<String> = prefs.getStringSet(KEY_OWN, emptySet()) ?: emptySet()

    // =============================== Sending ===============================

    /**
     * Sends [text] and/or [attachments] (content uris) to [recipients] as one MMS.
     * Returns false when nothing could be prepared or handed to the platform.
     */
    fun send(recipients: List<String>, text: String, attachments: List<Uri>, subscriptionId: Int): Boolean {
        if (recipients.isEmpty()) return false
        val budget = (transport.maxMessageSize(subscriptionId) * 0.9).toInt()
        val parts = ArrayList<MmsPdu.Part>()
        if (text.isNotBlank()) {
            parts += MmsPdu.Part(MmsPdu.CT_TEXT, text.toByteArray(Charsets.UTF_8), charset = MmsPdu.CHARSET_UTF8)
        }
        val textBytes = parts.sumOf { it.data.size }
        val perAttachment = if (attachments.isEmpty()) 0 else (budget - textBytes - 2_000) / attachments.size
        attachments.forEach { uri -> readAttachment(uri, perAttachment)?.let(parts::add) }
        if (parts.isEmpty()) return false

        val threadId = runCatching { Telephony.Threads.getOrCreateThreadId(appContext, recipients.toSet()) }.getOrDefault(-1L)
        val tx = MmsTransport.newTransactionId()
        val row = store.persistOutgoing(threadId, recipients, parts, subscriptionId, tx)
        val pdu = MmsPdu.encodeSendReq(recipients, parts, tx)
        val handed = transport.send(pdu, row, subscriptionId)
        if (!handed && row != null) store.setBox(row, Telephony.Mms.MESSAGE_BOX_FAILED)
        return handed
    }

    /** Re-sends a failed MMS with the same parts and recipients. */
    fun resend(mmsId: Long, subscriptionId: Int): Boolean {
        val parts = store.readRawParts(mmsId)
        val to = store.recipientsOf(mmsId)
        if (parts.isEmpty() || to.isEmpty()) return false
        val row = Uri.parse("content://mms/$mmsId")
        store.setBox(row, Telephony.Mms.MESSAGE_BOX_OUTBOX)
        val handed = transport.send(MmsPdu.encodeSendReq(to, parts, MmsTransport.newTransactionId()), row, subscriptionId)
        if (!handed) store.setBox(row, Telephony.Mms.MESSAGE_BOX_FAILED)
        return handed
    }

    /** Loads an attachment; photos are re-encoded as JPEG small enough for MMS. */
    private fun readAttachment(uri: Uri, maxBytes: Int): MmsPdu.Part? = runCatching {
        val resolver = appContext.contentResolver
        val type = resolver.getType(uri) ?: MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri.toString())) ?: "application/octet-stream"
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        if (type.startsWith("image/") && type != "image/gif") {
            val jpeg = compressImage(uri, maxBytes) ?: return@runCatching null
            MmsPdu.Part("image/jpeg", jpeg, name = name?.substringBeforeLast('.')?.plus(".jpg"))
        } else {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            if (bytes.size > maxBytes * 1.5) {
                Log.w(TAG, "Attachment ${bytes.size}B exceeds carrier limit; sending anyway")
            }
            MmsPdu.Part(type, bytes, name = name)
        }
    }.onFailure { Log.e(TAG, "Couldn't read attachment $uri", it) }.getOrNull()

    /** Downscales + re-compresses until the JPEG fits [maxBytes]. */
    private fun compressImage(uri: Uri, maxBytes: Int): ByteArray? {
        val bitmap = decodeBitmap(uri, maxEdge = 1600) ?: return null
        var bmp = bitmap
        var quality = 85
        repeat(12) {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= maxBytes || (quality <= 40 && max(bmp.width, bmp.height) <= 480)) return out.toByteArray()
            if (quality > 55) {
                quality -= 10
            } else {
                val scale = sqrt(maxBytes.toDouble() / out.size()).coerceIn(0.5, 0.9)
                bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
            }
        }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 40, it) }.toByteArray()
    }

    private fun decodeBitmap(uri: Uri, maxEdge: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(appContext.contentResolver, uri)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = maxEdge.toFloat() / max(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) sample *= 2
            appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }.getOrNull()

    companion object {
        private const val TAG = "MmsCoordinator"
        private const val KEY_OWN = "own_numbers"

        /** Compares phone numbers by their last 9 digits (ignores +98 / 0 prefixes). */
        internal fun samePhone(a: String, b: String): Boolean {
            val da = a.filter(Char::isDigit)
            val db = b.filter(Char::isDigit)
            if (da.isEmpty() || db.isEmpty()) return a.equals(b, ignoreCase = true)
            return da.takeLast(9) == db.takeLast(9)
        }
    }
}
