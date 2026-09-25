package com.liquidglass.messages.data.model

/**
 * Carries a send effect inside a plain SMS, the way iPhones do when they text a
 * non-iMessage phone: a short readable line at the end — "(Sent with Slam effect)".
 *
 * Liquid Messages strips that line from the bubble and plays the effect; any
 * other messaging app simply shows the line, which still makes sense to a human.
 * Also understands the names iOS uses (Slam, Loud, Gentle, Invisible Ink, …) so
 * effects sent from an iPhone play here too.
 */
object EffectTag {

    private val tagRegex = Regex("""\s*\(\s*sent with ([a-z][a-z ]{1,20}?)(?: effect)?\s*\)\s*$""", RegexOption.IGNORE_CASE)

    /** iOS bubble / screen effect names → our nearest effect. */
    private val iosNames = mapOf(
        "slam" to MessageEffect.SHAKE,
        "loud" to MessageEffect.BIG,
        "gentle" to MessageEffect.SMALL,
        "invisible ink" to MessageEffect.BLOOM,
        "echo" to MessageEffect.RIPPLE,
        "spotlight" to MessageEffect.BLOOM,
        "balloons" to MessageEffect.BLOOM,
        "confetti" to MessageEffect.EXPLODE,
        "love" to MessageEffect.BLOOM,
        "lasers" to MessageEffect.JITTER,
        "fireworks" to MessageEffect.EXPLODE,
        "celebration" to MessageEffect.EXPLODE,
        "nod" to MessageEffect.JITTER,
    )

    data class Parsed(val text: String, val effect: MessageEffect)

    /** The text to send for [body] with [effect] (unchanged for [MessageEffect.NONE]). */
    fun append(body: String, effect: MessageEffect): String =
        if (effect == MessageEffect.NONE) body else "$body\n(Sent with ${effect.label} effect)"

    /** Splits a received/stored body into the visible text and its effect. */
    fun parse(body: String): Parsed {
        val m = tagRegex.find(body) ?: return Parsed(body, MessageEffect.NONE)
        val name = m.groupValues[1].trim().lowercase()
        val effect = MessageEffect.entries.firstOrNull { it != MessageEffect.NONE && it.label.lowercase() == name }
            ?: iosNames[name]
            ?: return Parsed(body, MessageEffect.NONE)
        return Parsed(body.removeRange(m.range).trimEnd(), effect)
    }

    /** Body without the effect line (for previews and notifications). */
    fun strip(body: String): String = parse(body).text
}
