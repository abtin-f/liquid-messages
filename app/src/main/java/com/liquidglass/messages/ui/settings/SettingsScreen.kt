package com.liquidglass.messages.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.liquidglass.messages.BuildConfig
import com.liquidglass.messages.appContainer
import com.liquidglass.messages.data.local.AppSettings
import com.liquidglass.messages.data.local.AppearanceMode
import com.liquidglass.messages.data.local.BubbleStyle
import com.liquidglass.messages.data.local.ChatWallpaper
import com.liquidglass.messages.data.local.GlassLook
import com.liquidglass.messages.data.local.KeepMessages
import com.liquidglass.messages.data.local.NotificationPreviews
import com.liquidglass.messages.data.local.TextSize
import com.liquidglass.messages.ui.chat.BubbleShape
import com.liquidglass.messages.ui.components.IconTile
import com.liquidglass.messages.ui.components.IosDialogs
import com.liquidglass.messages.ui.components.IosGroupedPage
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.components.IosRow
import com.liquidglass.messages.ui.components.IosSwitch
import com.liquidglass.messages.ui.components.iosSection
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme
import com.liquidglass.messages.ui.theme.darkLiquidColors
import com.liquidglass.messages.ui.theme.lightLiquidColors
import com.liquidglass.messages.ui.theme.sentBubbleFill
import com.liquidglass.messages.ui.theme.wallpaperBrush
import com.liquidglass.messages.util.DefaultSmsAppManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** iOS Settings palette for icon tiles. */
private val TileGreen = Color(0xFF34C759)
private val TileBlue = Color(0xFF007AFF)
private val TileRed = Color(0xFFFF3B30)
private val TileGray = Color(0xFF8E8E93)
private val TileOrange = Color(0xFFFF9500)
private val TileIndigo = Color(0xFF5856D6)
private val TilePink = Color(0xFFFF2D55)
private val TileTeal = Color(0xFF30B0C7)
private val TilePurple = Color(0xFFAF52DE)

/** Pages of the Settings stack. */
private enum class Page { ROOT, PERSONALIZE, KEEP, PREVIEWS }

/**
 * Settings, laid out like iOS 27 Settings › Apps › Messages, plus a
 * Personalization page (appearance, bubble colour, text size, conversation
 * backgrounds, Liquid Glass). Sub-pages push and pop with the iOS slide.
 * Every row does something real — no decorative toggles.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var page by remember { mutableStateOf(Page.ROOT) }
    BackHandler(enabled = page != Page.ROOT) { page = Page.ROOT }

    val push = tween<IntOffset>(durationMillis = 380, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f))
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (targetState != Page.ROOT) {
                slideInHorizontally(push) { it } togetherWith slideOutHorizontally(push) { -it / 3 }
            } else {
                slideInHorizontally(push) { -it / 3 } togetherWith slideOutHorizontally(push) { it }
            }
        },
        label = "settingsPage",
    ) { p ->
        when (p) {
            Page.ROOT -> RootPage(onBack = onBack, open = { page = it })
            Page.PERSONALIZE -> PersonalizePage(onBack = { page = Page.ROOT })
            Page.KEEP -> KeepMessagesPage(onBack = { page = Page.ROOT })
            Page.PREVIEWS -> PreviewsPage(onBack = { page = Page.ROOT })
        }
    }
}

@Composable
private fun RootPage(onBack: () -> Unit, open: (Page) -> Unit) {
    val context = LocalContext.current
    val colors = LiquidTheme.colors
    val settings = context.appContainer.appSettings

    val bubbleStyle by settings.bubbleStyle.collectAsState()
    val appearance by settings.appearance.collectAsState()
    val wallpaper by settings.wallpaper.collectAsState()
    val deliveryReports by settings.deliveryReports.collectAsState()
    val characterCount by settings.characterCount.collectAsState()
    val lowQuality by settings.lowQualityImages.collectAsState()
    val filterUnknown by settings.filterUnknown.collectAsState()
    val autoPlay by settings.autoPlayEffects.collectAsState()
    val swipeReply by settings.swipeToReply.collectAsState()
    val haptics by settings.haptics.collectAsState()
    val keep by settings.keepMessages.collectAsState()
    val previews by settings.notificationPreviews.collectAsState()

    // Permission/role state can change in system Settings; re-read on resume.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val isDefault = remember(refresh) { DefaultSmsAppManager.isDefaultSmsApp(context) }
    val hasContacts = remember(refresh) { granted(context, Manifest.permission.READ_CONTACTS) }
    val hasNotifications = remember(refresh) {
        Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)
    }
    val hasLocation = remember(refresh) { granted(context, Manifest.permission.ACCESS_COARSE_LOCATION) }

    IosGroupedPage(title = "Messages", onBack = onBack) {
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

        iosSection(
            "personalize",
            header = "Personalization",
            footer = "Colours, dark mode, text size, backgrounds and Liquid Glass.",
        ) {
            IosRow(
                title = "Personalize", icon = IosIcons.Paintbrush, iconBackground = TilePurple,
                value = "${appearance.label} · ${bubbleStyle.label}", chevron = true,
                onClick = { open(Page.PERSONALIZE) },
            )
            IosRow(
                title = "Conversation Backgrounds", icon = IosIcons.Wallpaper, iconBackground = TileTeal,
                value = wallpaper.label, chevron = true, showDivider = false,
                onClick = { open(Page.PERSONALIZE) },
            )
        }

        iosSection(
            "messages",
            header = "Messages",
            footer = "Effects play when a message sent with an effect arrives. Swipe a bubble right to reply.",
        ) {
            IosRow(title = "Show Contact Photos", icon = IosIcons.PersonCircle, iconBackground = TileIndigo) {
                val photos by settings.showContactPhotos.collectAsState()
                IosSwitch(photos, settings::setShowContactPhotos)
            }
            IosRow(title = "Auto-Play Message Effects", icon = IosIcons.Sparkles, iconBackground = TilePink) {
                IosSwitch(autoPlay, settings::setAutoPlayEffects)
            }
            IosRow(title = "Swipe to Reply", icon = IosIcons.Reply, iconBackground = TileBlue) {
                IosSwitch(swipeReply, settings::setSwipeToReply)
            }
            IosRow(title = "Haptic Feedback", icon = IosIcons.Haptics, iconBackground = TileGray, showDivider = false) {
                IosSwitch(haptics, settings::setHaptics)
            }
        }

        iosSection(
            "notifications",
            header = "Notifications",
        ) {
            IosRow(
                title = "Show Previews", icon = IosIcons.Eye, iconBackground = TileRed,
                value = previews.label, chevron = true, showDivider = false,
                onClick = { open(Page.PREVIEWS) },
            )
        }

        iosSection(
            "sms",
            header = "SMS/MMS",
            footer = "Delivery reports ask the carrier to confirm each text reached the other phone. Some carriers charge for them. Low Quality Image Mode sends smaller photos.",
        ) {
            IosRow(title = "Delivery Reports", icon = IosIcons.Check, iconBackground = TileGreen) {
                IosSwitch(deliveryReports, settings::setDeliveryReports)
            }
            IosRow(title = "Character Count", icon = IosIcons.TextCount, iconBackground = TileOrange) {
                IosSwitch(characterCount, settings::setCharacterCount)
            }
            IosRow(title = "Low Quality Image Mode", icon = IosIcons.Photos, iconBackground = TileTeal, showDivider = false) {
                IosSwitch(lowQuality, settings::setLowQualityImages)
            }
        }

        iosSection(
            "filter",
            header = "Message Filtering",
            footer = "Sort messages from people who aren't in your contacts into a separate list you can pick from the filter menu.",
        ) {
            IosRow(title = "Filter Unknown Senders", icon = IosIcons.PersonQuestion, iconBackground = TileBlue) {
                IosSwitch(filterUnknown, settings::setFilterUnknown)
            }
            IosRow(
                title = "Blocked Contacts", icon = IosIcons.Block, iconBackground = TileRed,
                chevron = true, showDivider = false,
                onClick = { openBlockedNumbers(context) },
            )
        }

        iosSection("history", header = "Message History") {
            IosRow(
                title = "Keep Messages", icon = IosIcons.Archive, iconBackground = TileGray,
                value = keep.label, chevron = true, showDivider = false,
                onClick = { open(Page.KEEP) },
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

/* ------------------------------ Personalization ------------------------------ */

@Composable
internal fun PersonalizePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = context.appContainer.appSettings
    val bubble by settings.bubbleStyle.collectAsState()
    val appearance by settings.appearance.collectAsState()
    val textSize by settings.textSize.collectAsState()
    val wallpaper by settings.wallpaper.collectAsState()
    val glass by settings.glassLook.collectAsState()
    val photos by settings.showContactPhotos.collectAsState()
    val colors = LiquidTheme.colors

    IosGroupedPage(title = "Personalization", onBack = onBack, backLabel = "Messages") {
        iosSection("preview", footer = "Changes apply everywhere as you make them.") {
            ChatPreview(bubble, wallpaper)
        }

        iosSection("appearance", header = "Appearance") {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                AppearanceMode.entries.forEach { mode ->
                    AppearanceOption(mode, selected = mode == appearance) { settings.setAppearance(mode) }
                }
            }
        }

        iosSection("bubble", header = "Bubble Color", footer = "The colour of the messages you send.") {
            BubbleColorPicker(bubble, settings::setBubbleStyle)
        }

        iosSection(
            "text",
            header = "Text Size",
            footer = "Drag the slider to make text in Messages bigger or smaller.",
        ) {
            TextSizeSlider(textSize, settings::setTextSize)
        }

        iosSection("wallpaper", header = "Conversation Background") {
            WallpaperPicker(wallpaper, settings::setWallpaper)
        }

        iosSection(
            "glass",
            header = "Liquid Glass",
            footer = "Clear keeps the glass see-through. Tinted makes buttons and bars more opaque for extra contrast.",
        ) {
            Box(Modifier.padding(16.dp)) {
                SegmentedControl(
                    options = GlassLook.entries.map { it.label },
                    selected = glass.ordinal,
                    onSelect = { settings.setGlassLook(GlassLook.entries[it]) },
                )
            }
        }

        iosSection("photos") {
            IosRow(title = "Show Contact Photos", showDivider = false) {
                IosSwitch(photos, settings::setShowContactPhotos)
            }
        }

        iosSection("reset") {
            IosRow(
                title = "Reset Personalization",
                titleColor = colors.destructive,
                showDivider = false,
                onClick = {
                    IosDialogs.alert(
                        "Reset Personalization?",
                        "Appearance, bubble colour, text size, background and glass go back to their defaults.",
                        IosDialogs.Action("Cancel", IosDialogs.Role.CANCEL),
                        IosDialogs.Action("Reset", IosDialogs.Role.DESTRUCTIVE) { resetPersonalization(settings) },
                    )
                },
            )
        }
    }
}

private fun resetPersonalization(settings: AppSettings) {
    settings.setAppearance(AppearanceMode.SYSTEM)
    settings.setBubbleStyle(BubbleStyle.BLUE)
    settings.setTextSize(TextSize.DEFAULT)
    settings.setWallpaper(ChatWallpaper.NONE)
    settings.setGlassLook(GlassLook.CLEAR)
    settings.setShowContactPhotos(true)
}

/** A tiny live conversation showing the current colours and background. */
@Composable
private fun ChatPreview(bubble: BubbleStyle, wallpaper: ChatWallpaper) {
    val colors = LiquidTheme.colors
    val brush = wallpaperBrush(wallpaper, colors.isDark)
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.chatBackground)
            .then(if (brush != null) Modifier.background(brush) else Modifier)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PreviewBubble("Did you see the new look? 👀", outgoing = false, bubble)
        PreviewBubble("Yes! Dark mode and my own colour 😍", outgoing = true, bubble)
        PreviewBubble("سلام! این هم پیام فارسی", outgoing = true, bubble, tail = true)
    }
}

@Composable
private fun PreviewBubble(text: String, outgoing: Boolean, style: BubbleStyle, tail: Boolean = !outgoing) {
    val colors = LiquidTheme.colors
    val fill = if (outgoing) Brush.verticalGradient(sentBubbleFill(style, colors.isDark)) else Brush.linearGradient(listOf(colors.receivedBubble, colors.receivedBubble))
    Box(Modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text,
            style = IosType.body,
            fontFamily = com.liquidglass.messages.ui.theme.fontFamilyFor(text),
            color = if (outgoing) Color.White else colors.receivedText,
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .wrapContentWidth(if (outgoing) Alignment.End else Alignment.Start)
                .background(fill, BubbleShape(outgoing = outgoing, hasTail = tail))
                .padding(start = if (outgoing) 12.dp else 18.dp, end = if (outgoing) 18.dp else 12.dp, top = 7.dp, bottom = 7.dp),
        )
    }
}

/** iOS Display & Brightness: a phone thumbnail per mode with a radio circle. */
@Composable
private fun AppearanceOption(mode: AppearanceMode, selected: Boolean, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        val shape = RoundedCornerShape(12.dp)
        Box(
            Modifier
                .size(width = 64.dp, height = 116.dp)
                .shadow(3.dp, shape)
                .clip(shape)
                .border(if (selected) BorderStroke(2.dp, colors.accent) else BorderStroke(0.5.dp, colors.divider), shape),
        ) {
            when (mode) {
                AppearanceMode.LIGHT -> MiniPhone(dark = false, Modifier.fillMaxSize())
                AppearanceMode.DARK -> MiniPhone(dark = true, Modifier.fillMaxSize())
                AppearanceMode.SYSTEM -> Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxSize().clipToBounds()) {
                        MiniPhone(dark = false, Modifier.requiredWidth(64.dp).height(116.dp).offset(x = 16.dp))
                    }
                    Box(Modifier.weight(1f).fillMaxSize().clipToBounds()) {
                        MiniPhone(dark = true, Modifier.requiredWidth(64.dp).height(116.dp).offset(x = (-16).dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(mode.label, style = IosType.subheadline, color = colors.primaryText)
        Spacer(Modifier.height(8.dp))
        RadioCircle(selected)
    }
}

/** A miniature Messages screen used as the appearance thumbnail. */
@Composable
private fun MiniPhone(dark: Boolean, modifier: Modifier) {
    val c = if (dark) darkLiquidColors() else lightLiquidColors()
    Column(modifier.background(c.listBackground).padding(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.width(30.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.primaryText.copy(alpha = 0.85f)))
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(c.fieldBackground))
        repeat(4) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.avatarBottom))
                Spacer(Modifier.width(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.width(24.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.primaryText.copy(alpha = 0.7f)))
                    Box(Modifier.width(32.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.secondaryText))
                }
            }
        }
    }
}

@Composable
private fun RadioCircle(selected: Boolean) {
    val colors = LiquidTheme.colors
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accent else Color.Transparent)
            .border(if (selected) 0.dp else 1.5.dp, if (selected) Color.Transparent else colors.tertiaryText, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(IosIcons.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(13.dp))
    }
}

/** Row of colour swatches; the chosen one gets the iOS double ring. */
@Composable
private fun BubbleColorPicker(current: BubbleStyle, onPick: (BubbleStyle) -> Unit) {
    val colors = LiquidTheme.colors
    Column(Modifier.padding(vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            BubbleStyle.entries.forEach { style ->
                val selected = style == current
                val ring by animateDpAsState(if (selected) 2.5.dp else 0.dp, spring(dampingRatio = 0.6f), label = "ring")
                Box(
                    Modifier
                        .size(38.dp)
                        .border(ring, if (selected) colors.primaryText.copy(alpha = 0.35f) else Color.Transparent, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(sentBubbleFill(style, colors.isDark)))
                        .clickable { onPick(style) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(IosIcons.Check, contentDescription = style.label, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
        Text(
            current.label,
            style = IosType.subheadline,
            color = colors.secondaryText,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp),
        )
    }
}

/** iOS Text Size: small A · stepped track · large A, snapping to each step. */
@Composable
private fun TextSizeSlider(current: TextSize, onPick: (TextSize) -> Unit) {
    val colors = LiquidTheme.colors
    val steps = TextSize.entries
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            "Aa  ${current.label}",
            style = IosType.body,
            color = colors.primaryText,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A", fontSize = 13.sp, color = colors.primaryText)
            Spacer(Modifier.width(12.dp))
            BoxWithConstraints(Modifier.weight(1f).height(36.dp)) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                val knob = 28.dp
                val knobPx = with(density) { knob.toPx() }
                val trackPx = widthPx - knobPx
                fun indexAt(x: Float) = ((x - knobPx / 2) / trackPx * (steps.size - 1)).roundToInt().coerceIn(0, steps.lastIndex)
                val knobX by animateDpAsState(
                    with(density) { (trackPx * current.ordinal / (steps.size - 1)).toDp() },
                    spring(dampingRatio = 0.8f, stiffness = 600f),
                    label = "knob",
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { onPick(steps[indexAt(it.x)]) }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ -> onPick(steps[indexAt(change.position.x)]) }
                        },
                ) {
                    // Track with tick marks.
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(horizontal = knob / 2)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.fieldBackground),
                    )
                    Row(
                        Modifier.align(Alignment.Center).padding(horizontal = knob / 2).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        repeat(steps.size) { Box(Modifier.width(1.5.dp).height(12.dp).background(colors.tertiaryText)) }
                    }
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = knobX)
                            .size(knob)
                            .shadow(3.dp, CircleShape)
                            .background(Color.White, CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text("A", fontSize = 22.sp, color = colors.primaryText)
        }
    }
}

/** Grid of background swatches with names, iOS 27 Conversation Backgrounds style. */
@Composable
private fun WallpaperPicker(current: ChatWallpaper, onPick: (ChatWallpaper) -> Unit) {
    val colors = LiquidTheme.colors
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChatWallpaper.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { w ->
                    val selected = w == current
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, indication = null) { onPick(w) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val brush = wallpaperBrush(w, colors.isDark)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.72f)
                                .clip(shape)
                                .background(colors.chatBackground)
                                .then(if (brush != null) Modifier.background(brush) else Modifier)
                                .border(if (selected) BorderStroke(2.5.dp, colors.accent) else BorderStroke(0.5.dp, colors.divider), shape)
                                .padding(8.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                Box(Modifier.width(34.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(colors.receivedBubble))
                                Box(
                                    Modifier.align(Alignment.End).width(40.dp).height(12.dp).clip(RoundedCornerShape(6.dp))
                                        .background(colors.sentBubbleBottom),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            w.label,
                            style = IosType.footnote,
                            color = if (selected) colors.accent else colors.secondaryText,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** UISegmentedControl: grey track with a sliding white (or grey) thumb. */
@Composable
private fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = LiquidTheme.colors
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.fieldBackground)
            .padding(2.dp),
    ) {
        val segment: Dp = maxWidth / options.size
        val x by animateDpAsState(segment * selected, spring(dampingRatio = 0.8f, stiffness = 500f), label = "seg")
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxSize()
                .shadow(2.dp, RoundedCornerShape(50))
                .background(if (colors.isDark) Color(0xFF636366) else Color.White, RoundedCornerShape(50)),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = IosType.subheadline, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium, color = colors.primaryText)
                }
            }
        }
    }
}

/* ------------------------------ Choice pages ------------------------------ */

@Composable
private fun KeepMessagesPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = context.appContainer.appSettings
    val keep by settings.keepMessages.collectAsState()
    val scope = rememberCoroutineScope()

    IosGroupedPage(title = "Keep Messages", onBack = onBack, backLabel = "Messages") {
        iosSection("keep") {
            KeepMessages.entries.forEachIndexed { i, option ->
                CheckRow(option.label, option == keep, divider = i < KeepMessages.entries.lastIndex) {
                    val days = option.days
                    if (days == null || (keep.days != null && days >= keep.days!!)) {
                        settings.setKeepMessages(option)
                    } else {
                        // iOS asks before throwing history away.
                        IosDialogs.alert(
                            "Delete Older Messages?",
                            "This will permanently delete all text messages and attachments on this phone that are older than ${option.label.lowercase()}.",
                            IosDialogs.Action("Cancel", IosDialogs.Role.CANCEL),
                            IosDialogs.Action("Delete", IosDialogs.Role.DESTRUCTIVE) {
                                settings.setKeepMessages(option)
                                scope.launch {
                                    val cutoff = System.currentTimeMillis() - days * 86_400_000L
                                    val removed = context.appContainer.smsRepository.deleteOlderThan(cutoff)
                                    IosDialogs.notice(if (removed > 0) "Deleted $removed Messages" else "Nothing to Delete", IosIcons.Trash)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewsPage(onBack: () -> Unit) {
    val settings = LocalContext.current.appContainer.appSettings
    val current by settings.notificationPreviews.collectAsState()
    IosGroupedPage(title = "Show Previews", onBack = onBack, backLabel = "Messages") {
        iosSection("previews", footer = "Choose when message text appears in notifications.") {
            NotificationPreviews.entries.forEachIndexed { i, option ->
                CheckRow(option.label, option == current, divider = i < NotificationPreviews.entries.lastIndex) {
                    settings.setNotificationPreviews(option)
                }
            }
        }
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, divider: Boolean, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    IosRow(title = title, showDivider = divider, onClick = onClick) {
        val tint by animateColorAsState(if (checked) colors.accent else Color.Transparent, label = "check")
        Icon(IosIcons.Check, contentDescription = if (checked) "Selected" else null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/* ------------------------------ System intents ------------------------------ */

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun launch(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { IosDialogs.alert("Can't Open Settings", "This setting isn't available on this phone.") }
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
    else IosDialogs.alert("Blocked Contacts Unavailable", "This phone doesn't provide a blocked contacts list.")
}
