package com.liquidglass.messages.di

import android.content.Context
import com.liquidglass.messages.data.local.AppSettings
import com.liquidglass.messages.data.local.MessageMetaStore
import com.liquidglass.messages.data.mms.MmsCoordinator
import com.liquidglass.messages.data.mms.MmsStore
import com.liquidglass.messages.data.mms.MmsTransport
import com.liquidglass.messages.data.local.ThreadPrefs
import com.liquidglass.messages.data.schedule.ScheduledMessageStore
import com.liquidglass.messages.data.reaction.ReactionSyncManager
import com.liquidglass.messages.data.sms.ContactsHelper
import com.liquidglass.messages.data.sms.SmsRepository
import com.liquidglass.messages.data.sms.SmsRepositoryImpl
import com.liquidglass.messages.data.sms.SmsSender
import com.liquidglass.messages.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers

/**
 * Lightweight manual dependency container. Avoids the build-config surface of a
 * full DI framework while still giving us single, lazily-created instances that
 * are easy to follow and test.
 *
 * The constructor signatures referenced here are the binding contract that the
 * data-layer classes must match:
 *   - ContactsHelper(context: Context)
 *   - SmsSender(context: Context)
 *   - NotificationHelper(context: Context)
 *   - SmsRepositoryImpl(context, sender, contactsHelper, ioDispatcher)
 */
class ServiceLocator(private val appContext: Context) {

    val contactsHelper: ContactsHelper by lazy { ContactsHelper(appContext) }

    val smsSender: SmsSender by lazy {
        SmsSender(appContext, deliveryReports = { appSettings.deliveryReports.value })
    }

    val notificationHelper: NotificationHelper by lazy { NotificationHelper(appContext) }

    /** Local-only tapback + send-effect metadata (SMS can't carry these). */
    val messageMetaStore: MessageMetaStore by lazy { MessageMetaStore(appContext) }

    /** App-wide user preferences (sent-bubble colour, …). */
    val appSettings: AppSettings by lazy { AppSettings(appContext) }

    /** MMS: provider storage, platform transport and the send/receive pipeline. */
    val mmsStore: MmsStore by lazy { MmsStore(appContext) }
    val mmsTransport: MmsTransport by lazy { MmsTransport(appContext) }
    val mms: MmsCoordinator by lazy {
        MmsCoordinator(appContext, mmsStore, mmsTransport, contactsHelper, notificationHelper)
    }

    /** "Send Later" queue + alarms. */
    val scheduledMessages: ScheduledMessageStore by lazy { ScheduledMessageStore(appContext) }

    /** Per-thread preferences (muted notification threads). */
    val threadPrefs: ThreadPrefs by lazy { ThreadPrefs(appContext) }

    val smsRepository: SmsRepository by lazy {
        SmsRepositoryImpl(
            context = appContext,
            sender = smsSender,
            contactsHelper = contactsHelper,
            ioDispatcher = Dispatchers.IO,
            mmsStore = mmsStore,
            mms = mms,
        )
    }

    /** Reaction sync over SMS (send + receive); depends on repo, store, sender. */
    val reactionSyncManager: ReactionSyncManager by lazy {
        ReactionSyncManager(
            repository = smsRepository,
            metaStore = messageMetaStore,
            sender = smsSender,
        )
    }
}
