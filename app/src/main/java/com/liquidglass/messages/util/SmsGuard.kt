package com.liquidglass.messages.util

import android.util.Log

/**
 * Defensive rate-limiter / validator that every outgoing SMS passes through.
 *
 * Its job is NOT to throttle a real user (the caps are far above any human
 * texting rate) but to make the app provably safe: it blocks the kinds of
 * patterns a security reviewer or the platform would treat as abuse — a runaway
 * loop hammering one number, or an unbounded burst across many numbers (e.g. a
 * bug or a hijacked code path). All sends are user-initiated; this is a backstop.
 *
 * Thread-safe; cheap (a small sliding window per recipient).
 */
class SmsGuard(
    private val maxPerRecipientPerMinute: Int = 30,
    private val maxGlobalPerMinute: Int = 100,
    private val windowMs: Long = 60_000L,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    private val perRecipient = HashMap<String, ArrayDeque<Long>>()
    private val global = ArrayDeque<Long>()
    private val lock = Any()

    /** A basic sanity check that [address] looks like a dialable destination. */
    fun isValidDestination(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        val digits = address.count { it.isDigit() }
        // Short codes can be 3–8 digits; normal numbers more. Allow + and digits.
        return digits in 3..20 && address.all { it.isDigit() || it in "+-() ." }
    }

    /**
     * Records and authorizes a send to [address]. Returns false (and logs) when a
     * cap is exceeded or the destination is invalid — the caller must NOT send.
     */
    fun allow(address: String?): Boolean {
        if (!isValidDestination(address)) {
            Log.w(TAG, "Blocked send: invalid destination")
            return false
        }
        val key = address!!.filter { it.isDigit() || it == '+' }
        synchronized(lock) {
            val t = now()
            prune(global, t)
            // Bound the map: evict recipients with no sends inside the window so it
            // can't grow without limit over the process lifetime.
            perRecipient.entries.removeAll { (_, q) -> prune(q, t); q.isEmpty() }
            val bucket = perRecipient.getOrPut(key) { ArrayDeque() }
            prune(bucket, t)

            if (bucket.size >= maxPerRecipientPerMinute) {
                Log.w(TAG, "Blocked send: per-recipient cap reached")
                return false
            }
            if (global.size >= maxGlobalPerMinute) {
                Log.w(TAG, "Blocked send: global cap reached")
                return false
            }
            bucket.addLast(t)
            global.addLast(t)
            return true
        }
    }

    private fun prune(q: ArrayDeque<Long>, t: Long) {
        val cutoff = t - windowMs
        while (q.isNotEmpty() && q.first() < cutoff) q.removeFirst()
    }

    private companion object {
        const val TAG = "SmsGuard"
    }
}
