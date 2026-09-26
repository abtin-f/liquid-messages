package com.liquidglass.messages.data.local

import android.content.Context
import com.liquidglass.messages.data.model.Conversation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A small on-disk copy of the last inbox the app showed, so the list appears
 * the instant Messages opens (like iOS) while the provider is re-read in the
 * background. Only list metadata is stored, in the app's private files.
 */
class ConversationCache(context: Context) {

    private val file = File(context.applicationContext.filesDir, "inbox_cache.json")

    @Volatile
    private var memory: List<Conversation>? = null

    /** Last saved inbox, or null when there is none yet. */
    fun load(): List<Conversation>? {
        memory?.let { return it }
        return runCatching {
            if (!file.exists()) return null
            val arr = JSONArray(file.readText())
            List(arr.length()) { i -> decode(arr.getJSONObject(i)) }
        }.getOrNull()?.also { memory = it }
    }

    fun save(list: List<Conversation>) {
        if (memory == list) return
        memory = list
        runCatching {
            val arr = JSONArray()
            list.forEach { arr.put(encode(it)) }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
    }

    private fun encode(c: Conversation) = JSONObject().apply {
        put("t", c.threadId)
        put("a", c.address)
        put("n", c.contactName ?: JSONObject.NULL)
        put("s", c.snippet)
        put("ts", c.timestamp)
        put("u", c.unreadCount)
        put("c", c.messageCount)
        put("o", c.isOutgoingSnippet)
        put("p", c.photoUri ?: JSONObject.NULL)
        put("r", JSONArray(c.recipients))
        put("rn", JSONArray(c.recipientNames))
    }

    private fun decode(o: JSONObject): Conversation {
        fun strings(key: String): List<String> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return List(a.length()) { a.getString(it) }
        }
        val address = o.getString("a")
        return Conversation(
            threadId = o.getLong("t"),
            address = address,
            contactName = if (o.isNull("n")) null else o.getString("n"),
            snippet = o.getString("s"),
            timestamp = o.getLong("ts"),
            unreadCount = o.optInt("u"),
            messageCount = o.optInt("c"),
            isOutgoingSnippet = o.optBoolean("o"),
            photoUri = if (o.isNull("p")) null else o.getString("p"),
            recipients = strings("r").ifEmpty { listOf(address) },
            recipientNames = strings("rn"),
        )
    }
}
