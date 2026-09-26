package com.liquidglass.messages.data.sms

import com.liquidglass.messages.data.model.Contact
import com.liquidglass.messages.data.model.Conversation
import com.liquidglass.messages.data.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for SMS data, backed by the system Telephony provider
 * (content://sms, content://mms-sms/conversations). All reads are exposed as
 * cold [Flow]s that re-emit when the provider changes (via a ContentObserver).
 *
 * The concrete implementation is [SmsRepositoryImpl]; obtain the singleton from
 * `(context.applicationContext as MessagesApplication).container.smsRepository`.
 */
interface SmsRepository {

    /** Emits the conversation list, newest first, and re-emits on any change. */
    fun observeConversations(): Flow<List<Conversation>>

    /** The inbox as last shown (disk cache), for an instant first frame; null if none. */
    fun cachedConversations(): List<Conversation>? = null

    /** Emits messages in [threadId] oldest-first, and re-emits on any change. */
    fun observeMessages(threadId: Long): Flow<List<Message>>

    /** One-shot read of all conversations. */
    suspend fun getConversations(): List<Conversation>

    /** One-shot read of the messages in a thread. */
    suspend fun getMessages(threadId: Long): List<Message>

    /**
     * Sends an SMS to [address] and persists it to the Sent box. Splits long
     * bodies into multipart automatically. [subscriptionId] selects the SIM on
     * dual-SIM devices (-1 = system default).
     *
     * @return success once handed to the platform; delivery is reported
     *         asynchronously and reflected via [observeMessages].
     */
    suspend fun sendMessage(
        address: String,
        body: String,
        subscriptionId: Int = -1
    ): Result<Unit>

    /**
     * Sends one MMS (group text and/or attachments given as content uris) to
     * [recipients]. Returns failure when it couldn't be prepared or handed off.
     */
    suspend fun sendMms(
        recipients: List<String>,
        text: String,
        attachments: List<android.net.Uri>,
        subscriptionId: Int = -1,
    ): Result<Unit>

    /** Every other participant of a thread (more than one = group). */
    suspend fun getRecipients(threadId: Long): List<String>

    /**
     * Re-sends a failed outgoing message in place (same row, moved to "now").
     * Mirrors iMessage's "Not Delivered — tap to try again".
     */
    suspend fun resendMessage(messageId: Long): Result<Unit>

    /**
     * Persists an incoming message into the inbox. Called by the SMS_DELIVER
     * receiver, since the default SMS app is responsible for writing to the
     * provider. Returns the new row id.
     */
    suspend fun persistIncomingSms(
        address: String,
        body: String,
        timestampMillis: Long,
        subscriptionId: Int = -1
    ): Long

    /** Marks every message in [threadId] as read. */
    suspend fun markThreadRead(threadId: Long)

    /** iOS "Mark as Unread": flags the newest incoming message unread again. */
    suspend fun markThreadUnread(threadId: Long) {}

    /** iOS "Keep Messages": deletes every SMS/MMS older than [cutoffMillis]; returns rows removed. */
    suspend fun deleteOlderThan(cutoffMillis: Long): Int = 0

    /** Recently Deleted: brings a deleted conversation back. */
    suspend fun restoreThread(threadId: Long) {}

    /** Recently Deleted: brings a deleted message back. */
    suspend fun restoreMessage(messageId: Long) {}

    /** Recently Deleted › Delete: removes a deleted conversation's messages for good. */
    suspend fun purgeThread(threadId: Long) {}

    /** Recently Deleted › Delete: removes a deleted message for good. */
    suspend fun purgeMessage(messageId: Long) {}

    /** Permanently removes everything that has been in Recently Deleted for 30 days. */
    suspend fun purgeExpired() {}

    /** Moves an entire conversation to Recently Deleted. */
    suspend fun deleteThread(threadId: Long)

    /** Moves a single message to Recently Deleted. */
    suspend fun deleteMessage(messageId: Long)

    /** Returns (creating if needed) the thread id for a recipient address. */
    suspend fun getOrCreateThreadId(address: String): Long

    /** Resolves contact name/photo for a phone number (cached). */
    suspend fun resolveContact(address: String): Contact

    /** Searches device contacts by name or number for the compose screen. */
    suspend fun searchContacts(query: String): List<Contact>
}
