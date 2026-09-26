package com.liquidglass.messages.data.reaction

import android.util.Log
import com.liquidglass.messages.data.local.MessageMetaStore
import com.liquidglass.messages.data.model.Message
import com.liquidglass.messages.data.model.Reaction
import com.liquidglass.messages.data.sms.SmsRepository
import com.liquidglass.messages.data.sms.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Syncs tapback reactions between two devices over SMS, end to end:
 *
 *  - **outbound** ([sendReaction]) — when the local user reacts, a compact
 *    [ReactionProtocol] control SMS is sent to the other party. It is sent via
 *    [SmsSender.sendRawMessage] (no provider row), so it never shows as a bubble.
 *  - **inbound** ([handleIncoming]) — a delivered SMS that carries a reaction
 *    token is parsed, matched to the corresponding local message, applied to the
 *    [MessageMetaStore], and suppressed (never persisted or shown).
 *
 * Each side owns its own tapback: an incoming one is stored separately
 * ([MessageMetaStore.setTheirReaction]) and shown next to ours, never over it. Every send is user-initiated (a
 * tap) and routed through the same [com.liquidglass.messages.util.SmsGuard] as
 * normal messages; nothing is sent silently or automatically, and an inbound
 * reaction never triggers an outbound one (no echo loop).
 */
class ReactionSyncManager(
    private val repository: SmsRepository,
    private val metaStore: MessageMetaStore,
    private val sender: SmsSender,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Tighter, dedicated cap for reaction control SMS so they can't starve real sends. */
    private val reactionGuard = com.liquidglass.messages.util.SmsGuard(
        maxPerRecipientPerMinute = 10,
        maxGlobalPerMinute = 30,
    )

    /**
     * Sends the reaction the local user just set/cleared on [target] to [address].
     *
     * @param op       ADD when the reaction is now set, REMOVE when toggled off.
     * @param reaction the reaction toggled.
     * @param target   the local message it applies to (supplies body/timestamp/direction).
     */
    fun sendReaction(
        address: String,
        subscriptionId: Int,
        op: ReactionProtocol.Op,
        reaction: Reaction,
        target: Message,
    ) {
        if (address.isBlank() || target.body.isBlank()) return
        if (!reactionGuard.allow(address)) return
        scope.launch {
            try {
                val body = ReactionProtocol.encode(
                    op = op,
                    reaction = reaction,
                    localTargetIsOutgoing = target.isOutgoing,
                    targetBody = target.body,
                    targetTimestampMs = target.timestamp,
                )
                sender.sendRawMessage(address, body, subscriptionId)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to send reaction", t)
            }
        }
    }

    /**
     * If [body] from [address] is a reaction control message, apply it and return
     * true (so the caller suppresses it — no inbox row, no notification).
     * Returns false for ordinary messages.
     *
     * Safe to call from the SMS_DELIVER IO coroutine; never throws.
     */
    suspend fun handleIncoming(address: String, body: String): Boolean {
        if (!ReactionProtocol.isReactionControl(body)) return false
        // It IS a control message — suppress it regardless of whether we can match
        // a target, so a malformed/duplicate token never leaks into the thread.
        try {
            val parsed = ReactionProtocol.decode(body) ?: return true
            val threadId = repository.getOrCreateThreadId(address)
            if (threadId <= 0L) return true

            val target = findTarget(threadId, parsed)
            if (target != null) {
                when (parsed.op) {
                    // Their tapback lives in its own slot: it never replaces
                    // ours, and we can't change it — exactly like iMessage.
                    ReactionProtocol.Op.ADD ->
                        metaStore.setTheirReaction(target.id, parsed.reaction)
                    ReactionProtocol.Op.REMOVE ->
                        metaStore.setTheirReaction(target.id, null)
                }
            } else {
                Log.w(TAG, "Reaction target not found (ref=${parsed.bodyRef})")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to handle incoming reaction", t)
        }
        return true
    }

    /**
     * Finds the local message a reaction refers to: same direction (already
     * flipped to our frame), same body hash, and the timestamp closest to the
     * sender's hint (tie-break: newest). Returns null when nothing matches.
     */
    private suspend fun findTarget(
        threadId: Long,
        parsed: ReactionProtocol.Incoming,
    ): Message? {
        val candidates = repository.getMessages(threadId).filter {
            it.isOutgoing == parsed.targetIsOutgoingForUs &&
                ReactionProtocol.bodyHash(it.body) == parsed.bodyRef
        }
        if (candidates.isEmpty()) return null
        // Nearest timestamp wins; an EXACT delta tie breaks to the newest. A
        // lexicographic comparator avoids the overflow / mis-ranking that folding
        // both keys into one additive value caused.
        return candidates.minWithOrNull(
            compareBy<Message> { abs(it.timestamp / 1000L - parsed.tsHintSeconds) }
                .thenByDescending { it.timestamp }
        )
    }

    private companion object {
        const val TAG = "ReactionSync"
    }
}
