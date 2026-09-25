package com.liquidglass.messages.data.local

import android.content.Context

/**
 * Tiny per-thread preference store backed by SharedPreferences.
 *
 * Currently tracks which conversation threads the user has muted; the notification
 * layer consults [isMuted] before posting an incoming-message notification.
 */
class ThreadPrefs(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isMuted(threadId: Long): Boolean =
        threadId > 0 && prefs.getBoolean(keyMute(threadId), false)

    fun setMuted(threadId: Long, muted: Boolean) {
        if (threadId <= 0) return
        prefs.edit().apply {
            if (muted) putBoolean(keyMute(threadId), true) else remove(keyMute(threadId))
        }.apply()
    }

    private fun keyMute(threadId: Long): String = "mute_$threadId"

    private companion object {
        const val PREFS = "thread_prefs"
    }
}
