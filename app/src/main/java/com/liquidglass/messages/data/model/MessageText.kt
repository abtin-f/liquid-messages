package com.liquidglass.messages.data.model

import com.liquidglass.messages.data.location.LocationLink

/**
 * Carries a reply inside a plain SMS as a short quote on the first line —
 * readable in any messaging app, and turned back into a linked reply by
 * Liquid Messages on the other phone:
 *
 *     ↪ «Can you bring the charger…»
 *     Sure, both of them!
 */
object ReplyTag {

    /** Longest quoted snippet; keeps the SMS from spilling into a second part. */
    const val MAX_SNIPPET = 30
    private const val ELLIPSIS = "…"

    private val lineRegex = Regex("""^\s*↪\s*«(.{1,${MAX_SNIPPET + 2}}?)»[ \t]*(?:\r?\n|$)""")

    data class Parsed(val text: String, val quote: String?)

    /** Prefixes [body] with a quote of [original] (the message being answered). */
    fun format(original: Message, body: String): String = "↪ «${snippet(original)}»\n$body"

    fun parse(body: String): Parsed {
        val m = lineRegex.find(body) ?: return Parsed(body, null)
        return Parsed(body.substring(m.range.last + 1).trimStart('\n', '\r'), m.groupValues[1])
    }

    /** One-line, length-capped summary of a message for quoting. */
    fun snippet(m: Message): String {
        val text = MessageText.summary(m).replace(Regex("\\s+"), " ").trim()
        return if (text.length <= MAX_SNIPPET) text else text.take(MAX_SNIPPET - 1).trimEnd() + ELLIPSIS
    }

    /**
     * Finds the message a received quote refers to: the newest earlier message
     * whose summary starts with the quote (ignoring whitespace and the ellipsis).
     */
    fun findQuoted(quote: String, reply: Message, thread: List<Message>): Message? {
        val needle = normalize(quote.removeSuffix(ELLIPSIS))
        if (needle.isEmpty()) return null
        return thread.lastOrNull { m ->
            m.id != reply.id && m.timestamp <= reply.timestamp && normalize(MessageText.summary(m)).startsWith(needle)
        }
    }

    private fun normalize(s: String) = s.replace(Regex("\\s+"), " ").trim().lowercase()
}

/** The human-visible parts of a stored message body. */
object MessageText {

    /** Body with the reply quote line and the effect line removed. */
    fun visible(body: String): String = EffectTag.strip(ReplyTag.parse(body).text)

    /** Short description used for previews, quotes and notifications. */
    fun summary(m: Message): String {
        val visible = visible(m.body)
        val loc = LocationLink.parse(visible)
        return when {
            loc != null -> loc.remainingText.ifBlank { "📍 " + (loc.label ?: "Location") }
            visible.isBlank() && m.attachments.any { it.isImage } -> "📷 Photo"
            visible.isBlank() && m.attachments.any { it.isVideo } -> "🎬 Video"
            visible.isBlank() && m.attachments.isNotEmpty() -> "📎 Attachment"
            else -> visible
        }
    }
}
