package com.liquidglass.messages.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tiny per-thread preference store backed by SharedPreferences: which threads
 * are muted ("Hide Alerts") and which are pinned to the top of the inbox, in
 * the order they were pinned — both observable so the list updates instantly.
 */
class ThreadPrefs(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _muted = MutableStateFlow(
        prefs.all.keys.filter { it.startsWith("mute_") }.mapNotNull { it.removePrefix("mute_").toLongOrNull() }.toSet()
    )
    val muted: StateFlow<Set<Long>> = _muted.asStateFlow()

    private val _pinned = MutableStateFlow(
        prefs.getString(KEY_PINS, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
    )
    /** Pinned thread ids, oldest pin first (iOS keeps that order in the grid). */
    val pinned: StateFlow<List<Long>> = _pinned.asStateFlow()

    private val _wallpapers = MutableStateFlow(
        prefs.all.entries.filter { it.key.startsWith("bg_") }.mapNotNull { (k, v) ->
            val id = k.removePrefix("bg_").toLongOrNull() ?: return@mapNotNull null
            val w = ChatWallpaper.entries.firstOrNull { it.name == v } ?: return@mapNotNull null
            id to w
        }.toMap()
    )
    /** Per-conversation backgrounds that override the global one. */
    val wallpapers: StateFlow<Map<Long, ChatWallpaper>> = _wallpapers.asStateFlow()

    /** Sets a conversation's own background; null follows the global setting. */
    fun setWallpaper(threadId: Long, wallpaper: ChatWallpaper?) {
        if (threadId <= 0) return
        prefs.edit().apply {
            if (wallpaper == null) remove("bg_$threadId") else putString("bg_$threadId", wallpaper.name)
        }.apply()
        _wallpapers.value = if (wallpaper == null) _wallpapers.value - threadId else _wallpapers.value + (threadId to wallpaper)
    }

    private val _photos = MutableStateFlow(
        prefs.all.entries.filter { it.key.startsWith("bgphoto_") }.mapNotNull { (k, v) ->
            val id = k.removePrefix("bgphoto_").toLongOrNull() ?: return@mapNotNull null
            (v as? String)?.let { id to it }
        }.toMap()
    )
    /** Per-conversation photo backgrounds (file paths in app storage). */
    val photoBackgrounds: StateFlow<Map<Long, String>> = _photos.asStateFlow()

    fun setPhotoBackground(threadId: Long, path: String?) {
        if (threadId <= 0) return
        prefs.edit().apply {
            if (path == null) remove("bgphoto_$threadId") else putString("bgphoto_$threadId", path)
        }.apply()
        _photos.value = if (path == null) _photos.value - threadId else _photos.value + (threadId to path)
    }

    fun isMuted(threadId: Long): Boolean =
        threadId > 0 && prefs.getBoolean(keyMute(threadId), false)

    fun setMuted(threadId: Long, muted: Boolean) {
        if (threadId <= 0) return
        prefs.edit().apply {
            if (muted) putBoolean(keyMute(threadId), true) else remove(keyMute(threadId))
        }.apply()
        _muted.value = if (muted) _muted.value + threadId else _muted.value - threadId
    }

    fun isPinned(threadId: Long): Boolean = threadId in _pinned.value

    /** Pins or unpins a thread. iOS allows at most [MAX_PINS]; returns false when full. */
    fun setPinned(threadId: Long, pinned: Boolean): Boolean {
        if (threadId <= 0) return false
        val current = _pinned.value
        val next = when {
            !pinned -> current - threadId
            threadId in current -> current
            current.size >= MAX_PINS -> return false
            else -> current + threadId
        }
        _pinned.value = next
        prefs.edit().putString(KEY_PINS, next.joinToString(",")).apply()
        return true
    }

    private fun keyMute(threadId: Long): String = "mute_$threadId"

    companion object {
        const val MAX_PINS = 9
        private const val PREFS = "thread_prefs"
        private const val KEY_PINS = "pinned"
    }
}
