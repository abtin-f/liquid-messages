package com.liquidglass.messages.util

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/**
 * Helpers for checking whether Liquid Messages is the system default SMS app and
 * for building the platform-appropriate intent that asks the user to grant it.
 *
 * Being the default SMS app is a hard requirement for a messaging app: only the
 * default app receives [Telephony.Sms.Intents.SMS_DELIVER_ACTION] and is allowed
 * to write to the Telephony provider.
 */
object DefaultSmsAppManager {

    /** True when this package currently holds the default-SMS role. */
    fun isDefaultSmsApp(context: Context): Boolean =
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    /**
     * Builds the intent that prompts the user to make this app the default SMS
     * handler.
     *
     * On API 29+ this goes through [RoleManager.ROLE_SMS] (the legacy
     * ACTION_CHANGE_DEFAULT path is deprecated/no-op there). On older releases it
     * falls back to [Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT].
     *
     * @return the intent to start for a result, or null if the device has no SMS
     *         role available (extremely rare; non-telephony devices).
     */
    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            // Some non-phone devices report the role as unavailable; guard for it.
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            }
            return null
        }
        return Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
    }
}
