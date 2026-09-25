package com.liquidglass.messages.ui.conversations

import androidx.activity.compose.BackHandler
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
    viewModel: ConversationListViewModel = viewModel(factory = ConversationListViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                onDeleteThread = viewModel::deleteThread,
                onOpenSettings = onOpenSettings,
                onMarkAllRead = { state.conversations.filter { it.hasUnread }.forEach { viewModel.markRead(it.threadId) } },
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

/**
 * iOS Messages inbox. Glass "⋯" and compose buttons float at the top; the large
 * title and search field scroll with the list. Tapping Search runs the iOS
 * transition: title and toolbar slide away, the field rises to the top and a
 * Cancel button slides in beside it.
 */
@Composable
fun ConversationListContent(
    state: ConversationListUiState,
    onSearchChange: (String) -> Unit,
    onConversationClick: (threadId: Long, address: String) -> Unit,
    onNewMessage: () -> Unit,
    onDeleteThread: (threadId: Long) -> Unit,
    onOpenSettings: () -> Unit = {},
    onMarkAllRead: () -> Unit = {},
) {
    val colors = LiquidTheme.colors
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val backdrop = rememberBackdrop()

    var searching by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    fun endSearch() {
        onSearchChange("")
        focusManager.clearFocus()
        keyboard?.hide()
        searching = false
    }
    BackHandler(enabled = searching, onBack = ::endSearch)
    BackHandler(enabled = menuOpen) { menuOpen = false }

    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 90 }
    }
    val topPadding by animateDpAsState(
        targetValue = if (searching) statusTop + 8.dp else statusTop + ToolbarHeight,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "listTop",
    )

    Box(Modifier.fillMaxSize().background(colors.listBackground)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .backdropSource(backdrop)
                .background(colors.listBackground),
            contentPadding = PaddingValues(top = topPadding, bottom = navBottom + 24.dp),
        ) {
            item(key = "title") {
                AnimatedVisibility(
                    visible = !searching,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Text(
                        text = "Messages",
                        style = IosType.largeTitle,
                        color = colors.primaryText,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
            }
            item(key = "search") {
                SearchBar(
                    query = state.searchQuery,
                    searching = searching,
                    onQueryChange = onSearchChange,
                    onFocus = { searching = true },
                    onCancel = ::endSearch,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                )
            }
            items(items = state.conversations, key = { it.threadId }) { conversation ->
                ConversationRow(
                    conversation = conversation,
                    onClick = {
                        if (searching) endSearch()
                        onConversationClick(conversation.threadId, conversation.address)
                    },
                    onDelete = { onDeleteThread(conversation.threadId) },
                )
            }
        }

        if (state.conversations.isEmpty() && !state.isLoading) {
            EmptyState(
                title = if (state.searchQuery.isBlank()) "No Messages" else "No Results",
                subtitle = if (state.searchQuery.isBlank()) {
                    "Tap the compose button to start a conversation."
                } else {
                    "No conversations match “${state.searchQuery.trim()}”."
                },
                icon = Icons.Rounded.ChatBubbleOutline,
                modifier = Modifier.fillMaxSize().padding(top = statusTop + 160.dp),
            )
        }

        AnimatedVisibility(
            visible = collapsed || searching,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            EdgeFade(top = true, height = statusTop + if (searching) 16.dp else ToolbarHeight + 16.dp)
        }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
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
                    GlassCircleButton(
                        icon = IosIcons.Ellipsis,
                        contentDescription = "More",
                        onClick = { menuOpen = true },
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    AnimatedVisibility(
                        visible = collapsed,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Text("Messages", style = IosType.headline, color = colors.primaryText)
                    }
                    GlassCircleButton(
                        icon = IosIcons.Compose,
                        contentDescription = "New message",
                        onClick = onNewMessage,
                        tint = colors.accent,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }

            // iOS 26 glass menu, anchored under the ⋯ button (in-window so the
            // glass can refract the list behind it).
            if (menuOpen) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            menuOpen = false
                        },
                )
            }
            AnimatedVisibility(
                visible = menuOpen,
                enter = scaleIn(spring(dampingRatio = 0.75f, stiffness = 500f), initialScale = 0.3f, transformOrigin = TransformOrigin(0f, 0f)) + fadeIn(),
                exit = scaleOut(tween(150), targetScale = 0.3f, transformOrigin = TransformOrigin(0f, 0f)) + fadeOut(tween(150)),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = ToolbarHeight),
            ) {
                GlassMenu(
                    items = listOf(
                        MenuEntry("Mark All as Read", IosIcons.Check) { onMarkAllRead() },
                        MenuEntry("Settings", IosIcons.Gear) { onOpenSettings() },
                    ),
                    onDismiss = { menuOpen = false },
                )
            }
        }
    }
}

/** A row in a [GlassMenu]. */
data class MenuEntry(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** iOS 26 context menu: a rounded Liquid Glass panel of label + icon rows. */
@Composable
fun GlassMenu(items: List<MenuEntry>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LiquidTheme.colors
    Column(
        modifier = modifier
            .width(250.dp)
            .glassControl(RoundedCornerShape(24.dp), colors.glassShadow, colors, GlassStyle.Menu)
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
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(entry.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text(entry.label, style = IosType.body, color = tint)
            }
        }
    }
}

/**
 * iOS search bar: magnifier + field in a capsule; once active, a Cancel button
 * slides in from the right and the field shrinks to make room.
 */
@Composable
private fun SearchBar(
    query: String,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onFocus: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LiquidTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(colors.fieldBackground)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    runCatching { focus.requestFocus() }
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(IosIcons.Search, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
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
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(colors.secondaryText)
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(IosIcons.Close, contentDescription = "Clear", tint = colors.listBackground, modifier = Modifier.size(10.dp))
                }
            }
        }
        AnimatedVisibility(
            visible = searching,
            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
        ) {
            Text(
                "Cancel",
                style = IosType.body,
                color = colors.accent,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
            )
        }
    }
}
