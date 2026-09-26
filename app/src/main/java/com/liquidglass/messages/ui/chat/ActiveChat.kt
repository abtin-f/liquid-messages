package com.liquidglass.messages.ui.chat

/**
 * The conversation currently on screen (or -1). New messages in it are marked
 * read immediately and don't raise a notification — like iOS, where an open
 * conversation never shows a banner or leaves an unread dot behind.
 */
object ActiveChat {
    @Volatile
    var threadId: Long = -1L
}
