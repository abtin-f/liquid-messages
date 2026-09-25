package com.liquidglass.messages.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.liquidglass.messages.BuildConfig
import com.liquidglass.messages.appContainer
import com.liquidglass.messages.data.local.BubbleStyle
import com.liquidglass.messages.ui.chat.BubbleShape
import com.liquidglass.messages.ui.components.IconTile
import com.liquidglass.messages.ui.components.IosGroupedPage
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.components.IosRow
import com.liquidglass.messages.ui.components.IosSwitch
import com.liquidglass.messages.ui.components.iosSection
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme
import com.liquidglass.messages.util.DefaultSmsAppManager

/** iOS Settings palette for icon tiles. */
private val TileGreen = Color(0xFF34C759)
private val TileBlue = Color(0xFF007AFF)
private val TileRed = Color(0xFFFF3B30)
private val TileGray = Color(0xFF8E8E93)
private val TileOrange = Color(0xFFFF9500)
private val TileIndigo = Color(0xFF5856D6)

/**
 * Settings, laid out like iPhone Settings › Messages: access permissions,
 * bubble colour, SMS options, filtering and an about footer. Every row does
 * something real — no decorative toggles.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = LiquidTheme.colors
    val settings = context.appContainer.appSettings

    val bubbleStyle by settings.bubbleStyle.collectAsState()
    val deliveryReports by settings.deliveryReports.collectAsState()
    val characterCount by settings.characterCount.collectAsState()
    val contactPhotos by settings.showContactPhotos.collectAsState()

    // Permission/role state can change in system Settings; re-read on resume.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val isDefault = remember(refresh) { DefaultSmsAppManager.isDefaultSmsApp(context) }
    val hasContacts = remember(refresh) { granted(context, Manifest.permission.READ_CONTACTS) }
    val hasNotifications = remember(refresh) {
        Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)
    }
    val hasLocation = remember(refresh) { granted(context, Manifest.permission.ACCESS_COARSE_LOCATION) }

    IosGroupedPage(title = "Settings", onBack = onBack) {
        iosSection("app") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(IosIcons.Bubble, background = TileGreen, size = 58.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Liquid Messages", style = IosType.title2, color = colors.primaryText)
                    Text(
                        if (isDefault) "Default SMS app" else "Not your default SMS app",
                        style = IosType.subheadline,
                        color = if (isDefault) colors.secondaryText else colors.destructive,
                    )
                }
            }
        }

        iosSection("access", header = "Allow Messages to Access") {
            IosRow(
                title = "Contacts", icon = IosIcons.PersonCircle, iconBackground = TileGray,
                value = if (hasContacts) "On" else "Off", chevron = true,
                onClick = { openAppDetails(context) },
            )
            IosRow(
                title = "Notifications", icon = IosIcons.Bell, iconBackground = TileRed,
                value = if (hasNotifications) "On" else "Off", chevron = true,
                onClick = { openNotificationSettings(context) },
            )
            IosRow(
                title = "Location", icon = IosIcons.Location, iconBackground = TileBlue,
                value = if (hasLocation) "While Using" else "Never", chevron = true,
                showDivider = false,
                onClick = { openAppDetails(context) },
            )
        }

        iosSection("default", footer = "Only the default SMS app can send and receive text messages.") {
            IosRow(
                title = "Default Messaging App",
                value = if (isDefault) "Liquid Messages" else "Other",
                chevron = true,
                showDivider = false,
                onClick = { openDefaultApps(context) },
            )
        }

        iosSection("bubbles", header = "Message Bubbles") {
            BubbleChoice("Blue", BubbleStyle.BLUE, bubbleStyle, divider = true) { settings.setBubbleStyle(it) }
            BubbleChoice("Green", BubbleStyle.GREEN, bubbleStyle, divider = false) { settings.setBubbleStyle(it) }
        }

        iosSection(
            "sms",
            header = "SMS",
            footer = "Delivery reports ask the carrier to confirm each text reached the other phone. Some carriers charge for them.",
        ) {
            IosRow(title = "Delivery Reports", icon = IosIcons.Check, iconBackground = TileGreen) {
                IosSwitch(deliveryReports, settings::setDeliveryReports)
            }
            IosRow(title = "Character Count", icon = IosIcons.TextCount, iconBackground = TileOrange) {
                IosSwitch(characterCount, settings::setCharacterCount)
            }
            IosRow(title = "Show Contact Photos", icon = IosIcons.PersonCircle, iconBackground = TileIndigo, showDivider = false) {
                IosSwitch(contactPhotos, settings::setShowContactPhotos)
            }
        }

        iosSection("filter", header = "Message Filtering") {
            IosRow(
                title = "Blocked Contacts", icon = IosIcons.Block, iconBackground = TileRed,
                chevron = true, showDivider = false,
                onClick = { openBlockedNumbers(context) },
            )
        }

        iosSection("about", footer = "Liquid Messages ${BuildConfig.VERSION_NAME}\nYour messages stay on this phone. Nothing is uploaded.") {
            IosRow(
                title = "Version",
                value = BuildConfig.VERSION_NAME,
                showDivider = false,
            )
        }
    }
}

/** A selectable bubble-colour row with a live preview bubble and iOS checkmark. */
@Composable
private fun BubbleChoice(
    label: String,
    style: BubbleStyle,
    current: BubbleStyle,
    divider: Boolean,
    onPick: (BubbleStyle) -> Unit,
) {
    val colors = LiquidTheme.colors
    val fill = if (style == BubbleStyle.GREEN) {
        listOf(Color(0xFF4CD964), Color(0xFF30BE50))
    } else {
        listOf(Color(0xFF2E9BFF), Color(0xFF0A7AFF))
    }
    IosRow(title = label, showDivider = divider, onClick = { onPick(style) }) {
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(width = 54.dp, height = 28.dp)
                .background(Brush.verticalGradient(fill), BubbleShape(outgoing = true, hasTail = true)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Hi", style = IosType.footnote, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(end = 6.dp))
        }
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (style == current) {
                Icon(IosIcons.Check, contentDescription = "Selected", tint = colors.accent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun launch(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, "Couldn't open that setting.", Toast.LENGTH_SHORT).show() }
}

private fun openAppDetails(context: Context) = launch(
    context,
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(android.net.Uri.fromParts("package", context.packageName, null)),
)

private fun openNotificationSettings(context: Context) = launch(
    context,
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
)

private fun openDefaultApps(context: Context) = launch(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))

/** The system blocked-numbers list (open to the default SMS app). */
private fun openBlockedNumbers(context: Context) {
    val telecom = context.getSystemService(TelecomManager::class.java)
    val intent = runCatching { telecom?.createManageBlockedNumbersIntent() }.getOrNull()
    if (intent != null) launch(context, intent)
    else Toast.makeText(context, "Blocked contacts aren't available on this device.", Toast.LENGTH_SHORT).show()
}
