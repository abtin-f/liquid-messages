package com.liquidglass.messages.ui.conversations

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.animation.core.animate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import com.liquidglass.messages.appContainer
import com.liquidglass.messages.data.model.Conversation
import com.liquidglass.messages.ui.components.ContactAvatar
import com.liquidglass.messages.ui.components.IosDialogs
import com.liquidglass.messages.ui.theme.fontFamilyFor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liquidglass.messages.ui.components.EdgeFade
import com.liquidglass.messages.ui.components.EmptyState
import com.liquidglass.messages.ui.components.GlassCapsule
import com.liquidglass.messages.ui.components.GlassCircleButton
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.components.glassControl
import com.liquidglass.messages.ui.glass.GlassStyle
import com.liquidglass.messages.ui.glass.LocalBackdrop
import com.liquidglass.messages.ui.glass.backdropSource
import com.liquidglass.messages.ui.glass.rememberBackdrop
import com.liquidglass.messages.ui.newmessage.NewMessageSheet
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme

/**
 * Stateful entry point for the conversation list. Also hosts the New Message
 * page sheet, which (like iOS) presents over the list instead of navigating.
 */
@Composable
fun ConversationListScreen(
    onConversationClick: (threadId: Long, address: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecentlyDeleted: () -> Unit = {},
    viewModel: ConversationListViewModel = viewModel(factory = ConversationListViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val container = androidx.compose.ui.platform.LocalContext.current.appContainer
    val threadPrefs = container.threadPrefs
    val pinned by threadPrefs.pinned.collectAsStateWithLifecycle()
    val muted by threadPrefs.muted.collectAsStateWithLifecycle()
    val filterUnknown by container.appSettings.filterUnknown.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // One spring drives both the sheet and the "card" effect on the list below it.
    val sheet by animateFloatAsState(
        targetValue = if (sheetOpen) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 320f),
        label = "sheet",
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // iOS page sheet: the presenting screen recedes into a card.
                    val s = 1f - 0.07f * sheet
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    translationY = statusTop.toPx() * 0.6f * sheet
                    shape = RoundedCornerShape((16 * sheet).dp)
                    clip = sheet > 0f
                },
        ) {
            ConversationListContent(
                state = state,
                onSearchChange = viewModel::onSearchChange,
                onConversationClick = onConversationClick,
                onNewMessage = { sheetOpen = true },
                onDeleteThread = { id ->
                    threadPrefs.setPinned(id, false)
                    viewModel.deleteThread(id)
                },
                onOpenSettings = onOpenSettings,
                onOpenRecentlyDeleted = onOpenRecentlyDeleted,
                onMarkAllRead = { state.conversations.filter { it.hasUnread }.forEach { viewModel.markRead(it.threadId) } },
                pinned = pinned,
                muted = muted,
                filterUnknownEnabled = filterUnknown,
                onTogglePin = { id -> threadPrefs.setPinned(id, !threadPrefs.isPinned(id)) },
                onToggleMute = { id ->
                    val on = !threadPrefs.isMuted(id)
                    threadPrefs.setMuted(id, on)
                    IosDialogs.notice(if (on) "Alerts Hidden" else "Alerts On", if (on) IosIcons.BellSlash else IosIcons.Bell)
                },
                onToggleRead = { c -> if (c.hasUnread) viewModel.markRead(c.threadId) else viewModel.markUnread(c.threadId) },
            )
            if (sheet > 0f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f * sheet)))
            }
        }

        AnimatedVisibility(
            visible = sheetOpen,
            enter = slideInVertically(spring(dampingRatio = 0.92f, stiffness = 320f)) { it },
            exit = slideOutVertically(tween(260)) { it },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = statusTop + 10.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            ) {
                NewMessageSheet(
                    onDismiss = { sheetOpen = false },
                    onMessageSent = { threadId, address ->
                        sheetOpen = false
                        onConversationClick(threadId, address)
                    },
                )
            }
        }
    }
}

/** Height of the floating toolbar row (glass buttons) under the status bar. */
private val ToolbarHeight = 56.dp
/** Height of the floating bottom bar (search capsule + compose). */
private val BottomBarHeight = 52.dp

private enum class ToolbarMenu { FILTER, EDIT }

/** Inbox filters from the iOS Messages filter menu. */
enum class InboxFilter(val title: String) {
    ALL("Messages"), KNOWN("Known Senders"), UNKNOWN("Unknown Senders"), UNREAD("Unread Messages"),
}

/**
 * iOS 26 Messages inbox: a glass "Edit" pill and filter button float at the
 * top, pinned conversations sit above the list, and — new in iOS 26 — the
 * search field lives at the bottom as a floating Liquid Glass capsule (with
 * dictation) beside the compose button. Tapping it rises above the keyboard
 * and a close button replaces compose.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationListContent(
    state: ConversationListUiState,
    onSearchChange: (String) -> Unit,
    onConversationClick: (threadId: Long, address: String) -> Unit,
    onNewMessage: () -> Unit,
    onDeleteThread: (threadId: Long) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenRecentlyDeleted: () -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    pinned: List<Long> = emptyList(),
    muted: Set<Long> = emptySet(),
    filterUnknownEnabled: Boolean = false,
    onTogglePin: (Long) -> Unit = {},
    onToggleMute: (Long) -> Unit = {},
    onToggleRead: (Conversation) -> Unit = {},
) {
    val colors = LiquidTheme.colors
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val backdrop = rememberBackdrop()
    val scope = rememberCoroutineScope()

    var searching by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<ToolbarMenu?>(null) }
    val menuOpen = menu != null
    var lastMenu by remember { mutableStateOf(ToolbarMenu.FILTER) }
    menu?.let { lastMenu = it }
    var filter by remember { mutableStateOf(InboxFilter.ALL) }
    var contextTarget by remember { mutableStateOf<Conversation?>(null) }
    LaunchedEffect(filter) { if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0) }
    LaunchedEffect(filterUnknownEnabled) {
        if (!filterUnknownEnabled && (filter == InboxFilter.KNOWN || filter == InboxFilter.UNKNOWN)) filter = InboxFilter.ALL
    }

    fun endSearch() {
        onSearchChange("")
        focusManager.clearFocus()
        keyboard?.hide()
        searching = false
    }
    BackHandler(enabled = searching, onBack = ::endSearch)
    BackHandler(enabled = menuOpen) { menu = null }
    BackHandler(enabled = contextTarget != null) { contextTarget = null }

    // Header items: 0 = large title, 1 = search field (while not searching).
    // Title gone → small title in the bar; search field gone → it docks at the bottom.
    val titleGone by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 70 } }
    val searchDocked by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 1 || (listState.firstVisibleItemIndex == 1 && listState.firstVisibleItemScrollOffset > 40) }
    }
    val scrolled = titleGone

    // ---- Data: pins on top, the rest filtered ----
    val showPins = !searching && filter == InboxFilter.ALL
    val pinnedConversations = remember(state.conversations, pinned, showPins) {
        if (!showPins) emptyList() else {
            val byId = state.conversations.associateBy { it.threadId }
            pinned.mapNotNull { byId[it] }
        }
    }
    val rows = remember(state.conversations, pinned, showPins, filter) {
        val pinnedSet = pinned.toHashSet()
        state.conversations.filter { c ->
            (!showPins || c.threadId !in pinnedSet) && when (filter) {
                InboxFilter.ALL -> true
                InboxFilter.KNOWN -> c.contactName != null
                InboxFilter.UNKNOWN -> c.contactName == null && !c.isGroup
                InboxFilter.UNREAD -> c.hasUnread
            }
        }
    }
    val unknownUnread = remember(state.conversations) { state.conversations.count { it.contactName == null && !it.isGroup && it.hasUnread } }

    val contextBlur by animateDpAsState(if (contextTarget != null) 20.dp else 0.dp, tween(240), label = "ctxBlur")

    Box(Modifier.fillMaxSize().background(colors.listBackground)) {
        Box(Modifier.fillMaxSize().then(if (contextBlur > 0.dp) Modifier.blur(contextBlur) else Modifier)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .backdropSource(backdrop)
                    .background(colors.listBackground),
                contentPadding = PaddingValues(
                    top = statusTop + if (searching) 12.dp else ToolbarHeight,
                    bottom = navBottom + BottomBarHeight + 28.dp,
                ),
            ) {
                if (!searching) {
                    item(key = "largeTitle") {
                        Text(
                            filter.title,
                            style = IosType.largeTitle,
                            color = colors.primaryText,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                        )
                    }
                    item(key = "topSearch") {
                        TopSearchField(
                            onClick = { searching = true },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                        )
                    }
                }
                if (pinnedConversations.isNotEmpty()) {
                    item(key = "pins") {
                        PinnedGrid(
                            pins = pinnedConversations,
                            onClick = { onConversationClick(it.threadId, it.address) },
                            onLongPress = { contextTarget = it },
                        )
                    }
                }
                items(items = rows, key = { it.threadId }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        onClick = {
                            if (searching) endSearch()
                            onConversationClick(conversation.threadId, conversation.address)
                        },
                        onDelete = { onDeleteThread(conversation.threadId) },
                        muted = conversation.threadId in muted,
                        pinned = conversation.threadId in pinned,
                        onLongPress = { contextTarget = conversation },
                        onTogglePin = { togglePin(conversation, pinned, onTogglePin) },
                        onToggleMute = { onToggleMute(conversation.threadId) },
                        onToggleRead = { onToggleRead(conversation) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (rows.isEmpty() && pinnedConversations.isEmpty() && !state.isLoading) {
                EmptyState(
                    title = when {
                        state.searchQuery.isNotBlank() -> "No Results"
                        filter == InboxFilter.UNREAD -> "No Unread Messages"
                        else -> "No Messages"
                    },
                    subtitle = when {
                        state.searchQuery.isNotBlank() -> "No conversations match “${state.searchQuery.trim()}”."
                        filter != InboxFilter.ALL -> "Nothing in ${filter.title}."
                        else -> "Tap the compose button to start a conversation."
                    },
                    icon = Icons.Rounded.ChatBubbleOutline,
                    modifier = Modifier.fillMaxSize().padding(top = statusTop + 160.dp),
                )
            }

            // Content passing under the floating chrome fades out, iOS 26 style.
            AnimatedVisibility(
                visible = scrolled || searching,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                EdgeFade(top = true, height = statusTop + ToolbarHeight + 12.dp)
            }

            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                // ---- Top toolbar: Edit · (title when scrolled) · Filter ----
                AnimatedVisibility(
                    visible = !searching,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(ToolbarHeight)
                            .padding(horizontal = 16.dp),
                    ) {
                        GlassCapsule(
                            onClick = { menu = ToolbarMenu.EDIT },
                            modifier = Modifier.align(Alignment.CenterStart).height(44.dp),
                        ) {
                            Text("Edit", style = IosType.body, color = colors.primaryText, modifier = Modifier.padding(horizontal = 18.dp))
                        }
                        AnimatedVisibility(
                            visible = scrolled,
                            enter = fadeIn(tween(160)),
                            exit = fadeOut(tween(120)),
                            modifier = Modifier.align(Alignment.Center),
                        ) {
                            Text(filter.title, style = IosType.headline, color = colors.primaryText)
                        }
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlassCircleButton(
                                icon = IosIcons.Filter,
                                contentDescription = "Filter",
                                onClick = { menu = ToolbarMenu.FILTER },
                                tint = if (filter == InboxFilter.ALL) colors.primaryText else colors.accent,
                            )
                            // At the top of the list compose sits beside Filter; once the
                            // search docks at the bottom, compose moves down next to it.
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !searchDocked,
                                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.6f),
                                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.6f),
                            ) {
                                Row {
                                    Spacer(Modifier.width(10.dp))
                                    GlassCircleButton(
                                        icon = IosIcons.Compose,
                                        contentDescription = "New message",
                                        onClick = onNewMessage,
                                        tint = colors.accent,
                                    )
                                }
                            }
                        }
                    }
                }

                // ---- Bottom bar: search capsule + compose / close ----
                BottomSearchBar(
                    query = state.searchQuery,
                    searching = searching,
                    showField = searchDocked || searching,
                    onQueryChange = onSearchChange,
                    onFocus = { searching = true },
                    onClose = ::endSearch,
                    onCompose = onNewMessage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )

                // ---- Toolbar menus (anchored to their buttons) ----
                if (menuOpen) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                menu = null
                            },
                    )
                }
                val showEdit = (menu ?: lastMenu) == ToolbarMenu.EDIT
                val origin = if (showEdit) TransformOrigin(0f, 0f) else TransformOrigin(1f, 0f)
                AnimatedVisibility(
                    visible = menuOpen,
                    enter = scaleIn(spring(dampingRatio = 0.75f, stiffness = 500f), initialScale = 0.3f, transformOrigin = origin) + fadeIn(),
                    exit = scaleOut(tween(150), targetScale = 0.3f, transformOrigin = origin) + fadeOut(tween(150)),
                    modifier = Modifier
                        .align(if (showEdit) Alignment.TopStart else Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = ToolbarHeight),
                ) {
                    val entries = if (showEdit) listOf(
                        MenuEntry("New Group", IosIcons.PersonCircle) { onNewMessage() },
                        MenuEntry("Mark All as Read", IosIcons.Envelope) { onMarkAllRead() },
                        MenuEntry("Settings", IosIcons.Gear) { onOpenSettings() },
                    ) else buildList {
                        add(MenuEntry("Messages", IosIcons.Bubble, checked = filter == InboxFilter.ALL) { filter = InboxFilter.ALL })
                        if (filterUnknownEnabled) {
                            add(MenuEntry("Known Senders", IosIcons.PersonCircle, checked = filter == InboxFilter.KNOWN) { filter = InboxFilter.KNOWN })
                            add(
                                MenuEntry(
                                    "Unknown Senders", IosIcons.PersonQuestion,
                                    subtitle = if (unknownUnread > 0) "$unknownUnread New" else null,
                                    checked = filter == InboxFilter.UNKNOWN,
                                ) { filter = InboxFilter.UNKNOWN },
                            )
                        }
                        add(MenuEntry("Unread Messages", IosIcons.Envelope, checked = filter == InboxFilter.UNREAD) { filter = InboxFilter.UNREAD })
                        add(MenuEntry("Recently Deleted", IosIcons.Trash, dividerAfter = true) { onOpenRecentlyDeleted() })
                        add(MenuEntry("Manage Filtering", icon = null) { onOpenSettings() })
                    }
                    GlassMenu(items = entries, onDismiss = { menu = null })
                }
            }
        }

        // ---- Long-press context menu (lifted row + glass actions) ----
        ConversationContextMenu(
            target = contextTarget,
            pinned = pinned,
            muted = muted,
            onDismiss = { contextTarget = null },
            onOpen = { onConversationClick(it.threadId, it.address) },
            onTogglePin = { togglePin(it, pinned, onTogglePin) },
            onToggleMute = { onToggleMute(it.threadId) },
            onToggleRead = onToggleRead,
            onDelete = { onDeleteThread(it.threadId) },
        )
    }
}

/** Pins (with iOS's nine-pin limit) and confirms with a HUD. */
private fun togglePin(c: Conversation, pinned: List<Long>, onTogglePin: (Long) -> Unit) {
    val isPinned = c.threadId in pinned
    if (!isPinned && pinned.size >= com.liquidglass.messages.data.local.ThreadPrefs.MAX_PINS) {
        IosDialogs.alert("Can't Pin More", "You can pin up to ${com.liquidglass.messages.data.local.ThreadPrefs.MAX_PINS} conversations.")
        return
    }
    onTogglePin(c.threadId)
    IosDialogs.notice(if (isPinned) "Unpinned" else "Pinned", if (isPinned) IosIcons.PinSlash else IosIcons.Pin)
}

/**
 * iOS pinned conversations: big round avatars, three to a row, name below
 * and a blue dot for unread — above the regular list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedGrid(
    pins: List<Conversation>,
    onClick: (Conversation) -> Unit,
    onLongPress: (Conversation) -> Unit,
) {
    val colors = LiquidTheme.colors
    val haptics = LocalHapticFeedback.current
    val size = if (pins.size <= 3) 86.dp else 72.dp
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        pins.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { c ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onClick(c) },
                                onLongClick = {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    onLongPress(c)
                                },
                            )
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            ContactAvatar(name = c.displayName, photoUri = c.photoUri, size = size)
                            if (c.hasUnread) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .size(16.dp)
                                        .background(colors.listBackground, CircleShape)
                                        .padding(3.dp)
                                        .background(colors.accent, CircleShape),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            c.displayName,
                            style = IosType.footnote,
                            fontFamily = fontFamilyFor(c.displayName),
                            color = if (c.hasUnread) colors.primaryText else colors.secondaryText,
                            fontWeight = if (c.hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The iOS long-press menu for a conversation: everything else blurs, a
 * preview card of the conversation lifts out, and a glass menu of actions
 * sits under it.
 */
@Composable
private fun ConversationContextMenu(
    target: Conversation?,
    pinned: List<Long>,
    muted: Set<Long>,
    onDismiss: () -> Unit,
    onOpen: (Conversation) -> Unit,
    onTogglePin: (Conversation) -> Unit,
    onToggleMute: (Conversation) -> Unit,
    onToggleRead: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
) {
    val colors = LiquidTheme.colors
    var shown by remember { mutableStateOf<Conversation?>(null) }
    if (target != null) shown = target

    AnimatedVisibility(visible = target != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (colors.isDark) 0.35f else 0.12f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = target != null,
        enter = scaleIn(spring(dampingRatio = 0.72f, stiffness = 420f), initialScale = 0.85f) + fadeIn(tween(150)),
        exit = scaleOut(tween(160), targetScale = 0.9f) + fadeOut(tween(160)),
        modifier = Modifier.fillMaxSize(),
    ) {
        val c = shown ?: return@AnimatedVisibility
        val isPinned = c.threadId in pinned
        val isMuted = c.threadId in muted
        Column(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            // Preview card.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.listBackground)
                    .clickable { onDismiss(); onOpen(c) }
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ContactAvatar(name = c.displayName, photoUri = c.photoUri, size = 64.dp)
                Spacer(Modifier.height(8.dp))
                Text(c.displayName, style = IosType.headline, fontFamily = fontFamilyFor(c.displayName), color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                val preview = remember(c.snippet) { snippetText(c.snippet) }
                Text(
                    preview,
                    style = IosType.body,
                    fontFamily = fontFamilyFor(preview),
                    color = if (c.isOutgoingSnippet) Color.White else colors.receivedText,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(if (c.isOutgoingSnippet) Alignment.End else Alignment.Start)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (c.isOutgoingSnippet) colors.sentBubbleBottom else colors.receivedBubble)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            GlassMenu(
                items = listOf(
                    MenuEntry(if (isPinned) "Unpin" else "Pin", if (isPinned) IosIcons.PinSlash else IosIcons.Pin) { onTogglePin(c) },
                    MenuEntry(if (c.hasUnread) "Mark as Read" else "Mark as Unread", IosIcons.Envelope) { onToggleRead(c) },
                    MenuEntry(if (isMuted) "Show Alerts" else "Hide Alerts", if (isMuted) IosIcons.Bell else IosIcons.BellSlash, dividerAfter = true) {
                        onToggleMute(c)
                    },
                    MenuEntry("Delete", IosIcons.Trash, destructive = true) {
                        confirmDeleteConversation(onDelete = { onDelete(c) })
                    },
                ),
                onDismiss = onDismiss,
            )
        }
    }
}

/** A row in a [GlassMenu]. */
data class MenuEntry(
    val label: String,
    val icon: ImageVector?,
    val destructive: Boolean = false,
    /** Filter-style checkmark on the leading edge. */
    val checked: Boolean = false,
    /** Draws iOS's group separator under this row. */
    val dividerAfter: Boolean = false,
    /** Small grey second line ("1 New"). */
    val subtitle: String? = null,
    val onClick: () -> Unit,
)

/**
 * iOS 26 menu: a rounded Liquid Glass panel. Rows lead with an optional
 * checkmark, then the SF-style icon, then the label (and a small subtitle).
 */
@Composable
fun GlassMenu(items: List<MenuEntry>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LiquidTheme.colors
    val anyChecks = items.any { it.checked }
    Column(
        modifier = modifier
            .width(260.dp)
            .glassControl(RoundedCornerShape(26.dp), colors.glassShadow, colors, GlassStyle.Menu)
            .padding(vertical = 6.dp),
    ) {
        items.forEach { entry ->
            val tint = if (entry.destructive) colors.destructive else colors.primaryText
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        entry.onClick()
                    }
                    .padding(horizontal = 16.dp, vertical = if (entry.subtitle != null) 8.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (anyChecks) {
                    Box(Modifier.width(22.dp)) {
                        if (entry.checked) Icon(IosIcons.Check, contentDescription = "Selected", tint = tint, modifier = Modifier.size(15.dp))
                    }
                }
                if (entry.icon != null) {
                    Icon(entry.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(entry.label, style = IosType.body, color = tint)
                    entry.subtitle?.let { Text(it, style = IosType.footnote, color = colors.secondaryText) }
                }
            }
            if (entry.dividerAfter) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).height(0.5.dp).background(colors.divider))
            }
        }
    }
}

/**
 * iOS 26 bottom search: a glass capsule (magnifier · field · mic) with the
 * compose button beside it. While searching, the compose button turns into a
 * close button and the bar rides above the keyboard.
 */
@Composable
private fun BottomSearchBar(
    query: String,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onFocus: () -> Unit,
    onClose: () -> Unit,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
    /** The capsule itself; hidden while the search field still sits at the top. */
    showField: Boolean = true,
) {
    val colors = LiquidTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    // Dictation into the search field (the mic in the capsule).
    val dictation = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val text = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) {
            onFocus()
            onQueryChange(text)
        }
    }

    // Tapping the top field starts searching here, above the keyboard.
    LaunchedEffect(searching) { if (searching) runCatching { focus.requestFocus() } }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
        androidx.compose.animation.AnimatedVisibility(
            visible = showField,
            enter = fadeIn(tween(180)) + expandHorizontally(spring(dampingRatio = 0.85f, stiffness = 420f), expandFrom = Alignment.End),
            exit = fadeOut(tween(140)) + shrinkHorizontally(tween(200), shrinkTowards = Alignment.End),
        ) {
        GlassCapsule(
            modifier = Modifier.fillMaxWidth().height(BottomBarHeight),
            onClick = { runCatching { focus.requestFocus() } },
        ) {
            Row(
                Modifier.fillMaxSize().padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(IosIcons.Search, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onFocusChanged { if (it.isFocused) onFocus() },
                        singleLine = true,
                        textStyle = IosType.body.copy(
                            color = colors.primaryText,
                            textDirection = androidx.compose.ui.text.style.TextDirection.Content,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Search", style = IosType.body, color = colors.secondaryText)
                            inner()
                        },
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(remember { MutableInteractionSource() }, indication = null) { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(18.dp).clip(CircleShape).background(colors.secondaryText),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(IosIcons.Close, contentDescription = "Clear", tint = colors.listBackground, modifier = Modifier.size(10.dp))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(remember { MutableInteractionSource() }, indication = null) {
                                runCatching {
                                    dictation.launch(
                                        android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM),
                                    )
                                }.onFailure {
                                    IosDialogs.alert("Dictation Unavailable", "Dictation isn't available on this phone.")
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(IosIcons.Mic, contentDescription = "Dictate", tint = colors.primaryText, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = showField,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.6f),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.6f),
        ) {
            Row {
                Spacer(Modifier.width(10.dp))
                GlassCircleButton(
                    icon = if (searching) IosIcons.Close else IosIcons.Compose,
                    contentDescription = if (searching) "Close search" else "New message",
                    onClick = if (searching) onClose else onCompose,
                    tint = if (searching) colors.primaryText else colors.accent,
                    size = BottomBarHeight,
                    iconSize = if (searching) 18.dp else 23.dp,
                )
            }
        }
    }
}

/** The search field under the large title (a button: searching happens in the docked bar). */
@Composable
private fun TopSearchField(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LiquidTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(colors.fieldBackground)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(IosIcons.Search, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text("Search", style = IosType.body, color = colors.secondaryText, modifier = Modifier.weight(1f))
        Icon(IosIcons.Mic, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(17.dp))
    }
}
