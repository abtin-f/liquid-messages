package com.liquidglass.messages.ui.preview

import com.liquidglass.messages.data.model.Contact
import com.liquidglass.messages.data.model.Conversation
import com.liquidglass.messages.data.model.Message
import com.liquidglass.messages.data.model.MessageStatus

/**
 * Deterministic sample data for Compose @Preview functions and design reviews.
 *
 * Every timestamp is a FIXED literal epoch-millis constant — never a live clock
 * — so previews render identically across runs and machines. The base instant
 * [BASE] is Fri, 2024-05-17 16:41:00 UTC; all other stamps are expressed as
 * offsets from it so the relative spacing (minutes/hours/days apart) reads
 * naturally in the date separators and conversation-row stamps.
 *
 * This object has no Android or platform dependencies, so it is safe to use from
 * the Android Studio preview pane and from unit tests alike.
 */
object SampleData {

    /* --------------------------- Time constants --------------------------- */

    /** Fixed reference instant: 2024-05-17 16:41:00 UTC, in epoch millis. */
    const val BASE: Long = 1_715_964_060_000L

    private const val MINUTE: Long = 60_000L
    private const val HOUR: Long = 60L * MINUTE
    private const val DAY: Long = 24L * HOUR

    /* ------------------------------ Contacts ------------------------------ */

    /**
     * A small roster of recognizable sample contacts. The third entry has no
     * resolved name on purpose, so previews exercise the "unknown number" path
     * where [Contact.displayName] falls back to the raw number.
     */
    val sampleContacts: List<Contact> = listOf(
        Contact(number = "+15551234567", name = "Ada Lovelace"),
        Contact(number = "+15557654321", name = "Alan Turing"),
        Contact(number = "+15550009999", name = null),
        Contact(number = "+15553344556", name = "Grace Hopper"),
        Contact(number = "+15552223333", name = "Katherine Johnson"),
    )

    /** Convenience: the primary contact used by the single-thread chat preview. */
    val primaryContact: Contact = sampleContacts.first()

    /* ---------------------------- Conversations --------------------------- */

    /**
     * A realistic, newest-first conversation list mixing read/unread threads, an
     * outgoing-snippet thread ("You: …"), an unknown-number OTP thread, and a
     * couple of older threads to exercise the relative-time formatting.
     */
    val sampleConversations: List<Conversation> = listOf(
        Conversation(
            threadId = 1L,
            address = "+15551234567",
            contactName = "Ada Lovelace",
            snippet = "Did you get a chance to look at the analytical engine notes?",
            timestamp = BASE - 2L * MINUTE,
            unreadCount = 2,
            messageCount = 14,
            isOutgoingSnippet = false,
        ),
        Conversation(
            threadId = 2L,
            address = "+15557654321",
            contactName = "Alan Turing",
            snippet = "Sounds good — talk soon.",
            timestamp = BASE - 1L * HOUR,
            unreadCount = 0,
            messageCount = 8,
            isOutgoingSnippet = true,
        ),
        Conversation(
            threadId = 3L,
            address = "+15550009999",
            contactName = null,
            snippet = "Your verification code is 482913. It expires in 10 minutes.",
            timestamp = BASE - 3L * HOUR,
            unreadCount = 1,
            messageCount = 1,
            isOutgoingSnippet = false,
        ),
        Conversation(
            threadId = 4L,
            address = "+15553344556",
            contactName = "Grace Hopper",
            snippet = "Shipped the compiler fix. Nanoseconds saved.",
            timestamp = BASE - 1L * DAY,
            unreadCount = 0,
            messageCount = 22,
            isOutgoingSnippet = false,
        ),
        Conversation(
            threadId = 5L,
            address = "+15552223333",
            contactName = "Katherine Johnson",
            snippet = "You: I double-checked the trajectory math, we're clear.",
            timestamp = BASE - 4L * DAY,
            unreadCount = 0,
            messageCount = 31,
            isOutgoingSnippet = true,
        ),
    )

    /** Convenience accessors for the two row-state previews (read / unread). */
    val unreadConversation: Conversation = sampleConversations.first { it.hasUnread }
    val readConversation: Conversation = sampleConversations.first { !it.hasUnread }

    /* ------------------------------ Messages ------------------------------ */

    /**
     * A believable iMessage-style back-and-forth for [threadId]. Messages are
     * returned oldest-first (matching the repository contract), span a couple of
     * day boundaries so date separators render, group consecutive same-sender
     * runs, and include varied delivery statuses on the outgoing side
     * (DELIVERED / READ / SENT / SENDING / FAILED).
     *
     * Unknown thread ids fall back to the default scripted thread so any preview
     * id still renders content.
     */
    fun sampleMessages(threadId: Long): List<Message> = when (threadId) {
        2L -> turingThread()
        3L -> otpThread()
        else -> defaultThread(threadId)
    }

    /** The default rich thread (used by thread 1 and any unknown id). */
    private fun defaultThread(threadId: Long): List<Message> {
        val address = "+15551234567"
        var id = 100L

        fun msg(
            body: String,
            outgoing: Boolean,
            offset: Long,
            status: MessageStatus = if (outgoing) MessageStatus.DELIVERED else MessageStatus.RECEIVED,
        ): Message = Message(
            id = id++,
            threadId = threadId,
            address = address,
            body = body,
            timestamp = BASE + offset,
            isOutgoing = outgoing,
            status = status,
            read = true,
        )

        return listOf(
            // ---- Two days ago: opening exchange ----
            msg("Hey! Are we still on for the demo tomorrow?", outgoing = false, offset = -2L * DAY - 3L * HOUR),
            msg("Absolutely. I'll have the build ready by 9.", outgoing = true, offset = -2L * DAY - 3L * HOUR + 4L * MINUTE, status = MessageStatus.READ),
            msg("Perfect 🙌", outgoing = false, offset = -2L * DAY - 3L * HOUR + 6L * MINUTE),

            // ---- Yesterday: a grouped run from each side ----
            msg("Quick update — the analytical engine notes are scanned.", outgoing = false, offset = -1L * DAY - 2L * HOUR),
            msg("There are 47 pages, mostly diagrams.", outgoing = false, offset = -1L * DAY - 2L * HOUR + 1L * MINUTE),
            msg("Nice, that's more than I expected.", outgoing = true, offset = -1L * DAY - 2L * HOUR + 5L * MINUTE, status = MessageStatus.READ),
            msg("Can you send the first ten?", outgoing = true, offset = -1L * DAY - 2L * HOUR + 5L * MINUTE + 20_000L, status = MessageStatus.READ),
            msg("On it.", outgoing = false, offset = -1L * DAY - 2L * HOUR + 7L * MINUTE),

            // ---- Today: the live tail of the conversation ----
            msg("Sent! Did they come through okay?", outgoing = false, offset = -25L * MINUTE),
            msg("Got them, thank you. The diagrams are gorgeous.", outgoing = true, offset = -20L * MINUTE, status = MessageStatus.DELIVERED),
            msg("Did you get a chance to look at the analytical engine notes?", outgoing = false, offset = -2L * MINUTE),
        )
    }

    /** A shorter, read/sent thread for Alan Turing (thread 2). */
    private fun turingThread(): List<Message> {
        val address = "+15557654321"
        var id = 200L

        fun msg(
            body: String,
            outgoing: Boolean,
            offset: Long,
            status: MessageStatus = if (outgoing) MessageStatus.SENT else MessageStatus.RECEIVED,
        ): Message = Message(
            id = id++,
            threadId = 2L,
            address = address,
            body = body,
            timestamp = BASE + offset,
            isOutgoing = outgoing,
            status = status,
            read = true,
        )

        return listOf(
            msg("Can you review the halting-problem write-up?", outgoing = false, offset = -3L * HOUR),
            msg("Sure, send it over.", outgoing = true, offset = -2L * HOUR - 50L * MINUTE, status = MessageStatus.READ),
            msg("Just emailed it. No rush.", outgoing = false, offset = -2L * HOUR - 45L * MINUTE),
            msg("Sounds good — talk soon.", outgoing = true, offset = -1L * HOUR, status = MessageStatus.SENT),
        )
    }

    /** A single-message OTP thread from an unknown number (thread 3). */
    private fun otpThread(): List<Message> = listOf(
        Message(
            id = 300L,
            threadId = 3L,
            address = "+15550009999",
            body = "Your verification code is 482913. It expires in 10 minutes.",
            timestamp = BASE - 3L * HOUR,
            isOutgoing = false,
            status = MessageStatus.RECEIVED,
            read = false,
        ),
    )

    /**
     * A compact set covering every outgoing status, handy for previewing the
     * delivery-caption styling in isolation.
     */
    val statusShowcase: List<Message> = listOf(
        Message(700L, 9L, "+15551112222", "Sending this one…", BASE - 10L * MINUTE, isOutgoing = true, status = MessageStatus.SENDING),
        Message(701L, 9L, "+15551112222", "This one sent.", BASE - 9L * MINUTE, isOutgoing = true, status = MessageStatus.SENT),
        Message(702L, 9L, "+15551112222", "This one was delivered.", BASE - 8L * MINUTE, isOutgoing = true, status = MessageStatus.DELIVERED),
        Message(703L, 9L, "+15551112222", "This one was read.", BASE - 7L * MINUTE, isOutgoing = true, status = MessageStatus.READ),
        Message(704L, 9L, "+15551112222", "This one failed to send.", BASE - 6L * MINUTE, isOutgoing = true, status = MessageStatus.FAILED),
    )
}
