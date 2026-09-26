package com.liquidglass.messages

import android.app.Application
import com.liquidglass.messages.di.ServiceLocator

/**
 * App entry point. Builds the [ServiceLocator] and registers notification
 * channels once at process start.
 *
 * Access the container anywhere with:
 *   `(context.applicationContext as MessagesApplication).container`
 */
class MessagesApplication : Application() {

    lateinit var container: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        com.liquidglass.messages.data.mms.MmsLog.init(this)
        container = ServiceLocator(this)
        container.notificationHelper.createChannels()
        // Alarms can be lost (force-stop, some OEM task killers); re-arm Send Later.
        container.scheduledMessages.rescheduleAll()
    }
}

/** Convenience accessor for the DI container from any [android.content.Context]. */
val android.content.Context.appContainer: ServiceLocator
    get() = (applicationContext as MessagesApplication).container
