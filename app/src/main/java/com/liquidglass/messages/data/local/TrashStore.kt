package com.liquidglass.messages.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS "Recently Deleted". Nothing is copied out of the SMS/MMS database:
 * deleting a conversation records a cut-off time (everything in the thread up
 * to then is hidden), and deleting a single message records its id. Recover
 * just forgets the record, so messages come back exactly as they were —
 * photos, reactions and all. After [RETENTION_DAYS] (or "Delete Now") the
 * rows are removed from the provider for good.
 *
 * A message that arrives after a conversation was deleted is newer than the
 * cut-off, so it starts a fresh conversation — the same as iOS.
 */
class TrashStore(context: Context) {

    /** A deleted conversation, with a snapshot to show in Recently Deleted. */
    data class TrashedThread(
        val threadId: Long,
        /** Messages at or before this time (epoch ms) are hidden. */
        val cutoff: Long,
        val deletedAt: Long,
        val address: String,
        val name: String?,
        val snippet: String,
        val messageCount: Int,
    )

    /** A single deleted message. */
    data class TrashedMessage(
        val messageId: Long,
        val threadId: Long,
        val deletedAt: Long,
        val address: String,
        val snippet: String,
    )

    data class State(
        val threads: Map<Long, TrashedThread> = emptyMap(),
        val messages: Map<Long, TrashedMessage> = emptyMap(),
    ) {
        val isEmpty: Boolean get() = threads.isEmpty() && messages.isEmpty()

        /** True when a message of [threadId] sent/received at [timestamp] is in the trash. */
        fun hides(threadId: Long, messageId: Long, timestamp: Long): Boolean =
            messageId in messages || (threads[threadId]?.let { timestamp <= it.cutoff } ?: false)
    }

    private val file = File(context.applicationContext.filesDir, "recently_deleted.json")
    private val lock = Any()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state.asStateFlow()

    fun trashThread(t: TrashedThread) = update { it.copy(threads = it.threads + (t.threadId to t)) }

    fun trashMessage(m: TrashedMessage) = update { it.copy(messages = it.messages + (m.messageId to m)) }

    fun forgetThread(threadId: Long) = update { s ->
        s.copy(threads = s.threads - threadId, messages = s.messages.filterValues { it.threadId != threadId })
    }

    fun forgetMessage(messageId: Long) = update { it.copy(messages = it.messages - messageId) }

    /** Entries older than the retention window. */
    fun expired(now: Long = System.currentTimeMillis()): State {
        val limit = now - RETENTION_DAYS * DAY_MS
        val s = _state.value
        return State(s.threads.filterValues { it.deletedAt < limit }, s.messages.filterValues { it.deletedAt < limit })
    }

    private fun update(transform: (State) -> State) = synchronized(lock) {
        val next = transform(_state.value)
        _state.value = next
        save(next)
    }

    private fun save(s: State) = runCatching {
        val root = JSONObject()
        root.put("threads", JSONArray().apply {
            s.threads.values.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.threadId); put("cutoff", t.cutoff); put("at", t.deletedAt)
                    put("address", t.address); put("name", t.name ?: JSONObject.NULL)
                    put("snippet", t.snippet); put("count", t.messageCount)
                })
            }
        })
        root.put("messages", JSONArray().apply {
            s.messages.values.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.messageId); put("thread", m.threadId); put("at", m.deletedAt)
                    put("address", m.address); put("snippet", m.snippet)
                })
            }
        })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        tmp.renameTo(file)
    }

    private fun load(): State = runCatching {
        if (!file.exists()) return State()
        val root = JSONObject(file.readText())
        val threads = root.optJSONArray("threads") ?: JSONArray()
        val messages = root.optJSONArray("messages") ?: JSONArray()
        State(
            threads = List(threads.length()) { i ->
                val o = threads.getJSONObject(i)
                TrashedThread(
                    threadId = o.getLong("id"), cutoff = o.getLong("cutoff"), deletedAt = o.getLong("at"),
                    address = o.optString("address"), name = if (o.isNull("name")) null else o.optString("name"),
                    snippet = o.optString("snippet"), messageCount = o.optInt("count"),
                )
            }.associateBy { it.threadId },
            messages = List(messages.length()) { i ->
                val o = messages.getJSONObject(i)
                TrashedMessage(
                    messageId = o.getLong("id"), threadId = o.getLong("thread"), deletedAt = o.getLong("at"),
                    address = o.optString("address"), snippet = o.optString("snippet"),
                )
            }.associateBy { it.messageId },
        )
    }.getOrDefault(State())

    companion object {
        const val RETENTION_DAYS = 30L
        const val DAY_MS = 86_400_000L
    }
}
