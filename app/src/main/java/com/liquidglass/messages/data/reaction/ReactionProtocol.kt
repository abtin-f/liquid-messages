package com.liquidglass.messages.data.reaction

import com.liquidglass.messages.data.model.Reaction
import java.util.zip.CRC32

/**
 * Wire format for syncing tapback reactions between two devices over plain SMS.
 *
 * SMS carries no message identifiers, so a reaction must reference its target
 * message by content. A reaction control SMS encodes:
 *
 *   `⟦LQR1|<op>|<code>|<dir>|<ref>|<ts>⟧`
 *
 *  - **op**   `a` = add/set, `r` = remove
 *  - **code** one stable letter per [Reaction] (decoupled from enum names)
 *  - **dir**  the *reactor's* view of the target: `o` = a message they sent,
 *             `i` = a message they received. The receiver flips this.
 *  - **ref**  CRC-32 (8 hex) of the trimmed target body — identical on both
 *             devices because the body text is identical.
 *  - **ts**   target timestamp in seconds (base-36) — a disambiguation hint used
 *             to pick the right message when several share the same body.
 *
 * The whole token fits in one SMS segment. A supporting app treats a message as a
 * reaction ONLY when the ENTIRE body is this token (never a substring match), then
 * applies the reaction and suppresses it. Any normal message — even one that merely
 * contains the token text — is always shown untouched, so no message can be lost.
 *
 * This is a best-effort sync (no IDs on the wire), so it converges on
 * last-writer-wins and matches by (direction + body hash + nearest timestamp).
 */
object ReactionProtocol {

    /** Protocol id + version embedded in every control token. */
    const val VERSION = "LQR1"

    private const val OPEN = "⟦"  // ⟦
    private const val CLOSE = "⟧" // ⟧

    private val TOKEN = Regex(
        "$OPEN$VERSION\\|([ar])\\|([A-Z])\\|([io])\\|([0-9a-f]{1,8})\\|([0-9a-z]{1,12})$CLOSE"
    )

    enum class Op(val wire: String) { ADD("a"), REMOVE("r") }

    /** Stable 1-char codes; independent of [Reaction] ordinal/name so renames are safe. */
    private val CODE_OF: Map<Reaction, String> = mapOf(
        Reaction.LOVE to "L",
        Reaction.LIKE to "K",
        Reaction.DISLIKE to "D",
        Reaction.LAUGH to "H",
        Reaction.EMPHASIZE to "E",
        Reaction.QUESTION to "Q",
    )
    private val BY_CODE: Map<String, Reaction> = CODE_OF.entries.associate { (k, v) -> v to k }

    /** A parsed inbound reaction, already translated to the *local* device's frame. */
    data class Incoming(
        val op: Op,
        val reaction: Reaction,
        /** True when the target is a message THIS device sent (outgoing here). */
        val targetIsOutgoingForUs: Boolean,
        val bodyRef: String,
        val tsHintSeconds: Long,
    )

    /** CRC-32 of the trimmed body as zero-padded 8-hex — deterministic across devices. */
    fun bodyHash(body: String): String {
        val crc = CRC32()
        crc.update(body.trim().encodeToByteArray())
        return crc.value.toString(16).padStart(8, '0').takeLast(8)
    }

    /**
     * Builds the control SMS body for a reaction the local user just set/cleared.
     *
     * @param localTargetIsOutgoing whether, on THIS device, the reacted-to message
     *        was outgoing. Encoded as the reactor's `dir`.
     */
    fun encode(
        op: Op,
        reaction: Reaction,
        localTargetIsOutgoing: Boolean,
        targetBody: String,
        targetTimestampMs: Long,
    ): String {
        val code = CODE_OF[reaction] ?: "L"
        val dir = if (localTargetIsOutgoing) "o" else "i"
        val ref = bodyHash(targetBody)
        val ts = (targetTimestampMs / 1000L).coerceAtLeast(0L).toString(36)
        return "$OPEN$VERSION|${op.wire}|$code|$dir|$ref|$ts$CLOSE"
    }

    /**
     * True only when the ENTIRE (trimmed) message is a reaction control token. We
     * match the whole body — never a substring — so an ordinary message that merely
     * contains the token text is never silently suppressed (a real data-loss / abuse
     * vector). Genuine reaction syncs are solely the token, so they still match.
     */
    fun isReactionControl(body: String): Boolean = TOKEN.matchEntire(body.trim()) != null

    /** Parses [body] into an [Incoming] in the local frame, or null if malformed. */
    fun decode(body: String): Incoming? {
        val m = TOKEN.matchEntire(body.trim()) ?: return null
        val opWire = m.groupValues[1]
        val codeWire = m.groupValues[2]
        val dirWire = m.groupValues[3]
        val ref = m.groupValues[4]
        val tsWire = m.groupValues[5]

        val op = if (opWire == "a") Op.ADD else Op.REMOVE
        val reaction = BY_CODE[codeWire] ?: return null
        // Flip the reactor's perspective to ours: a message they received ('i')
        // is one we sent (outgoing here); a message they sent ('o') is one we
        // received (incoming here).
        val targetIsOutgoingForUs = dirWire == "i"
        // Clamp the untrusted hint so a hostile/corrupt token can't drive overflow
        // in the downstream nearest-timestamp comparison.
        val maxTs = System.currentTimeMillis() / 1000L + 86_400L
        val tsHint = (tsWire.toLongOrNull(36) ?: 0L).coerceIn(0L, maxTs)
        return Incoming(op, reaction, targetIsOutgoingForUs, ref, tsHint)
    }
}
