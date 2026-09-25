package com.liquidglass.messages.data.model

/**
 * Delivery / lifecycle state of a single message, mirrored from the Telephony
 * provider's status + type columns and our own in-flight tracking.
 */
enum class MessageStatus {
    /** Outgoing message handed to SmsManager, not yet confirmed sent. */
    SENDING,
    /** Outgoing message accepted by the carrier. */
    SENT,
    /** Outgoing message confirmed delivered to the recipient handset. */
    DELIVERED,
    /** Outgoing message read by the recipient (rarely available over SMS). */
    READ,
    /** Outgoing message failed to send. */
    FAILED,
    /** Incoming message. */
    RECEIVED,
    /** Unknown / not applicable. */
    NONE
}

/**
 * A single SMS/MMS message belonging to a conversation thread.
 *
 * Backed directly by the system Telephony provider — this app does not keep a
 * private copy of the inbox.
 *
 * @param id          row id in content://sms
 * @param threadId    conversation thread id
 * @param address     phone number of the other party (E.164 or raw)
 * @param body        message text
 * @param timestamp   epoch millis the message was sent/received (the `date` column)
 * @param isOutgoing  true when sent by this device
 * @param status      delivery / lifecycle state
 * @param subscriptionId  SIM subscription id used (dual-SIM); -1 when unknown
 * @param read        whether the message has been read locally
 */
data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val status: MessageStatus = if (isOutgoing) MessageStatus.SENT else MessageStatus.RECEIVED,
    val subscriptionId: Int = -1,
    val read: Boolean = true,
    /** Media / files carried by an MMS (empty for SMS). */
    val attachments: List<Attachment> = emptyList(),
    /** True for rows from content://mms. Their [id] is offset by [MMS_ID_OFFSET]. */
    val isMms: Boolean = false,
) {
    /** Row id in its own table (content://sms or content://mms). */
    val providerId: Long get() = if (isMms) id - MMS_ID_OFFSET else id

    companion object {
        /**
         * SMS and MMS ids come from different tables and overlap; MMS ids are
         * shifted into their own range so every message has one unique key.
         */
        const val MMS_ID_OFFSET: Long = 1L shl 40

        fun isMmsId(id: Long) = id >= MMS_ID_OFFSET
    }
}

/**
 * One MMS part the UI can show: an image/video/audio/file, addressed by its
 * content:// uri (content://mms/part/<id> or a local file for drafts).
 */
data class Attachment(
    val uri: String,
    val mimeType: String,
    val name: String? = null,
    val sizeBytes: Long = 0,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    val isContactCard: Boolean get() = mimeType.contains("vcard", ignoreCase = true)
}
