package com.liquidglass.messages.data.sms

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.liquidglass.messages.data.model.Contact
import com.liquidglass.messages.data.model.Conversation
import com.liquidglass.messages.data.model.Message
import com.liquidglass.messages.data.model.MessageStatus
import com.liquidglass.messages.data.mms.MmsCoordinator
import com.liquidglass.messages.data.mms.MmsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [SmsRepository] backed directly by the system Telephony provider. No private
 * database is kept; the provider is the single source of truth and we observe it
 * with [ContentObserver]s, surfacing changes as cold [Flow]s.
 *
 * Every provider interaction runs on [ioDispatcher] and is wrapped to degrade
 * gracefully on missing permissions (SecurityException) or OEM provider quirks.
 */
class SmsRepositoryImpl(
    private val context: Context,
    private val sender: SmsSender,
    private val contactsHelper: ContactsHelper,
    private val ioDispatcher: CoroutineDispatcher,
    private val mmsStore: MmsStore,
    private val mms: MmsCoordinator,
) : SmsRepository {

    private val appContext: Context = context.applicationContext
    private val resolver get() = appContext.contentResolver

    // --- permissions -------------------------------------------------------

    private fun hasReadSms(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

    // --- conversations -----------------------------------------------------

    override fun observeConversations(): Flow<List<Conversation>> = callbackFlow {
        // Re-read and emit on every provider change; collisions are coalesced by
        // the single-capacity conflated channel so we never queue stale reads.
        suspend fun emitSnapshot() {
            trySend(getConversations())
        }

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // Hop back onto the flow's coroutine to do the suspending read.
                launch { emitSnapshot() }
            }
        }
        // Observe both the unified mms-sms surface and plain sms, since OEMs
        // notify on different uris.
        resolver.registerContentObserver(Telephony.MmsSms.CONTENT_URI, true, observer)
        resolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        resolver.registerContentObserver(CONVERSATIONS_URI, true, observer)

        // Prime the flow with an initial snapshot.
        emitSnapshot()

        awaitClose { resolver.unregisterContentObserver(observer) }
    }
        .conflate()
        .flowOn(ioDispatcher)

    override suspend fun getConversations(): List<Conversation> =
        withContext(ioDispatcher) {
            if (!hasReadSms()) return@withContext emptyList()
            try {
                readConversations()
            } catch (_: SecurityException) {
                emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

    /**
     * Builds the conversation list by grouping content://sms by thread_id. This
     * is more portable than the `?simple=true` thread summary, which several OEM
     * providers omit columns from, and lets us compute an accurate unread count.
     */
    private suspend fun readConversations(): List<Conversation> {
        data class Acc(
            var address: String,
            var snippet: String,
            var timestamp: Long,
            var messageCount: Int,
            var unreadCount: Int,
            var latestType: Int
        )

        val byThread = LinkedHashMap<Long, Acc>()

        val projection = arrayOf(
            SmsColumns.ID,
            SmsColumns.THREAD_ID,
            SmsColumns.ADDRESS,
            SmsColumns.BODY,
            SmsColumns.DATE,
            SmsColumns.TYPE,
            SmsColumns.READ
        )

        resolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${SmsColumns.DATE} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val threadId = cursor.getLongOrZero(SmsColumns.THREAD_ID)
                if (threadId == 0L) continue

                val address = cursor.getStringOrNull(SmsColumns.ADDRESS)?.trim().orEmpty()
                val body = cursor.getStringOrNull(SmsColumns.BODY).orEmpty()
                val date = cursor.getLongOrZero(SmsColumns.DATE)
                val type = cursor.getIntOrDefault(SmsColumns.TYPE, SmsColumns.MESSAGE_TYPE_INBOX)
                val read = cursor.getIntOrDefault(SmsColumns.READ, 1)

                val acc = byThread[threadId]
                if (acc == null) {
                    // First (newest, since DESC) row for this thread defines the snippet.
                    byThread[threadId] = Acc(
                        address = address,
                        snippet = body,
                        timestamp = date,
                        messageCount = 1,
                        unreadCount = if (type == SmsColumns.MESSAGE_TYPE_INBOX && read == 0) 1 else 0,
                        latestType = type
                    )
                } else {
                    acc.messageCount += 1
                    if (acc.address.isEmpty() && address.isNotEmpty()) acc.address = address
                    if (type == SmsColumns.MESSAGE_TYPE_INBOX && read == 0) acc.unreadCount += 1
                }
            }
        }

        // Fold in MMS: newer MMS take over the snippet; MMS-only (e.g. group)
        // threads get their participants from the threads table.
        val mmsSummaries = mmsStore.threadSummaries()
        val threadRecipients = readThreadRecipients()
        val outgoingByThread = HashMap<Long, Boolean>()
        byThread.forEach { (id, acc) -> outgoingByThread[id] = acc.latestType != SmsColumns.MESSAGE_TYPE_INBOX }
        for ((threadId, m) in mmsSummaries) {
            val acc = byThread[threadId]
            if (acc == null) {
                val address = threadRecipients[threadId]?.firstOrNull().orEmpty()
                byThread[threadId] = Acc(address, m.snippet, m.dateMillis, m.count, m.unread, 0)
                outgoingByThread[threadId] = m.outgoing
            } else {
                acc.messageCount += m.count
                acc.unreadCount += m.unread
                if (m.dateMillis > acc.timestamp) {
                    acc.snippet = m.snippet
                    acc.timestamp = m.dateMillis
                    outgoingByThread[threadId] = m.outgoing
                }
            }
        }

        // Resolve contact names (cached) and build the immutable model list.
        val result = ArrayList<Conversation>(byThread.size)
        for ((threadId, acc) in byThread) {
            val recipients = threadRecipients[threadId]?.takeIf { it.isNotEmpty() } ?: listOf(acc.address)
            val address = acc.address.ifBlank { recipients.first() }
            val contact: Contact = contactsHelper.resolveContact(address)
            val names = if (recipients.size > 1) {
                recipients.map { contactsHelper.resolveContact(it).displayName }
            } else {
                emptyList()
            }
            result.add(
                Conversation(
                    threadId = threadId,
                    address = address,
                    contactName = contact.name,
                    snippet = acc.snippet,
                    timestamp = acc.timestamp,
                    unreadCount = acc.unreadCount,
                    messageCount = acc.messageCount,
                    isOutgoingSnippet = outgoingByThread[threadId] ?: false,
                    photoUri = contact.photoUri,
                    recipients = recipients,
                    recipientNames = names,
                )
            )
        }

        result.sortByDescending { it.timestamp }
        return result
    }

    // --- messages ----------------------------------------------------------

    override fun observeMessages(threadId: Long): Flow<List<Message>> = callbackFlow {
        suspend fun emitSnapshot() {
            trySend(getMessages(threadId))
        }

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch { emitSnapshot() }
            }
        }
        resolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        resolver.registerContentObserver(Telephony.MmsSms.CONTENT_URI, true, observer)
        resolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, observer)

        emitSnapshot()

        awaitClose { resolver.unregisterContentObserver(observer) }
    }
        .conflate()
        .flowOn(ioDispatcher)

    override suspend fun getMessages(threadId: Long): List<Message> =
        withContext(ioDispatcher) {
            if (!hasReadSms()) return@withContext emptyList()
            try {
                // SMS and MMS live in separate tables; merge them into one timeline.
                (readMessages(threadId) + mmsStore.readThread(threadId)).sortedBy { it.timestamp }
            } catch (_: SecurityException) {
                emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

    private fun readMessages(threadId: Long): List<Message> {
        val projection = arrayOf(
            SmsColumns.ID,
            SmsColumns.THREAD_ID,
            SmsColumns.ADDRESS,
            SmsColumns.BODY,
            SmsColumns.DATE,
            SmsColumns.TYPE,
            SmsColumns.READ,
            SmsColumns.STATUS,
            SmsColumns.SUBSCRIPTION_ID
        )

        val messages = ArrayList<Message>()

        resolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${SmsColumns.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${SmsColumns.DATE} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLongOrZero(SmsColumns.ID)
                val address = cursor.getStringOrNull(SmsColumns.ADDRESS)?.trim().orEmpty()
                val body = cursor.getStringOrNull(SmsColumns.BODY).orEmpty()
                val date = cursor.getLongOrZero(SmsColumns.DATE)
                val type = cursor.getIntOrDefault(SmsColumns.TYPE, SmsColumns.MESSAGE_TYPE_INBOX)
                val readInt = cursor.getIntOrDefault(SmsColumns.READ, 1)
                val statusRaw = cursor.getIntOrDefault(SmsColumns.STATUS, SmsColumns.STATUS_NONE)
                val subId = cursor.getIntOrDefault(SmsColumns.SUBSCRIPTION_ID, -1)

                val isOutgoing = type != SmsColumns.MESSAGE_TYPE_INBOX
                val status = mapStatus(type, statusRaw)

                messages.add(
                    Message(
                        id = id,
                        threadId = threadId,
                        address = address,
                        body = body,
                        timestamp = date,
                        isOutgoing = isOutgoing,
                        status = status,
                        subscriptionId = normalizeSubscriptionId(subId),
                        read = readInt != 0
                    )
                )
            }
        }

        return messages
    }

    /**
     * Maps the provider's `type` + `status` columns onto our [MessageStatus].
     *
     * `type` carries the lifecycle bucket (inbox/sent/outbox/failed/queued) and,
     * for outgoing messages, `status` refines it (complete = delivered).
     */
    private fun mapStatus(type: Int, status: Int): MessageStatus {
        return when (type) {
            SmsColumns.MESSAGE_TYPE_INBOX -> MessageStatus.RECEIVED
            SmsColumns.MESSAGE_TYPE_OUTBOX,
            SmsColumns.MESSAGE_TYPE_QUEUED -> MessageStatus.SENDING
            SmsColumns.MESSAGE_TYPE_FAILED -> MessageStatus.FAILED
            SmsColumns.MESSAGE_TYPE_SENT -> when (status) {
                SmsColumns.STATUS_COMPLETE -> MessageStatus.DELIVERED
                SmsColumns.STATUS_FAILED -> MessageStatus.FAILED
                SmsColumns.STATUS_PENDING -> MessageStatus.SENT
                else -> MessageStatus.SENT // STATUS_NONE / unknown
            }
            else -> MessageStatus.NONE
        }
    }

    // --- sending -----------------------------------------------------------

    override suspend fun sendMessage(
        address: String,
        body: String,
        subscriptionId: Int
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val threadId = getOrCreateThreadId(address)

            // Insert an outbox row first so the UI reflects the message immediately.
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                // Only pin the thread id when we resolved a valid one; otherwise
                // let the provider assign one from the address on insert.
                if (threadId > 0) {
                    put(Telephony.Sms.THREAD_ID, threadId)
                }
                if (subscriptionId >= 0) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
                }
            }

            // Insert into content://sms; the OUTBOX type governs the bucket so the
            // UI shows the message immediately as "sending".
            val insertedUri: Uri? = try {
                resolver.insert(Telephony.Sms.CONTENT_URI, values)
            } catch (_: SecurityException) {
                // Not the default SMS app: still attempt to send, just without a row.
                null
            }

            // Hand off to the platform. The "sent" PendingIntent ALWAYS fires, so
            // SmsSender's internal receiver is the authoritative updater that flips
            // the OUTBOX row to SENT (or FAILED) based on the real carrier result —
            // we deliberately do not optimistically overwrite the status here, to
            // avoid racing that broadcast.
            val handedOff = sender.sendTextMessage(address, body, subscriptionId, insertedUri)
            if (!handedOff) {
                // Refused before reaching the radio: no status broadcast will ever
                // come, so fail the row now instead of leaving it "Sending…".
                insertedUri?.let { markFailed(it) }
                return@withContext Result.failure(IllegalStateException("SMS was not sent"))
            }

            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resendMessage(messageId: Long): Result<Unit> = withContext(ioDispatcher) {
        if (Message.isMmsId(messageId)) {
            return@withContext if (mms.resend(messageId - Message.MMS_ID_OFFSET, -1)) Result.success(Unit)
            else Result.failure(IllegalStateException("MMS resend failed"))
        }
        val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId)
        try {
            val row = resolver.query(
                uri,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.SUBSCRIPTION_ID),
                null, null, null,
            )?.use { c ->
                if (!c.moveToFirst()) null
                else Triple(c.getString(0), c.getString(1), if (c.isNull(2)) -1 else c.getInt(2))
            } ?: return@withContext Result.failure(IllegalArgumentException("No such message"))
            val (address, body, subId) = row
            if (address.isNullOrBlank() || body.isNullOrEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Message has no address/body"))
            }

            // Back to the outbox, stamped "now" so it moves to the bottom like iMessage.
            val values = ContentValues().apply {
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_NONE)
                put(Telephony.Sms.ERROR_CODE, 0)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
            }
            resolver.update(uri, values, null, null)

            if (!sender.sendTextMessage(address, body, subId, uri)) {
                markFailed(uri)
                return@withContext Result.failure(IllegalStateException("SMS was not sent"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun markFailed(uri: Uri) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
            }
            resolver.update(uri, values, null, null)
        } catch (_: Exception) {
            // Can't write (not default app) — nothing more we can do.
        }
    }

    // --- incoming persistence ---------------------------------------------

    override suspend fun persistIncomingSms(
        address: String,
        body: String,
        timestampMillis: Long,
        subscriptionId: Int
    ): Long = withContext(ioDispatcher) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                // Like AOSP Messaging: DATE is when WE received it (keeps the thread
                // ordered even if the sender's clock is wrong), DATE_SENT is the
                // service-centre timestamp from the PDU.
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.DATE_SENT, timestampMillis)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                if (subscriptionId >= 0) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
                }
            }
            val uri = resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            uri?.let { ContentUris.parseId(it) } ?: -1L
        } catch (_: SecurityException) {
            -1L
        } catch (_: Exception) {
            -1L
        }
    }

    // --- MMS --------------------------------------------------------------

    override suspend fun sendMms(
        recipients: List<String>,
        text: String,
        attachments: List<Uri>,
        subscriptionId: Int,
    ): Result<Unit> = withContext(ioDispatcher) {
        if (mms.send(recipients, text, attachments, subscriptionId)) Result.success(Unit)
        else Result.failure(IllegalStateException("MMS was not sent"))
    }

    override suspend fun getRecipients(threadId: Long): List<String> = withContext(ioDispatcher) {
        readThreadRecipients()[threadId].orEmpty()
    }

    /**
     * thread id → participant addresses, from the threads table
     * (`recipient_ids` → content://mms-sms/canonical-addresses).
     */
    private fun readThreadRecipients(): Map<Long, List<String>> = try {
        val canonical = HashMap<Long, String>()
        resolver.query(Uri.parse("content://mms-sms/canonical-addresses"), null, null, null, null)?.use { c ->
            val idCol = c.getColumnIndex("_id")
            val addrCol = c.getColumnIndex("address")
            if (idCol >= 0 && addrCol >= 0) {
                while (c.moveToNext()) canonical[c.getLong(idCol)] = c.getString(addrCol) ?: ""
            }
        }
        val out = HashMap<Long, List<String>>()
        resolver.query(
            Uri.parse("content://mms-sms/conversations?simple=true"),
            arrayOf(SmsColumns.THREADS_ID, SmsColumns.THREADS_RECIPIENT_IDS),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val ids = c.getString(1)?.split(' ')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()
                out[c.getLong(0)] = ids.mapNotNull { canonical[it]?.takeIf(String::isNotBlank) }
            }
        }
        out
    } catch (_: Exception) {
        emptyMap()
    }

    // --- mutations ---------------------------------------------------------

    override suspend fun markThreadRead(threadId: Long) {
        withContext(ioDispatcher) {
            try {
                val values = ContentValues().apply {
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                }
                resolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${SmsColumns.THREAD_ID} = ? AND ${SmsColumns.READ} = 0",
                    arrayOf(threadId.toString())
                )
                val mmsValues = ContentValues().apply {
                    put(Telephony.Mms.READ, 1)
                    put(Telephony.Mms.SEEN, 1)
                }
                resolver.update(
                    Telephony.Mms.CONTENT_URI,
                    mmsValues,
                    "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0",
                    arrayOf(threadId.toString())
                )
            } catch (_: SecurityException) {
                // Not default app / no write permission — ignore.
            } catch (_: Exception) {
                // Ignore provider hiccups.
            }
        }
    }

    override suspend fun deleteThread(threadId: Long) {
        withContext(ioDispatcher) {
            try {
                // Preferred: dedicated conversations endpoint deletes MMS+SMS.
                val convoUri = ContentUris.withAppendedId(CONVERSATIONS_URI, threadId)
                val deleted = resolver.delete(convoUri, null, null)
                if (deleted <= 0) {
                    // Fallback: delete all sms rows in the thread directly.
                    resolver.delete(
                        Telephony.Sms.CONTENT_URI,
                        "${SmsColumns.THREAD_ID} = ?",
                        arrayOf(threadId.toString())
                    )
                }
                Unit
            } catch (_: SecurityException) {
                // Ignore.
            } catch (_: Exception) {
                // Fallback path on any failure of the conversations endpoint.
                try {
                    resolver.delete(
                        Telephony.Sms.CONTENT_URI,
                        "${SmsColumns.THREAD_ID} = ?",
                        arrayOf(threadId.toString())
                    )
                } catch (_: Exception) {
                    // Give up silently.
                }
            }
        }
    }

    override suspend fun deleteMessage(messageId: Long) {
        withContext(ioDispatcher) {
            try {
                val uri = if (Message.isMmsId(messageId)) {
                    ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, messageId - Message.MMS_ID_OFFSET)
                } else {
                    ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId)
                }
                resolver.delete(uri, null, null)
            } catch (_: SecurityException) {
                // Ignore.
            } catch (_: Exception) {
                // Ignore.
            }
        }
    }

    override suspend fun getOrCreateThreadId(address: String): Long =
        withContext(ioDispatcher) {
            try {
                Telephony.Threads.getOrCreateThreadId(appContext, address)
            } catch (_: Exception) {
                // Some providers throw for malformed addresses; -1 signals failure.
                -1L
            }
        }

    // --- contacts (delegated) ---------------------------------------------

    override suspend fun resolveContact(address: String): Contact =
        contactsHelper.resolveContact(address)

    override suspend fun searchContacts(query: String): List<Contact> =
        contactsHelper.searchContacts(query)

    private companion object {
        /** content://mms-sms/conversations — deletes/queries across MMS+SMS. */
        val CONVERSATIONS_URI: Uri =
            Uri.parse("content://mms-sms/conversations")
    }
}
