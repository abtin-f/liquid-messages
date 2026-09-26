package com.liquidglass.messages.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Central registry of navigation routes for the Compose [androidx.navigation.NavHost].
 *
 * Routes are kept as plain strings (Navigation-Compose 2.7.x has no type-safe
 * builder), so we centralise the construction/parsing here to keep the graph and
 * call sites in sync. The chat route carries the thread id in its path and the
 * (optional) primary address as a query parameter; the address is URL-encoded so
 * phone numbers / emails with reserved characters (`+`, `&`, `?`, spaces) survive
 * the round-trip through the route string.
 */
object Routes {

    /** Conversation list — the start destination. */
    const val conversations: String = "conversations"

    /** App settings (iPhone Settings › Messages equivalent). */
    const val settings: String = "settings"

    /** iOS Messages › Recently Deleted. */
    const val recentlyDeleted: String = "recently_deleted"

    /** Argument keys used by the chat destination. */
    const val argThreadId: String = "threadId"
    const val argAddress: String = "address"

    /**
     * Pattern registered with `composable(...)`. The thread id is a required path
     * segment; the address is an optional query parameter. Both are parsed back
     * out via the [argThreadId] / [argAddress] nav arguments.
     */
    const val chatPattern: String = "chat/{$argThreadId}?$argAddress={$argAddress}"

    /**
     * Builds a concrete chat route for navigation.
     *
     * @param threadId the Telephony thread id (use -1 for a not-yet-created thread
     *                 when deep-linking to a brand-new recipient).
     * @param address  the primary address; URL-encoded into the query string.
     */
    fun chat(threadId: Long, address: String): String =
        "chat/$threadId?$argAddress=${encodeAddress(address)}"

    /** Contact-info sheet route pattern + builder (same args as the chat route). */
    const val contactInfoPattern: String = "contactInfo/{$argThreadId}?$argAddress={$argAddress}"

    fun contactInfo(threadId: Long, address: String): String =
        "contactInfo/$threadId?$argAddress=${encodeAddress(address)}"

    /** URL-encodes an address for safe embedding in a route query parameter. */
    fun encodeAddress(address: String): String =
        URLEncoder.encode(address, Charsets.UTF_8.name())

    /**
     * Decodes an address previously produced by [encodeAddress]. Returns an empty
     * string for a null input so callers always receive a non-null address.
     */
    fun decodeAddress(encoded: String?): String =
        if (encoded.isNullOrEmpty()) "" else URLDecoder.decode(encoded, Charsets.UTF_8.name())
}
