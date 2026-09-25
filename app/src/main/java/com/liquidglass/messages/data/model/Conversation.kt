package com.liquidglass.messages.data.model

/**
 * A conversation thread shown in the conversation list.
 *
 * @param threadId        Telephony thread id
 * @param address         primary phone number of the thread
 * @param contactName     resolved contact display name, or null if unknown
 * @param snippet         preview text of the most recent message
 * @param timestamp       epoch millis of the most recent message
 * @param unreadCount     number of unread incoming messages
 * @param messageCount    total messages in the thread
 * @param isOutgoingSnippet  true when the latest message was sent by this device
 * @param photoUri        contact photo content uri string, or null
 */
data class Conversation(
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val snippet: String,
    val timestamp: Long,
    val unreadCount: Int = 0,
    val messageCount: Int = 0,
    val isOutgoingSnippet: Boolean = false,
    val photoUri: String? = null,
    /** Every other participant; more than one means a group (MMS) conversation. */
    val recipients: List<String> = listOf(address),
    /** Display names for [recipients] (same order); used for group titles. */
    val recipientNames: List<String> = emptyList(),
) {
    val isGroup: Boolean get() = recipients.size > 1

    /** Name to show in the UI: contact name(s) when known, else the raw number(s). */
    val displayName: String
        get() = if (isGroup) {
            (recipientNames.ifEmpty { recipients }).joinToString(", ") { it.substringBefore(' ') }
        } else {
            contactName?.takeIf { it.isNotBlank() } ?: address
        }

    val hasUnread: Boolean get() = unreadCount > 0
}
