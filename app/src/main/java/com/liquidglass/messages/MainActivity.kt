package com.liquidglass.messages

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.liquidglass.messages.ui.components.IosDialogHost
import com.liquidglass.messages.ui.navigation.MessagesNavGraph
import kotlinx.coroutines.launch
import com.liquidglass.messages.ui.onboarding.SetupScreen
import com.liquidglass.messages.ui.theme.LiquidMessagesTheme
import com.liquidglass.messages.ui.theme.LiquidTheme
import com.liquidglass.messages.util.DefaultSmsAppManager
import com.liquidglass.messages.util.PermissionManager

/**
 * Single-activity host for Liquid Messages.
 *
 * Responsibilities:
 *  - Install the splash screen and go edge-to-edge.
 *  - Track whether the app is the default SMS handler and whether all runtime
 *    permissions are granted, refreshing both in [onResume] (the user may toggle
 *    either from system settings while we're backgrounded).
 *  - Drive the default-app role request and the runtime-permission request via
 *    the Activity Result APIs.
 *  - Parse an incoming SENDTO/SEND intent into a recipient + body deep link and
 *    feed it into the nav graph.
 *
 * Until the app is both the default SMS app and fully permissioned, the
 * [SetupScreen] gate is shown; afterwards the [MessagesNavGraph] takes over.
 */
class MainActivity : ComponentActivity() {

    // Reactive gates read by the composition; refreshed in onResume.
    private var isDefaultSmsApp by mutableStateOf(false)
    private var hasPermissions by mutableStateOf(false)

    // Set when Android refused the request without the user getting a real
    // choice — typically Android 13+/15+ "restricted settings" for sideloaded
    // apps, where the role dialog never appears or returns instantly. The setup
    // screen then switches to step-by-step "Allow restricted settings" help.
    private var roleBlocked by mutableStateOf(false)
    private var permissionsBlocked by mutableStateOf(false)
    private var roleRequestStartedAt = 0L

    // Deep-link target derived from the launch (or new) intent.
    private var pendingRecipient by mutableStateOf<String?>(null)
    private var pendingBody by mutableStateOf<String?>(null)

    /** Launches the system "make default SMS app" flow; refreshes state on return. */
    private val defaultAppLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Regardless of result code, re-query the authoritative system state.
            refreshGates()
            if (isDefaultSmsApp) {
                roleBlocked = false
                // The role auto-grants SMS permissions; ask for the optional ones
                // (contacts, notifications) right away, once.
                requestPermissions()
            } else {
                // Any refusal shows the help card; it also covers the silent
                // "restricted setting" block where no dialog was ever shown.
                roleBlocked = true
                val elapsed = SystemClock.elapsedRealtime() - roleRequestStartedAt
                Log.i(TAG, "Default-SMS role not granted (returned after ${elapsed}ms)")
            }
        }

    /** Requests the runtime permissions; refreshes state on return. */
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshGates()
            permissionsBlocked = !hasPermissions &&
                PermissionManager.requiredPermanentlyDenied(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate / setContentView for the backport.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Draw behind the system bars; the theme keeps them transparent.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        refreshGates()
        parseDeepLink(intent)

        setContent {
            LiquidMessagesTheme {
                Surface(color = LiquidTheme.colors.listBackground) {
                  IosDialogHost {
                    if (!isDefaultSmsApp || !hasPermissions) {
                        SetupScreen(
                            isDefault = isDefaultSmsApp,
                            hasPermissions = hasPermissions,
                            roleBlocked = roleBlocked,
                            permissionsBlocked = permissionsBlocked,
                            onRequestDefault = ::requestDefaultSmsApp,
                            onRequestPermissions = ::requestPermissions,
                            onOpenAppSettings = { PermissionManager.openAppSettings(this) },
                        )
                    } else {
                        MessagesNavGraph(
                            startWithRecipient = pendingRecipient,
                            startWithBody = pendingBody,
                        )
                    }
                  }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user can change the default app / permissions from settings while
        // we're backgrounded, so re-sync whenever we return to the foreground.
        refreshGates()
        if (isDefaultSmsApp) {
            roleBlocked = false
            pruneOldMessages()
        }
        if (hasPermissions) permissionsBlocked = false
    }

    /**
     * Handles relaunch via singleTask: a fresh SENDTO/SEND intent arrives here
     * rather than through onCreate, so re-parse it for the deep link.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseDeepLink(intent)
    }

    /** iOS "Keep Messages": drops anything older than the chosen window. */
    private fun pruneOldMessages() {
        // Recently Deleted keeps things for 30 days, then deletes them for good.
        lifecycleScope.launch { appContainer.smsRepository.purgeExpired() }
        val days = appContainer.appSettings.keepMessages.value.days ?: return
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        lifecycleScope.launch { appContainer.smsRepository.deleteOlderThan(cutoff) }
    }

    /** Re-reads the authoritative default-app + permission state into the gates. */
    private fun refreshGates() {
        isDefaultSmsApp = DefaultSmsAppManager.isDefaultSmsApp(this)
        hasPermissions = PermissionManager.hasRequiredPermissions(this)
    }

    /** Starts the platform-appropriate "set as default SMS app" request. */
    private fun requestDefaultSmsApp() {
        val intent = DefaultSmsAppManager.requestIntent(this)
        if (intent == null) {
            roleBlocked = true
            return
        }
        roleRequestStartedAt = SystemClock.elapsedRealtime()
        runCatching { defaultAppLauncher.launch(intent) }.onFailure {
            Log.w(TAG, "Could not start the default-SMS request", it)
            roleBlocked = true
        }
    }

    /**
     * Requests whatever is still missing: the required SMS permissions (normally
     * already granted by the role) plus the optional ones, the latter only once
     * so a "Don't allow" is respected instead of re-prompting on every launch.
     */
    private fun requestPermissions() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val askOptional = !prefs.getBoolean(KEY_ASKED_OPTIONAL, false)
        val toAsk = PermissionManager.missingRequired(this) +
            (if (askOptional) PermissionManager.missingOptional(this) else emptyList())
        if (toAsk.isEmpty()) {
            refreshGates()
            return
        }
        if (askOptional) prefs.edit().putBoolean(KEY_ASKED_OPTIONAL, true).apply()
        permissionLauncher.launch(toAsk.toTypedArray())
    }

    /**
     * Derives a recipient + body from an incoming intent and stores them as the
     * pending deep link. Supports:
     *  - ACTION_SENDTO / ACTION_SEND with an `sms:`/`smsto:`/`mms:`/`mmsto:` URI
     *    (recipient in the URI's scheme-specific part).
     *  - a message body from the `sms_body` extra or [Intent.EXTRA_TEXT].
     *
     * Clears the pending values when the intent carries no usable recipient so a
     * plain launcher tap doesn't re-trigger an old deep link.
     */
    private fun parseDeepLink(intent: Intent?) {
        if (intent == null) {
            pendingRecipient = null
            pendingBody = null
            return
        }

        val recipient = when (intent.action) {
            Intent.ACTION_SENDTO, Intent.ACTION_SEND, Intent.ACTION_VIEW ->
                recipientFromSmsUri(intent.data)
            else -> null
        }

        val body = intent.getStringExtra("sms_body")
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)

        pendingRecipient = recipient?.takeIf { it.isNotBlank() }
        pendingBody = body?.takeIf { it.isNotBlank() }
    }

    /**
     * Extracts the recipient phone number from an `sms:`/`smsto:`/`mms:`/`mmsto:`
     * URI. The number lives in the scheme-specific part, e.g. `smsto:+15551234567`,
     * and may be URL-encoded or carry a trailing `?body=` query we strip off.
     */
    private fun recipientFromSmsUri(data: Uri?): String? {
        if (data == null) return null
        val scheme = data.scheme?.lowercase() ?: return null
        if (scheme !in SMS_SCHEMES) return null

        // schemeSpecificPart is everything after "smsto:" up to a '#'; drop any
        // "?body=..." query the sender may have appended.
        val raw = data.schemeSpecificPart?.substringBefore('?')?.trim()
        if (raw.isNullOrBlank()) return null

        // Some senders pass multiple recipients separated by ',' or ';'; take the
        // first for a single-thread compose.
        val first = raw.split(',', ';').firstOrNull()?.trim()
        if (first.isNullOrBlank()) return null
        return Uri.decode(first).takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "MainActivity"
        const val PREFS = "setup"
        const val KEY_ASKED_OPTIONAL = "asked_optional_permissions"
        val SMS_SCHEMES = setOf("sms", "smsto", "mms", "mmsto")
    }
}
