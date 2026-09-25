package com.liquidglass.messages.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * The runtime permissions Liquid Messages uses, split by how much they matter.
 *
 * Only the SMS permissions gate the app. Holding the default-SMS role grants
 * them automatically on Android 10+, so in practice the role is the only real
 * gate. Contacts and notifications are *optional*: denying them degrades the
 * app (numbers instead of names, no banners) but must never lock the user out —
 * the old "all or nothing" gate trapped anyone who denied one of them on the
 * setup screen forever.
 */
object PermissionManager {

    /** Without these the app cannot function at all. */
    val requiredPermissions: Array<String> = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
    )

    /** Nice to have; requested once alongside the required set. */
    val optionalPermissions: Array<String> = buildList {
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.RECEIVE_MMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun missing(context: Context, perms: Array<String>): List<String> =
        perms.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun missingRequired(context: Context): List<String> = missing(context, requiredPermissions)

    fun missingOptional(context: Context): List<String> = missing(context, optionalPermissions)

    fun hasRequiredPermissions(context: Context): Boolean = missingRequired(context).isEmpty()

    /**
     * True when at least one required permission can no longer be requested with
     * the in-app dialog (denied twice, or blocked as a restricted setting), so the
     * only way forward is the system App-info page.
     */
    fun requiredPermanentlyDenied(activity: Activity): Boolean =
        missingRequired(activity).any { !activity.shouldShowRequestPermissionRationale(it) }

    /** Opens this app's system "App info" page (where "Allow restricted settings" lives). */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
