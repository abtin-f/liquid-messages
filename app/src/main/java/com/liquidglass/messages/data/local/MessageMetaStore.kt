package com.liquidglass.messages.data.local

import android.content.Context
import com.liquidglass.messages.data.model.MessageEffect
import com.liquidglass.messages.data.model.Reaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Local-only per-message extras that SMS cannot carry. Like iMessage, each
 * person owns their own tapback: [reaction] is ours (only we can change it),
 * [theirReaction] arrived from the other phone and is read-only here.
 */
@androidx.compose.runtime.Immutable
data class MessageMeta(
    val reaction: Reaction? = null,
    val effect: MessageEffect = MessageEffect.NONE,
    /** Id of the message this one replies to (SMS can't carry the link). */
    val replyTo: Long? = null,
    val theirReaction: Reaction? = null,
) {
    val isEmpty: Boolean get() =
        reaction == null && theirReaction == null && effect == MessageEffect.NONE && replyTo == null
}

/**
 * Persists [MessageMeta] keyed by message row id in [android.content.SharedPreferences]
 * and exposes the whole map as an observable [StateFlow] so the chat re-renders the
 * instant a reaction/effect changes. Lightweight (no DB), survives process death.
 *
 * Obtained from the DI container: `context.appContainer.messageMetaStore`.
 */
class MessageMetaStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Guards the read-modify-write of [_metas] + prefs (now touched from >1 thread). */
    private val lock = Any()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Start empty so construction never blocks (it is first touched on the main
    // thread, during the first chat composition). The stored map is parsed off the
    // main thread and emitted a moment later — the chat already observes the flow.
    private val _metas = MutableStateFlow<Map<Long, MessageMeta>>(emptyMap())
    val metas: StateFlow<Map<Long, MessageMeta>> = _metas.asStateFlow()

    init {
        scope.launch {
            val loaded = loadAll()
            synchronized(lock) {
                // Loaded values are the base; any write that landed during the load wins.
                _metas.value = loaded + _metas.value
            }
        }
    }

    fun metaFor(messageId: Long): MessageMeta = _metas.value[messageId] ?: MessageMeta()

    /** Sets (or clears, when null) the tapback for a message. */
    fun setReaction(messageId: Long, reaction: Reaction?) =
        update(messageId) { it.copy(reaction = reaction) }

    /** Applies the other person's tapback (sync from their phone); null removes it. */
    fun setTheirReaction(messageId: Long, reaction: Reaction?) =
        update(messageId) { it.copy(theirReaction = reaction) }

    /**
     * Toggles a tapback (tapping the same reaction again removes it) atomically,
     * returning the resulting reaction (or null if cleared) so a caller can derive
     * its sync op from the same state transition instead of re-reading the flow.
     */
    fun toggleReaction(messageId: Long, reaction: Reaction): Reaction? = synchronized(lock) {
        val current = metaFor(messageId).reaction
        val next = if (current == reaction) null else reaction
        setReaction(messageId, next)
        next
    }

    /** Stores the send-effect chosen for an outgoing message. */
    fun setEffect(messageId: Long, effect: MessageEffect) =
        update(messageId) { it.copy(effect = effect) }

    /** Links [messageId] as a reply to [targetId]. */
    fun setReplyTo(messageId: Long, targetId: Long) = update(messageId) { it.copy(replyTo = targetId) }

    /** Forgets all metadata for a deleted message. */
    fun clear(messageId: Long) = update(messageId) { MessageMeta() }

    private fun update(messageId: Long, transform: (MessageMeta) -> MessageMeta) = synchronized(lock) {
        val current = _metas.value
        val newMeta = transform(current[messageId] ?: MessageMeta())
        val newMap = current.toMutableMap()
        if (newMeta.isEmpty) newMap.remove(messageId) else newMap[messageId] = newMeta
        _metas.value = newMap

        val key = KEY_PREFIX + messageId
        prefs.edit().apply {
            if (newMeta.isEmpty) remove(key) else putString(key, encode(newMeta))
        }.apply()
    }

    private fun encode(meta: MessageMeta): String =
        "${meta.reaction?.name ?: ""}|${meta.effect.name}|${meta.replyTo ?: ""}|${meta.theirReaction?.name ?: ""}"

    private fun decode(value: String): MessageMeta {
        val parts = value.split("|")
        fun reactionAt(i: Int) = parts.getOrNull(i)?.takeIf { it.isNotEmpty() }
            ?.let { name -> Reaction.entries.firstOrNull { it.name == name } }
        val reaction = reactionAt(0)
        val effect = MessageEffect.fromName(parts.getOrNull(1))
        val replyTo = parts.getOrNull(2)?.toLongOrNull()
        return MessageMeta(reaction, effect, replyTo, theirReaction = reactionAt(3))
    }

    private fun loadAll(): Map<Long, MessageMeta> {
        val result = HashMap<Long, MessageMeta>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(KEY_PREFIX) && value is String) {
                val id = key.removePrefix(KEY_PREFIX).toLongOrNull() ?: continue
                val meta = decode(value)
                if (!meta.isEmpty) result[id] = meta
            }
        }
        return result
    }

    private companion object {
        const val PREFS = "message_meta"
        const val KEY_PREFIX = "m_"
    }
}
