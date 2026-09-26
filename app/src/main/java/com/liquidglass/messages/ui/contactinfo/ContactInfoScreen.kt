package com.liquidglass.messages.ui.contactinfo

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.liquidglass.messages.appContainer
import com.liquidglass.messages.data.local.ChatWallpaper
import com.liquidglass.messages.data.model.Attachment
import com.liquidglass.messages.data.model.Contact
import com.liquidglass.messages.data.model.Message
import com.liquidglass.messages.ui.chat.ImageViewer
import com.liquidglass.messages.ui.chat.LocationCard
import com.liquidglass.messages.ui.chat.openExternally
import com.liquidglass.messages.ui.components.ContactAvatar
import com.liquidglass.messages.ui.components.GlassCapsule
import com.liquidglass.messages.ui.components.GlassCircleButton
import com.liquidglass.messages.ui.components.IosDialogs
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.components.IosRow
import com.liquidglass.messages.ui.components.IosSwitch
import com.liquidglass.messages.ui.glass.GlassStyle
import com.liquidglass.messages.ui.glass.LocalBackdrop
import com.liquidglass.messages.ui.glass.backdropSource
import com.liquidglass.messages.ui.glass.liquidGlass
import com.liquidglass.messages.ui.glass.rememberBackdrop
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidColors
import com.liquidglass.messages.ui.theme.LiquidTheme
import com.liquidglass.messages.ui.theme.LocalLiquidColors
import com.liquidglass.messages.ui.theme.darkLiquidColors
import com.liquidglass.messages.ui.theme.fontFamilyFor
import com.liquidglass.messages.ui.theme.wallpaperBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Tabs of the iOS 26 conversation details. */
private enum class DetailTab(val label: String) {
    INFO("Info"), BACKGROUNDS("Backgrounds"), PHOTOS("Photos"), LINKS("Links"), DOCUMENTS("Documents"), LOCATIONS("Locations"),
}

/**
 * iOS 26 conversation details: the conversation's own background fills the
 * page behind a centred avatar, the name and a row of round glass actions
 * (call · video · message). Below, a glass tab strip switches between Info,
 * Backgrounds, Photos, Links, Documents and Locations.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactInfoScreen(
    threadId: Long,
    address: String,
    onBack: () -> Unit,
    onConversationDeleted: () -> Unit = onBack,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val container = context.appContainer
    val base = LiquidTheme.colors

    var contact by remember(address) { mutableStateOf(Contact(number = address)) }
    var messages by remember(threadId) { mutableStateOf<List<Message>>(emptyList()) }
    var blocked by remember(address) { mutableStateOf(false) }
    var muted by remember(threadId) { mutableStateOf(container.threadPrefs.isMuted(threadId)) }
    var confirm by remember { mutableStateOf<ConfirmAction?>(null) }
    var recipients by remember(threadId) { mutableStateOf<List<Contact>>(emptyList()) }

    LaunchedEffect(address, threadId) {
        if (address.isNotBlank()) contact = container.smsRepository.resolveContact(address)
        if (threadId > 0) {
            messages = container.smsRepository.getMessages(threadId)
            val numbers = container.smsRepository.getRecipients(threadId)
            if (numbers.size > 1) recipients = numbers.map { container.smsRepository.resolveContact(it) }
        }
        blocked = withContext(Dispatchers.IO) { isBlocked(context, address) }
    }
    val shared = remember(messages) { SharedContent.from(messages) }
    val isGroup = recipients.size > 1
    val title = if (isGroup) recipients.joinToString(", ") { it.displayName.substringBefore(' ') } else contact.displayName

    // This conversation's background (its own, else the global one).
    val wallpapers by container.threadPrefs.wallpapers.collectAsState()
    val photos by container.threadPrefs.photoBackgrounds.collectAsState()
    val globalWallpaper by container.appSettings.wallpaper.collectAsState()
    val photoBg = photos[threadId]
    val wallpaper = wallpapers[threadId] ?: globalWallpaper
    val hasBackdrop = photoBg != null || wallpaper != ChatWallpaper.NONE
    // Over a background, iOS switches the page to its dark "vibrant" look.
    val colors = if (hasBackdrop && !base.isDark) darkLiquidColors() else base

    var tab by remember { mutableStateOf(DetailTab.INFO) }
    var searchOpen by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    BackHandler(enabled = searchOpen && viewing == null) { searchOpen = false }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) { saveBackgroundPhoto(context, threadId, uri) }
            if (path != null) container.threadPrefs.setPhotoBackground(threadId, path)
            else IosDialogs.alert("Couldn't Use Photo", "This photo couldn't be opened.")
        }
    }

    val backdrop = rememberBackdrop()
    CompositionLocalProvider(LocalLiquidColors provides colors) {
        Box(Modifier.fillMaxSize().background(colors.groupedBackground)) {
            // ---- Background layer (what the glass refracts) ----
            Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
                when {
                    photoBg != null -> AsyncImage(
                        model = File(photoBg),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(28.dp),
                    )
                    wallpaper != ChatWallpaper.NONE -> Box(
                        Modifier.fillMaxSize().background(wallpaperBrush(wallpaper, true) ?: Brush.linearGradient(listOf(colors.groupedBackground, colors.groupedBackground))),
                    )
                }
                if (hasBackdrop) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
            }

            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(top = 8.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // ---- Header: avatar, name, round glass actions ----
                    if (isGroup) GroupAvatars(recipients)
                    else ContactAvatar(name = contact.displayName, photoUri = contact.photoUri, size = 96.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        title,
                        style = IosType.title2.copy(fontSize = IosType.title2.fontSize * 1.1f),
                        fontFamily = fontFamilyFor(title),
                        color = colors.primaryText,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 72.dp),
                    )
                    val subtitle = when {
                        isGroup -> "${recipients.size} People"
                        contact.name != null -> contact.number
                        else -> null
                    }
                    subtitle?.let { Text(it, style = IosType.subheadline, color = colors.secondaryText) }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RoundAction(IosIcons.Phone, "Call") { dial(context, address) }
                        RoundAction(IosIcons.Video, "Video") {
                            IosDialogs.alert("FaceTime Unavailable", "Video calls aren't available for text messages yet.")
                        }
                        RoundAction(IosIcons.Envelope, "Message", onClick = onBack)
                    }
                    Spacer(Modifier.height(22.dp))

                    TabStrip(tab) { tab = it }
                    Spacer(Modifier.height(16.dp))

                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "tab",
                    ) { t ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            when (t) {
                                DetailTab.INFO -> InfoTab(
                                    contact = contact,
                                    address = address,
                                    isGroup = isGroup,
                                    recipients = recipients,
                                    messages = messages,
                                    muted = muted,
                                    blocked = blocked,
                                    onToggleMute = { on ->
                                        muted = on
                                        container.threadPrefs.setMuted(threadId, on)
                                    },
                                    onCopyNumber = {
                                        clipboard.setText(AnnotatedString(contact.number))
                                        IosDialogs.notice("Copied", IosIcons.Copy)
                                    },
                                    onSearch = { searchOpen = true },
                                    onAddContact = { openOrAddContact(context, it) },
                                    onBlock = { confirm = if (blocked) ConfirmAction.UNBLOCK else ConfirmAction.BLOCK },
                                    onReportJunk = {
                                        IosDialogs.alert(
                                            "Report Junk?",
                                            "This number will be blocked and the conversation deleted from this phone.",
                                            IosDialogs.Action("Cancel", IosDialogs.Role.CANCEL),
                                            IosDialogs.Action("Report Junk", IosDialogs.Role.DESTRUCTIVE) {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) { setBlocked(context, address, true) }
                                                    container.smsRepository.deleteThread(threadId)
                                                    onConversationDeleted()
                                                }
                                            },
                                        )
                                    },
                                    onDelete = { confirm = ConfirmAction.DELETE },
                                )
                                DetailTab.BACKGROUNDS -> BackgroundsTab(
                                    photo = photoBg,
                                    current = wallpapers[threadId],
                                    onPickPhoto = {
                                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    onPick = { w ->
                                        container.threadPrefs.setPhotoBackground(threadId, null)
                                        container.threadPrefs.setWallpaper(threadId, w)
                                    },
                                )
                                DetailTab.PHOTOS -> PhotosTab(shared.photos) { if (it.isImage) viewing = it else openExternally(context, it) }
                                DetailTab.LINKS -> LinksTab(shared.links) { openUrl(context, it) }
                                DetailTab.DOCUMENTS -> DocumentsTab(shared.documents)
                                DetailTab.LOCATIONS -> LocationsTab(shared.locations)
                            }
                        }
                    }
                }

                // ---- Floating bar: back · Edit ----
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    GlassCircleButton(
                        icon = IosIcons.ChevronLeft,
                        contentDescription = "Back",
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    if (!isGroup) {
                        GlassCapsule(onClick = { openOrAddContact(context, contact) }, modifier = Modifier.align(Alignment.CenterEnd).height(44.dp)) {
                            Text("Edit", style = IosType.body, color = colors.primaryText, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            // Search pushes over the page like a navigation push.
            val push = tween<IntOffset>(durationMillis = 380, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f))
            androidx.compose.animation.AnimatedVisibility(
                visible = searchOpen,
                enter = slideInHorizontally(push) { it },
                exit = slideOutHorizontally(push) { it },
            ) {
                CompositionLocalProvider(LocalLiquidColors provides base) {
                    SearchPage(messages, title, onBack = { searchOpen = false })
                }
            }
            viewing?.let { ImageViewer(it, onClose = { viewing = null }) }
        }
    }

    confirm?.let { action ->
        LaunchedEffect(action) {
            confirm = null
            IosDialogs.alert(
                action.title,
                action.message,
                IosDialogs.Action("Cancel", IosDialogs.Role.CANCEL),
                IosDialogs.Action(action.confirmLabel, IosDialogs.Role.DESTRUCTIVE) {
                    scope.launch {
                        when (action) {
                            ConfirmAction.BLOCK, ConfirmAction.UNBLOCK -> {
                                val ok = withContext(Dispatchers.IO) { setBlocked(context, address, action == ConfirmAction.BLOCK) }
                                if (ok) {
                                    blocked = action == ConfirmAction.BLOCK
                                    IosDialogs.notice(if (blocked) "Blocked" else "Unblocked", IosIcons.Block)
                                } else {
                                    IosDialogs.alert("Couldn't Update Blocked Contacts", "The block list couldn't be changed on this phone.")
                                }
                            }
                            ConfirmAction.DELETE -> {
                                container.smsRepository.deleteThread(threadId)
                                onConversationDeleted()
                            }
                        }
                    }
                },
            )
        }
    }
}

/* ------------------------------ Header pieces ------------------------------ */

/** Round glass action (call / video / message) with an icon only, iOS 26 style. */
@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    GlassCircleButton(
        icon = icon,
        contentDescription = label,
        onClick = onClick,
        size = 58.dp,
        iconSize = 24.dp,
        tint = LiquidTheme.colors.primaryText,
    )
}

/** The glass segmented tab strip (scrolls sideways when it doesn't fit). */
@Composable
private fun TabStrip(selected: DetailTab, onSelect: (DetailTab) -> Unit) {
    val colors = LiquidTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailTab.entries.forEach { t ->
            val isSel = t == selected
            Box(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .then(if (isSel) Modifier.liquidGlass(RoundedCornerShape(50), GlassStyle(blur = 10.dp)) else Modifier)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(t) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.label,
                    style = IosType.body,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSel) colors.primaryText else colors.secondaryText,
                )
            }
        }
    }
}

/** A translucent rounded group of rows, readable over any background. */
@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val colors = LiquidTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.groupedCell.copy(alpha = if (colors.isDark) 0.72f else 0.9f)),
        content = content,
    )
}

/* ------------------------------ Tabs ------------------------------ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoTab(
    contact: Contact,
    address: String,
    isGroup: Boolean,
    recipients: List<Contact>,
    messages: List<Message>,
    muted: Boolean,
    blocked: Boolean,
    onToggleMute: (Boolean) -> Unit,
    onCopyNumber: () -> Unit,
    onSearch: () -> Unit,
    onAddContact: (Contact) -> Unit,
    onBlock: () -> Unit,
    onReportJunk: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val context = LocalContext.current
    if (!isGroup) {
        Card {
            Column(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { dial(context, address) }, onLongClick = onCopyNumber)
                    .padding(horizontal = 20.dp, vertical = 11.dp),
            ) {
                Text("mobile", style = IosType.footnote, color = colors.primaryText)
                Text(contact.number, style = IosType.body, color = colors.accent)
            }
            if (contact.name == null) {
                Box(Modifier.padding(start = 20.dp).fillMaxWidth().height(0.5.dp).background(colors.divider))
                IosRow(title = "Add to Contacts", titleColor = colors.accent, showDivider = false, onClick = { onAddContact(contact) })
            }
        }
    } else {
        Card {
            recipients.forEachIndexed { i, person ->
                IosRow(
                    title = person.displayName,
                    subtitle = if (person.name != null) person.number else null,
                    showDivider = i < recipients.lastIndex,
                    chevron = true,
                    onClick = { onAddContact(person) },
                    trailing = {
                        Icon(
                            IosIcons.Phone, contentDescription = "Call ${person.displayName}", tint = colors.accent,
                            modifier = Modifier.padding(start = 8.dp).size(20.dp).clickable { dial(context, person.number) },
                        )
                    },
                )
            }
        }
    }
    Card {
        IosRow(title = "Search in Conversation", icon = IosIcons.Search, iconBackground = Color(0xFF8E8E93), chevron = true, onClick = onSearch)
        IosRow(title = "Hide Alerts", icon = IosIcons.BellSlash, iconBackground = Color(0xFF5856D6), showDivider = false) {
            IosSwitch(muted, onToggleMute)
        }
    }
    if (messages.isNotEmpty()) {
        Card {
            IosRow(title = "Messages", value = messages.size.toString())
            IosRow(title = "Sent by You", value = messages.count { it.isOutgoing }.toString())
            IosRow(
                title = "First Message",
                value = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(messages.first().timestamp)),
                showDivider = false,
            )
        }
    }
    Card {
        IosRow(title = if (blocked) "Unblock this Caller" else "Block this Caller", titleColor = colors.destructive, onClick = onBlock)
        if (!isGroup && contact.name == null) {
            IosRow(title = "Report Junk", titleColor = colors.destructive, onClick = onReportJunk)
        }
        IosRow(title = "Delete Conversation", titleColor = colors.destructive, showDivider = false, onClick = onDelete)
    }
}

/** iOS 26 Backgrounds: round swatches, the chosen one ringed with a check badge. */
@Composable
private fun BackgroundsTab(
    photo: String?,
    current: ChatWallpaper?,
    onPickPhoto: () -> Unit,
    onPick: (ChatWallpaper?) -> Unit,
) {
    val options: List<Pair<String, (@Composable () -> Unit)>> = buildList {
        add("Default" to { Swatch(null, null, selected = photo == null && current == null, label = "Aa") { onPick(null) } })
        add("None" to { Swatch(ChatWallpaper.NONE, null, selected = photo == null && current == ChatWallpaper.NONE) { onPick(ChatWallpaper.NONE) } })
        add("Photo" to { Swatch(null, photo, selected = photo != null, icon = IosIcons.Photos, onClick = onPickPhoto) })
        ChatWallpaper.entries.filter { it != ChatWallpaper.NONE }.forEach { w ->
            add(w.label to { Swatch(w, null, selected = photo == null && current == w) { onPick(w) } })
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        options.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (label, swatch) ->
                    Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        swatch()
                        Spacer(Modifier.height(6.dp))
                        Text(label, style = IosType.footnote, color = LiquidTheme.colors.primaryText, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.width(80.dp)) }
            }
        }
    }
}

@Composable
private fun Swatch(
    wallpaper: ChatWallpaper?,
    photo: String?,
    selected: Boolean,
    label: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, spring(dampingRatio = 0.6f), label = "ring")
    Box(Modifier.size(72.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .border(ring, colors.accent, CircleShape)
                .padding(if (selected) 5.dp else 0.dp)
                .clip(CircleShape)
                .background(colors.groupedCell.copy(alpha = 0.6f))
                .then(wallpaper?.let { w -> wallpaperBrush(w, true)?.let { Modifier.background(it) } } ?: Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                photo != null -> AsyncImage(File(photo), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                icon != null -> Icon(icon, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(28.dp))
                label != null -> Text(label, style = IosType.headline, color = colors.secondaryText)
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-2).dp, y = (-2).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .border(2.dp, colors.groupedBackground, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(IosIcons.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(12.dp)) }
        }
    }
}

@Composable
private fun PhotosTab(photos: List<Attachment>, onOpen: (Attachment) -> Unit) {
    if (photos.isEmpty()) return EmptyTab("No Photos", "Photos and videos you share in this conversation appear here.")
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        photos.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { a -> Thumb(a, Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp))) { onOpen(a) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LinksTab(links: List<String>, onOpen: (String) -> Unit) {
    if (links.isEmpty()) return EmptyTab("No Links", "Links shared in this conversation appear here.")
    Card {
        links.forEachIndexed { i, url ->
            IosRow(
                title = Uri.parse(url).host?.removePrefix("www.") ?: url,
                subtitle = url,
                icon = IosIcons.Link,
                iconBackground = Color(0xFF34C759),
                chevron = true,
                showDivider = i < links.lastIndex,
                onClick = { onOpen(url) },
            )
        }
    }
}

@Composable
private fun DocumentsTab(docs: List<Pair<Attachment, Long>>) {
    val context = LocalContext.current
    if (docs.isEmpty()) return EmptyTab("No Documents", "Files, contact cards and audio shared here appear in this list.")
    Card {
        docs.forEachIndexed { i, (a, at) ->
            val size = if (a.sizeBytes > 0) android.text.format.Formatter.formatShortFileSize(context, a.sizeBytes) + " · " else ""
            IosRow(
                title = a.name ?: a.mimeType.substringAfter('/').uppercase(),
                subtitle = size + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at)),
                icon = IosIcons.Doc,
                iconBackground = Color(0xFFFF9500),
                chevron = true,
                showDivider = i < docs.lastIndex,
                onClick = { openExternally(context, a) },
            )
        }
    }
}

@Composable
private fun LocationsTab(locations: List<Pair<com.liquidglass.messages.data.location.SharedLocation, Long>>) {
    if (locations.isEmpty()) return EmptyTab("No Locations", "Locations shared in this conversation appear here.")
    val colors = LiquidTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        locations.forEach { (loc, at) ->
            Column {
                LocationCard(location = loc, outgoing = false, onLongPress = {})
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at)),
                    style = IosType.footnote,
                    color = colors.secondaryText,
                    modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyTab(title: String, body: String) {
    val colors = LiquidTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = IosType.title2, color = colors.primaryText)
        Spacer(Modifier.height(6.dp))
        Text(body, style = IosType.subheadline, color = colors.secondaryText, textAlign = TextAlign.Center)
    }
}

/** Copies a picked photo into private storage, downscaled for a background. */
private fun saveBackgroundPhoto(context: Context, threadId: Long, uri: Uri): String? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1440) sample *= 2
    val bmp = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val dir = File(context.filesDir, "backgrounds").apply { mkdirs() }
    // New name each time so image caches never show the old picture.
    dir.listFiles { f -> f.name.startsWith("thread_${threadId}_") }?.forEach { it.delete() }
    val file = File(dir, "thread_${threadId}_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 86, it) }
    file.absolutePath
}.getOrNull()

private enum class ConfirmAction(val title: String, val message: String, val confirmLabel: String) {
    BLOCK("Block this Caller?", "You won't receive calls or messages from this number.", "Block"),
    UNBLOCK("Unblock this Caller?", "You'll receive calls and messages from this number again.", "Unblock"),
    DELETE("Delete Conversation?", "This conversation will be moved to Recently Deleted. You can recover it there for 30 days.", "Delete"),
}

private fun start(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { IosDialogs.alert("No App Available", "No app on this phone can open this.") }
}

private fun dial(context: Context, number: String) =
    start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

private fun openUrl(context: Context, url: String) = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

private fun shareNumber(context: Context, contact: Contact) {
    val text = if (contact.name != null) "${contact.name}\n${contact.number}" else contact.number
    start(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}

/** Opens the saved contact, or the "add contact" editor pre-filled with the number. */
private fun openOrAddContact(context: Context, contact: Contact) {
    val lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(contact.number))
    val contactUri = runCatching {
        context.contentResolver.query(
            lookup, arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY), null, null, null,
        )?.use { c -> if (c.moveToFirst()) ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1)) else null }
    }.getOrNull()
    if (contactUri != null) {
        start(context, Intent(Intent.ACTION_VIEW, contactUri))
    } else {
        start(
            context,
            Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
                .putExtra(ContactsContract.Intents.Insert.PHONE, contact.number),
        )
    }
}

/** System block list (the default SMS app may read and write it). */
private fun isBlocked(context: Context, number: String): Boolean = runCatching {
    BlockedNumberContract.canCurrentUserBlockNumbers(context) && BlockedNumberContract.isBlocked(context, number)
}.getOrDefault(false)

private fun setBlocked(context: Context, number: String, block: Boolean): Boolean = runCatching {
    if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) return false
    if (block) {
        val values = ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
        context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
    } else {
        BlockedNumberContract.unblock(context, number)
    }
    true
}.getOrDefault(false)

/** Overlapping avatars for a group, like the iOS group photo. */
@Composable
private fun GroupAvatars(people: List<Contact>) {
    Box(Modifier.size(width = 124.dp, height = 104.dp)) {
        people.take(3).forEachIndexed { i, p ->
            val (x, y, size) = when (i) {
                0 -> Triple(0.dp, 20.dp, 64.dp)
                1 -> Triple(56.dp, 0.dp, 68.dp)
                else -> Triple(46.dp, 52.dp, 52.dp)
            }
            ContactAvatar(
                name = p.displayName, photoUri = p.photoUri, size = size,
                modifier = Modifier.offset(x = x, y = y).border(2.dp, LiquidTheme.colors.groupedBackground, CircleShape),
            )
        }
    }
}
