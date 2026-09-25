package com.liquidglass.messages.data.mms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.liquidglass.messages.data.model.Attachment
import com.liquidglass.messages.data.model.Message
import com.liquidglass.messages.data.model.MessageStatus

/**
 * Reads and writes MMS in the system Telephony provider, the way the stock
 * Messaging app does: a row in content://mms, its parts in
 * content://mms/<id>/part (binary parts streamed into the provider's own file)
 * and its participants in content://mms/<id>/addr.
 *
 * Only the default SMS app may write here; every call degrades to a no-op /
 * empty result on SecurityException.
 */
class MmsStore(context: Context) {

    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver

    // =============================== Writing ===============================

    /**
     * Persists a downloaded M-Retrieve.conf into the inbox and returns its row
     * uri. [recipients] are the other participants (sender + other To/Cc,
     * excluding us) and decide the thread — one for 1:1, a group thread otherwise.
     */
    fun persistIncoming(
        retrieved: MmsPdu.Retrieved,
        recipients: Set<String>,
        subscriptionId: Int,
        contentLocation: String?,
    ): Uri? {
        return try {
        val threadId = Telephony.Threads.getOrCreateThreadId(appContext, recipients)
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, now / 1000)
            put(Telephony.Mms.DATE_SENT, if (retrieved.dateSeconds > 0) retrieved.dateSeconds else now / 1000)
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
            put(Telephony.Mms.READ, 0)
            put(Telephony.Mms.SEEN, 0)
            put(Telephony.Mms.MESSAGE_TYPE, MmsPdu.TYPE_RETRIEVE_CONF)
            put(Telephony.Mms.MMS_VERSION, 0x12)
            put(Telephony.Mms.CONTENT_TYPE, retrieved.contentType)
            retrieved.messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
            retrieved.transactionId?.let { put(Telephony.Mms.TRANSACTION_ID, it) }
            contentLocation?.let { put(Telephony.Mms.CONTENT_LOCATION, it) }
            retrieved.subject?.let { put(Telephony.Mms.SUBJECT, it); put(Telephony.Mms.SUBJECT_CHARSET, MmsPdu.CHARSET_UTF8) }
            put(Telephony.Mms.MESSAGE_SIZE, retrieved.parts.sumOf { it.data.size })
            put(Telephony.Mms.TEXT_ONLY, if (retrieved.parts.all { it.contentType.startsWith("text/") || it.contentType == MmsPdu.CT_SMIL }) 1 else 0)
            if (subscriptionId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
        }
        val uri = resolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        writeParts(id, retrieved.parts)
        retrieved.from?.let { writeAddress(id, it, ADDR_FROM) }
        retrieved.to.forEach { writeAddress(id, it, ADDR_TO) }
        retrieved.cc.forEach { writeAddress(id, it, ADDR_CC) }
        uri
    } catch (e: Exception) {
        Log.e(TAG, "Failed to persist incoming MMS", e)
        null
    }
    }

    /** Writes an outgoing MMS to the outbox (before handing it to the radio). */
    fun persistOutgoing(
        threadId: Long,
        recipients: List<String>,
        parts: List<MmsPdu.Part>,
        subscriptionId: Int,
        transactionId: String,
    ): Uri? {
        return try {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, now / 1000)
            put(Telephony.Mms.DATE_SENT, now / 1000)
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX)
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
            put(Telephony.Mms.MESSAGE_TYPE, MmsPdu.TYPE_SEND_REQ)
            put(Telephony.Mms.MMS_VERSION, 0x12)
            put(Telephony.Mms.CONTENT_TYPE, MmsPdu.CT_MULTIPART_RELATED)
            put(Telephony.Mms.TRANSACTION_ID, transactionId)
            put(Telephony.Mms.MESSAGE_SIZE, parts.sumOf { it.data.size })
            put(Telephony.Mms.TEXT_ONLY, if (parts.all { it.contentType.startsWith("text/") }) 1 else 0)
            if (subscriptionId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
        }
        val uri = resolver.insert(Telephony.Mms.Outbox.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        writeParts(id, parts)
        writeAddress(id, "insert-address-token", ADDR_FROM)
        recipients.forEach { writeAddress(id, it, ADDR_TO) }
        uri
    } catch (e: Exception) {
        Log.e(TAG, "Failed to persist outgoing MMS", e)
        null
    }
    }

    /** Moves a row between boxes (outbox → sent / failed). */
    fun setBox(uri: Uri, box: Int, messageId: String? = null) {
        runCatching {
            val v = ContentValues().apply {
                put(Telephony.Mms.MESSAGE_BOX, box)
                messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
                if (box == Telephony.Mms.MESSAGE_BOX_SENT) put(Telephony.Mms.DATE_SENT, System.currentTimeMillis() / 1000)
            }
            resolver.update(uri, v, null, null)
        }.onFailure { Log.w(TAG, "Couldn't update MMS box", it) }
    }

    /** True when this download was already stored (carriers re-send notifications). */
    fun alreadyStored(transactionId: String?, contentLocation: String?): Boolean = runCatching {
        val (sel, args) = when {
            !contentLocation.isNullOrBlank() -> "${Telephony.Mms.CONTENT_LOCATION} = ?" to arrayOf(contentLocation)
            !transactionId.isNullOrBlank() -> "${Telephony.Mms.TRANSACTION_ID} = ?" to arrayOf(transactionId)
            else -> return@runCatching false
        }
        resolver.query(Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms._ID), sel, args, null)?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    private fun writeParts(messageId: Long, parts: List<MmsPdu.Part>) {
        val partUri = Uri.parse("content://mms/$messageId/part")
        parts.forEach { p ->
            val v = ContentValues().apply {
                put(Telephony.Mms.Part.MSG_ID, messageId)
                put(Telephony.Mms.Part.CONTENT_TYPE, p.contentType)
                p.contentId?.let { put(Telephony.Mms.Part.CONTENT_ID, it) }
                p.contentLocation?.let { put(Telephony.Mms.Part.CONTENT_LOCATION, it) }
                p.name?.let { put(Telephony.Mms.Part.NAME, it); put(Telephony.Mms.Part.FILENAME, it) }
                val textual = p.contentType.startsWith("text/") || p.contentType == MmsPdu.CT_SMIL
                if (textual) {
                    put(Telephony.Mms.Part.CHARSET, MmsPdu.CHARSET_UTF8)
                    put(Telephony.Mms.Part.TEXT, p.text())
                }
            }
            val inserted = resolver.insert(partUri, v) ?: return@forEach
            val textual = p.contentType.startsWith("text/") || p.contentType == MmsPdu.CT_SMIL
            if (!textual) {
                // Binary data goes into the provider-owned file behind the part row.
                resolver.openOutputStream(inserted)?.use { it.write(p.data) }
            }
        }
    }

    private fun writeAddress(messageId: Long, address: String, type: Int) {
        runCatching {
            val v = ContentValues().apply {
                put(Telephony.Mms.Addr.ADDRESS, address)
                put(Telephony.Mms.Addr.TYPE, type)
                put(Telephony.Mms.Addr.CHARSET, MmsPdu.CHARSET_UTF8)
                put(Telephony.Mms.Addr.MSG_ID, messageId)
            }
            resolver.insert(Uri.parse("content://mms/$messageId/addr"), v)
        }
    }

    // =============================== Reading ===============================

    /** All displayable MMS of a thread, oldest first, as [Message]s. */
    fun readThread(threadId: Long): List<Message> {
        return try {
        val rows = ArrayList<Row>()
        resolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(
                Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.READ,
                Telephony.Mms.MESSAGE_TYPE, Telephony.Mms.SUBSCRIPTION_ID, Telephony.Mms.SUBJECT,
            ),
            "${Telephony.Mms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Mms.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getInt(4)
                // Undownloaded notification stubs (m_type 130) have nothing to show.
                if (type == MmsPdu.TYPE_NOTIFICATION_IND) continue
                rows += Row(
                    id = c.getLong(0),
                    dateMillis = c.getLong(1) * 1000,
                    box = c.getInt(2),
                    read = c.getInt(3) != 0,
                    subId = if (c.isNull(5)) -1 else c.getInt(5),
                    subject = c.getString(6),
                )
            }
        }
        if (rows.isEmpty()) return emptyList()
        val parts = readParts(rows.map { it.id })
        rows.map { row ->
            val ps = parts[row.id].orEmpty()
            val text = ps.filter { it.first == MmsPdu.CT_TEXT || it.first.startsWith("text/plain") }
                .mapNotNull { it.third }
                .joinToString("\n")
                .let { t -> if (!row.subject.isNullOrBlank() && t.isBlank()) row.subject else t }
            val attachments = ps.filter { it.first != MmsPdu.CT_SMIL && !it.first.startsWith("text/plain") && it.first != "application/smil" }
                .map { (ct, partId, _, name) -> Attachment(uri = "content://mms/part/$partId", mimeType = ct, name = name) }
            val outgoing = row.box != Telephony.Mms.MESSAGE_BOX_INBOX
            Message(
                id = row.id + Message.MMS_ID_OFFSET,
                threadId = threadId,
                address = if (outgoing) "" else senderOf(row.id).orEmpty(),
                body = text,
                timestamp = row.dateMillis,
                isOutgoing = outgoing,
                status = when (row.box) {
                    Telephony.Mms.MESSAGE_BOX_INBOX -> MessageStatus.RECEIVED
                    Telephony.Mms.MESSAGE_BOX_OUTBOX -> MessageStatus.SENDING
                    Telephony.Mms.MESSAGE_BOX_FAILED -> MessageStatus.FAILED
                    else -> MessageStatus.SENT
                },
                subscriptionId = row.subId,
                read = row.read,
                attachments = attachments,
                isMms = true,
            )
        }
    } catch (e: SecurityException) {
        emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read MMS for thread $threadId", e)
        emptyList()
    }
    }

    /** Newest MMS per thread (date millis, snippet text, outgoing, unread) for the list. */
    fun threadSummaries(): Map<Long, Summary> = try {
        val out = HashMap<Long, Summary>()
        val newestIds = HashMap<Long, Long>()
        resolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, Telephony.Mms.THREAD_ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.READ, Telephony.Mms.MESSAGE_TYPE),
            null, null, "${Telephony.Mms.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(5) == MmsPdu.TYPE_NOTIFICATION_IND) continue
                val thread = c.getLong(1)
                val unread = c.getInt(3) == Telephony.Mms.MESSAGE_BOX_INBOX && c.getInt(4) == 0
                val existing = out[thread]
                if (existing == null) {
                    newestIds[thread] = c.getLong(0)
                    out[thread] = Summary(c.getLong(2) * 1000, "", c.getInt(3) != Telephony.Mms.MESSAGE_BOX_INBOX, if (unread) 1 else 0, 1)
                } else {
                    out[thread] = existing.copy(unread = existing.unread + if (unread) 1 else 0, count = existing.count + 1)
                }
            }
        }
        val parts = readParts(newestIds.values.toList())
        newestIds.forEach { (thread, id) ->
            val ps = parts[id].orEmpty()
            val text = ps.firstOrNull { it.first.startsWith("text/plain") }?.third
            val media = ps.firstOrNull { it.first != MmsPdu.CT_SMIL && !it.first.startsWith("text/") }?.first
            val snippet = text?.takeIf { it.isNotBlank() } ?: when {
                media == null -> "MMS"
                media.startsWith("image/") -> "📷 Photo"
                media.startsWith("video/") -> "🎬 Video"
                media.startsWith("audio/") -> "🎵 Audio"
                media.contains("vcard", true) -> "👤 Contact"
                else -> "📎 Attachment"
            }
            out[thread] = out.getValue(thread).copy(snippet = snippet)
        }
        out
    } catch (e: Exception) {
        emptyMap()
    }

    data class Summary(val dateMillis: Long, val snippet: String, val outgoing: Boolean, val unread: Int, val count: Int)

    /** Raw part data for re-sending a failed MMS. */
    fun readRawParts(mmsId: Long): List<MmsPdu.Part> = try {
        val list = ArrayList<MmsPdu.Part>()
        resolver.query(
            Uri.parse("content://mms/part"),
            arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT, Telephony.Mms.Part.NAME),
            "${Telephony.Mms.Part.MSG_ID} = ?", arrayOf(mmsId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val ct = c.getString(1) ?: continue
                if (ct == MmsPdu.CT_SMIL) continue
                val data = if (ct.startsWith("text/")) {
                    (c.getString(2) ?: "").toByteArray(Charsets.UTF_8)
                } else {
                    resolver.openInputStream(Uri.parse("content://mms/part/${c.getLong(0)}"))?.use { it.readBytes() } ?: continue
                }
                list += MmsPdu.Part(ct, data, name = c.getString(3), charset = if (ct.startsWith("text/")) MmsPdu.CHARSET_UTF8 else 0)
            }
        }
        list
    } catch (e: Exception) {
        emptyList()
    }

    /** Recipients (To) of an outgoing MMS, for re-sending. */
    fun recipientsOf(mmsId: Long): List<String> = addresses(mmsId, ADDR_TO)

    private fun senderOf(mmsId: Long): String? = addresses(mmsId, ADDR_FROM).firstOrNull()

    private fun addresses(mmsId: Long, type: Int): List<String> = runCatching {
        resolver.query(
            Uri.parse("content://mms/$mmsId/addr"),
            arrayOf(Telephony.Mms.Addr.ADDRESS),
            "${Telephony.Mms.Addr.TYPE} = ?", arrayOf(type.toString()), null,
        )?.use { c ->
            buildList { while (c.moveToNext()) c.getString(0)?.takeIf { it != "insert-address-token" }?.let(::add) }
        } ?: emptyList()
    }.getOrDefault(emptyList())

    /** (contentType, partId, text, name) per message id. */
    private fun readParts(ids: List<Long>): Map<Long, List<PartRow>> {
        if (ids.isEmpty()) return emptyMap()
        val result = HashMap<Long, MutableList<PartRow>>()
        ids.chunked(400).forEach { chunk ->
            resolver.query(
                Uri.parse("content://mms/part"),
                arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.MSG_ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT, Telephony.Mms.Part.NAME, Telephony.Mms.Part.CONTENT_LOCATION),
                "${Telephony.Mms.Part.MSG_ID} IN (${chunk.joinToString(",")})", null, "${Telephony.Mms.Part.SEQ} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val mid = c.getLong(1)
                    result.getOrPut(mid) { ArrayList() } += PartRow(
                        c.getString(2) ?: "application/octet-stream",
                        c.getLong(0),
                        c.getString(3),
                        c.getString(4) ?: c.getString(5),
                    )
                }
            }
        }
        return result
    }

    private data class PartRow(val first: String, val second: Long, val third: String?, val fourth: String?)

    private data class Row(val id: Long, val dateMillis: Long, val box: Int, val read: Boolean, val subId: Int, val subject: String?)

    companion object {
        private const val TAG = "MmsStore"
        const val ADDR_FROM = 137
        const val ADDR_TO = 151
        const val ADDR_CC = 130
    }
}
