package com.liquidglass.messages.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.liquidglass.messages.appContainer
import com.liquidglass.messages.data.model.Contact
import com.liquidglass.messages.data.model.Message

/**
 * Builds and posts the "new message" notifications and owns the notification
 * channel. Constructor matches the [com.liquidglass.messages.di.ServiceLocator]
 * binding: `NotificationHelper(context: Context)`.
 */
class NotificationHelper(private val context: Context) {

    /**
     * Creates the messaging notification channel. Safe to call repeatedly
     * (creating an existing channel is a no-op). Called once at process start and
     * again after BOOT_COMPLETED.
     */
    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming text messages"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Posts an incoming-message notification for [message] from [contact].
     *
     * Uses [NotificationCompat.MessagingStyle] for the iMessage-like look, with a
     * tap target that deep-links into the thread, an inline RemoteInput Reply and
     * a Mark-as-read action (both routed through the declared headless service,
     * see [NotificationActions]).
     *
     * No-ops if the user has disabled notifications or has not granted
     * POST_NOTIFICATIONS on API 33+.
     */
    fun notifyIncoming(message: Message, contact: Contact) {
        val manager = NotificationManagerCompat.from(context)

        // Respect the user's choice / runtime permission state.
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // The conversation is open on screen: no banner (it's marked read there).
        if (com.liquidglass.messages.ui.chat.ActiveChat.threadId == message.threadId) return

        // Respect a per-thread mute set from the contact-info sheet.
        if (context.appContainer.threadPrefs.isMuted(message.threadId)) return

        val address = message.address
        val threadId = message.threadId
        val notificationId = notificationIdFor(threadId)

        // Sender shown in the MessagingStyle thread.
        val sender = Person.Builder()
            .setName(contact.displayName)
            .setKey(address)
            .build()
        // "Me" is left without a name so the system labels it as the local user.
        val me = Person.Builder().setName("Me").build()

        // iOS "Show Previews": Never hides the text everywhere; When Unlocked
        // shows a redacted public version on the lock screen.
        val previews = context.appContainer.appSettings.notificationPreviews.value
        val shownBody = if (previews == com.liquidglass.messages.data.local.NotificationPreviews.NEVER) "Message" else
            com.liquidglass.messages.data.model.MessageText.visible(message.body)

        val messagingStyle = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(contact.displayName)
            .setGroupConversation(false)
            .addMessage(shownBody, message.timestamp, sender)

        // ---- Inline Reply action (RemoteInput) ----
        val remoteInput = RemoteInput.Builder(NotificationActions.KEY_REPLY_TEXT)
            .setLabel("Reply")
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            NotificationActions.replyPendingIntent(context, address, threadId, notificationId)
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()

        // ---- Mark-as-read action ----
        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Mark as read",
            NotificationActions.markReadPendingIntent(context, address, threadId, notificationId)
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setStyle(messagingStyle)
            .setContentTitle(contact.displayName)
            .setContentText(shownBody)
            .setContentIntent(
                NotificationActions.openThreadPendingIntent(context, address, notificationId)
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(replyAction)
            .addAction(markReadAction)
        when (previews) {
            com.liquidglass.messages.data.local.NotificationPreviews.ALWAYS ->
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            else -> {
                builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                builder.setPublicVersion(
                    NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                        .setSmallIcon(android.R.drawable.sym_action_chat)
                        .setContentTitle("Messages")
                        .setContentText("Notification")
                        .build()
                )
            }
        }

        // POST_NOTIFICATIONS already verified above; suppress the lint flag.
        try {
            manager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post — ignore.
        }
    }

    /** Cancels the notification associated with [threadId], if shown. */
    fun cancelThread(threadId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(threadId))
    }

    /** Stable, thread-scoped notification id (keep positive, avoid 0). */
    private fun notificationIdFor(threadId: Long): Int =
        NOTIFICATION_ID_BASE + (threadId.toInt() and 0x00FF_FFFF)

    companion object {
        /** High-importance channel for incoming messages. */
        const val CHANNEL_MESSAGES = "messages"

        /** Offset so thread-derived ids never collide with id 0. */
        private const val NOTIFICATION_ID_BASE = 0x0100_0000
    }
}
