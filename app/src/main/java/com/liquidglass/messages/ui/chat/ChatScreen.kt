package com.liquidglass.messages.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.TransformOrigin
import com.liquidglass.messages.appContainer
import com.liquidglass.messages.data.schedule.ScheduledMessage
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liquidglass.messages.data.model.Message
import com.liquidglass.messages.data.model.MessageEffect
import com.liquidglass.messages.data.model.Reaction
import com.liquidglass.messages.ui.chat.effects.EffectPicker
import com.liquidglass.messages.ui.chat.reactions.ReactionBar
import com.liquidglass.messages.ui.components.ContactAvatar
import com.liquidglass.messages.ui.components.EdgeFade
import com.liquidglass.messages.ui.components.GlassCapsule
import com.liquidglass.messages.ui.components.GlassCircleButton
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.components.glassControl
import com.liquidglass.messages.ui.glass.GlassStyle
import com.liquidglass.messages.ui.glass.LocalBackdrop
import com.liquidglass.messages.ui.glass.backdropSource
import com.liquidglass.messages.ui.glass.rememberBackdrop
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme
import com.liquidglass.messages.util.TimeFormat
import kotlinx.coroutines.launch

/** Gap between consecutive messages (ms) beyond which a timestamp is inserted. */
private const val SEPARATOR_GAP_MS = 60L * 60L * 1000L

/** Height of the floating header below the status bar (avatar + name pill). */
private val HeaderHeight = 92.dp

/**
 * Stateful entry point for a single conversation. Wires the [ChatViewModel],
 * collects its state lifecycle-aware, and delegates rendering to [ChatContent].
 */
@Composable
fun ChatScreen(
    threadId: Long,
    address: String,
    onBack: () -> Unit,
    onOpenInfo: (Long, String) -> Unit = { _, _ -> },
    initialText: String? = null,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(threadId, address)),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val selectedEffect by viewModel.selectedEffect.collectAsStateWithLifecycle()
    val sendLaterAt by viewModel.sendLaterAt.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val replyTo by viewModel.replyTo.collectAsStateWithLifecycle()
    val characterCount by LocalContext.current.appContainer.appSettings.characterCount.collectAsStateWithLifecycle()

    // A shared/SENDTO body pre-fills the composer once.
    LaunchedEffect(initialText) {
        if (!initialText.isNullOrBlank() && viewModel.inputText.value.isEmpty()) {
            viewModel.onInputChange(initialText)
        }
    }

    ChatContent(
        state = uiState,
        inputText = inputText,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::sendMessage,
        onBack = onBack,
        selectedEffect = selectedEffect,
        onReact = viewModel::setReaction,
        onEffectSelected = viewModel::onEffectSelected,
        onTitleClick = { onOpenInfo(viewModel.threadId, address) },
        onRetry = viewModel::retry,
        onDelete = viewModel::deleteMessage,
        sendLaterAt = sendLaterAt,
        onSetSendLater = viewModel::setSendLater,
        onCancelScheduled = viewModel::cancelScheduled,
        onSendScheduledNow = viewModel::sendScheduledNow,
        onSendText = viewModel::sendText,
        onAppendText = viewModel::appendToComposer,
        showCharacterCount = characterCount,
        attachments = attachments,
        onAddAttachment = viewModel::addAttachment,
        onRemoveAttachment = viewModel::removeAttachment,
        replyTo = replyTo,
        onReplyTo = viewModel::setReplyTo,
    )
}

/** One rendered row in the message list: a bubble or a timestamp header. */
private sealed interface ChatRow {
    val key: String

    data class Bubble(
        val message: Message,
        val isFirstInGroup: Boolean,
        val isLastInGroup: Boolean,
        val showStatus: Boolean,
    ) : ChatRow {
        override val key get() = "m${message.id}"
    }

    data class Separator(val day: String, val time: String, val anchorId: Long) : ChatRow {
        override val key get() = "s$anchorId"
    }
}

/**
 * Stateless chat UI, laid out like iOS 26 Messages: messages scroll edge to
 * edge under a floating glass header (back button · avatar + name · video) and
 * a floating composer, with soft fades where content meets the chrome.
 */
@Composable
fun ChatContent(
    state: ChatUiState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    selectedEffect: MessageEffect = MessageEffect.NONE,
    onReact: (Long, Reaction) -> Unit = { _, _ -> },
    onEffectSelected: (MessageEffect) -> Unit = {},
    onTitleClick: () -> Unit = {},
    onRetry: (Long) -> Unit = {},
    onDelete: (Long) -> Unit = {},
    sendLaterAt: Long? = null,
    onSetSendLater: (Long?) -> Unit = {},
    onCancelScheduled: (Long) -> Unit = {},
    onSendScheduledNow: (Long) -> Unit = {},
    onSendText: (String) -> Unit = {},
    onAppendText: (String) -> Unit = {},
    showCharacterCount: Boolean = false,
    attachments: List<android.net.Uri> = emptyList(),
    onAddAttachment: (android.net.Uri) -> Unit = {},
    onRemoveAttachment: (android.net.Uri) -> Unit = {},
    replyTo: Message? = null,
    onReplyTo: (Message?) -> Unit = {},
) {
    val colors = LiquidTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val messages = state.messages

    var menuTarget by remember { mutableStateOf<Message?>(null) }
    val effectTriggers = remember { mutableStateMapOf<Long, Int>() }
    val autoPlayed = remember { mutableSetOf<Long>() }
    var showEffects by remember { mutableStateOf(false) }
    var appsOpen by remember { mutableStateOf(false) }
    var showSendLater by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<com.liquidglass.messages.data.model.Attachment?>(null) }

    // MMS pickers. The system photo picker and document picker need no permissions;
    // the camera writes into our FileProvider cache.
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        uris.forEach(onAddAttachment)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onAddAttachment)
    }
    var cameraTarget by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        cameraTarget?.let { if (ok) onAddAttachment(it) }
        cameraTarget = null
    }
    fun launchCamera() {
        val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
        val file = java.io.File(dir, "photo-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        cameraTarget = uri
        runCatching { takePicture.launch(uri) }.onFailure {
            Toast.makeText(context, "No camera app available.", Toast.LENGTH_SHORT).show()
        }
    }
    var scheduledTarget by remember { mutableStateOf<ScheduledMessage?>(null) }
    val shareLocation = rememberLocationSharer(onSendText)
    val shareContact = rememberContactSharer(onAppendText)
    BackHandler(enabled = appsOpen) { appsOpen = false }
    val animatedIds = remember { mutableSetOf<Long>() }

    // History loaded when the chat opens must not pop in — only messages that
    // arrive afterwards animate. Seeded synchronously during composition (a
    // plain set, not snapshot state) so the first frame already sees it.
    val seeded = remember { booleanArrayOf(false) }
    val historyIds = remember { mutableSetOf<Long>() }
    if (!seeded[0] && messages.isNotEmpty()) {
        messages.forEach { animatedIds.add(it.id); historyIds.add(it.id) }
        seeded[0] = true
    }

    // Replies: who/what a quoted message shows, and jumping back to it.
    val messagesById = remember(messages) { messages.associateBy { it.id } }
    fun previewOf(m: Message): ReplyPreview {
        val author = when {
            m.isOutgoing -> "You"
            state.isGroup -> state.participants[m.address]?.displayName ?: m.address
            else -> state.contact.displayName
        }
        val visible = com.liquidglass.messages.data.model.EffectTag.strip(m.body)
        val loc = com.liquidglass.messages.data.location.LocationLink.parse(visible)
        val text = when {
            loc != null -> loc.remainingText.ifBlank { "📍 " + (loc.label ?: "Location") }
            visible.isBlank() && m.attachments.any { it.isImage } -> "📷 Photo"
            visible.isBlank() && m.attachments.isNotEmpty() -> "📎 Attachment"
            else -> visible
        }
        return ReplyPreview(author, text)
    }
    val jumpScope = rememberCoroutineScope()
    var highlightId by remember { mutableStateOf<Long?>(null) }

    val lastOutgoingId = remember(messages) { messages.lastOrNull { it.isOutgoing }?.id }
    val rows = remember(messages, lastOutgoingId) { buildRows(context, messages, lastOutgoingId) }
    val reversedRows = remember(rows) { rows.asReversed() }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (reversedRows.isNotEmpty() && listState.firstVisibleItemIndex <= 3) {
            listState.animateScrollToItem(0)
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var composerHeightPx by remember { mutableIntStateOf(0) }
    val composerHeight = with(density) { composerHeightPx.toDp() }

    val backdrop = rememberBackdrop()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.chatBackground),
    ) {
      // iOS blurs the whole conversation behind the tapback menu.
      val menuBlur by animateDpAsState(
          targetValue = if (menuTarget != null) 22.dp else 0.dp,
          animationSpec = tween(260),
          label = "menuBlur",
      )
      Box(Modifier.fillMaxSize().then(if (menuBlur > 0.dp) Modifier.blur(menuBlur) else Modifier)) {
      CompositionLocalProvider(LocalBackdrop provides backdrop) {
        // The message list is the backdrop every glass control refracts.
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .backdropSource(backdrop)
                .background(colors.chatBackground),
        ) {
            // iOS caps bubbles at ~3/4 of the width, and never wider than ~420dp.
            val bubbleMaxFraction = minOf(0.78f, 420.dp / maxWidth)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(
                    top = statusTop + HeaderHeight + 8.dp,
                    bottom = composerHeight + 8.dp,
                ),
            ) {
                // Send Later messages sit below everything, like iOS.
                items(items = state.scheduled.asReversed(), key = { "sch" + it.id }) { msg ->
                    ScheduledBubble(msg) { scheduledTarget = msg }
                }
                items(items = reversedRows, key = { it.key }) { row ->
                    when (row) {
                        is ChatRow.Separator -> TimestampHeader(row.day, row.time)
                        is ChatRow.Bubble -> {
                            val msg = row.message
                            val meta = state.meta[msg.id]
                            // Effect: chosen here when we sent it, or carried in the SMS
                            // text ("(Sent with … effect)") by the other phone.
                            val tagEffect = remember(msg.body) { com.liquidglass.messages.data.model.EffectTag.parse(msg.body).effect }
                            val effect = meta?.effect?.takeIf { it != MessageEffect.NONE } ?: tagEffect
                            if (effect != MessageEffect.NONE && msg.id !in historyIds) {
                                LaunchedEffect(msg.id) {
                                    if (autoPlayed.add(msg.id)) {
                                        effectTriggers[msg.id] = (effectTriggers[msg.id] ?: 0) + 1
                                    }
                                }
                            }
                            val quotedId = meta?.replyTo
                            val quoted = quotedId?.let { messagesById[it] }?.let(::previewOf)
                            val bubble: @Composable () -> Unit = {
                                MessageBubble(
                                    message = msg,
                                    isFirstInGroup = row.isFirstInGroup,
                                    isLastInGroup = row.isLastInGroup,
                                    showStatus = row.showStatus,
                                    animatedIds = animatedIds,
                                    reaction = meta?.reaction,
                                    effect = effect,
                                    effectTrigger = effectTriggers[msg.id] ?: 0,
                                    maxWidthFraction = bubbleMaxFraction,
                                    onLongPress = { menuTarget = msg },
                                    onTap = {
                                        if (effect != MessageEffect.NONE) {
                                            effectTriggers[msg.id] = (effectTriggers[msg.id] ?: 0) + 1
                                        }
                                    },
                                    onRetry = { onRetry(msg.id) },
                                    senderName = if (state.isGroup && !msg.isOutgoing) {
                                        state.participants[msg.address]?.displayName ?: msg.address
                                    } else {
                                        null
                                    },
                                    onOpenImage = { viewing = it },
                                    replyPreview = quoted,
                                    onQuoteClick = {
                                        val target = quotedId ?: return@MessageBubble
                                        val idx = reversedRows.indexOfFirst { it is ChatRow.Bubble && it.message.id == target }
                                        if (idx >= 0) {
                                            jumpScope.launch {
                                                listState.animateScrollToItem(idx + state.scheduled.size)
                                                highlightId = target
                                                kotlinx.coroutines.delay(700)
                                                highlightId = null
                                            }
                                        }
                                    },
                                    highlighted = highlightId == msg.id,
                                )
                            }
                            SwipeToReply(onReply = { onReplyTo(msg) }) {
                                if (state.isGroup && !msg.isOutgoing) {
                                    // Group chats: the sender's avatar beside the last bubble of their run.
                                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 8.dp)) {
                                        Box(Modifier.width(28.dp).padding(bottom = 2.dp)) {
                                            if (row.isLastInGroup) {
                                                val who = state.participants[msg.address]
                                                ContactAvatar(name = who?.displayName ?: msg.address, photoUri = who?.photoUri, size = 28.dp)
                                            }
                                        }
                                        Box(Modifier.weight(1f)) { bubble() }
                                    }
                                } else {
                                    bubble()
                                }
                            }
                        }
                    }
                }
            }
        }

        // Soft fades where messages slide under the floating chrome.
        EdgeFade(top = true, height = statusTop + HeaderHeight + 12.dp, modifier = Modifier.align(Alignment.TopCenter))
        EdgeFade(top = false, height = composerHeight + 16.dp, modifier = Modifier.align(Alignment.BottomCenter))

        ChatHeader(
            name = state.title,
            photoUri = if (state.isGroup) null else state.contact.photoUri,
            onBack = onBack,
            onTitleClick = onTitleClick,
            onVideo = {
                Toast.makeText(context, "Video calls aren't available yet", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        MessageInputBar(
            text = inputText,
            onTextChange = onInputChange,
            onSend = onSend,
            isSending = state.isSending,
            selectedEffect = selectedEffect,
            onRequestEffects = { showEffects = true },
            onPlus = { appsOpen = true },
            sendLaterAt = sendLaterAt,
            onSendLaterClick = { showSendLater = true },
            onClearSendLater = { onSetSendLater(null) },
            showCharacterCount = showCharacterCount,
            attachments = attachments,
            onRemoveAttachment = onRemoveAttachment,
            replyPreview = replyTo?.let(::previewOf),
            onCancelReply = { onReplyTo(null) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Measured OUTSIDE the inset padding so the list's bottom padding
                // tracks the keyboard / gesture bar too.
                .onSizeChanged { composerHeightPx = it.height }
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        )


        // "+" apps menu: in-window so its glass refracts the conversation.
        if (appsOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { appsOpen = false },
            )
        }
        AnimatedVisibility(
            visible = appsOpen,
            enter = scaleIn(spring(dampingRatio = 0.78f, stiffness = 480f), initialScale = 0.2f, transformOrigin = TransformOrigin(0f, 1f)) + fadeIn(),
            exit = scaleOut(tween(160), targetScale = 0.2f, transformOrigin = TransformOrigin(0f, 1f)) + fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = composerHeight + 4.dp),
        ) {
            ChatAppsMenu(onPick = { app ->
                appsOpen = false
                when (app) {
                    ChatApp.CAMERA -> launchCamera()
                    ChatApp.PHOTOS -> runCatching {
                        pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                    ChatApp.FILES -> runCatching { pickFile.launch(arrayOf("*/*")) }
                    ChatApp.STICKERS -> showStickers = true
                    ChatApp.LOCATION -> shareLocation()
                    ChatApp.CONTACT -> shareContact()
                    ChatApp.SEND_LATER -> showSendLater = true
                    ChatApp.EFFECTS -> showEffects = true
                }
            })
        }
      }
      }

        // Long-press: tapback bar above a lifted copy of the bubble, actions below.
        val target = menuTarget
        AnimatedVisibility(
            visible = target != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            if (target != null) {
                MessageMenuOverlay(
                    target = target,
                    selectedReaction = state.meta[target.id]?.reaction,
                    onDismiss = { menuTarget = null },
                    onReact = { r ->
                        onReact(target.id, r)
                        menuTarget = null
                    },
                    onReply = {
                        onReplyTo(target)
                        menuTarget = null
                    },
                    onCopy = {
                        copyToClipboard(context, com.liquidglass.messages.data.model.EffectTag.strip(target.body))
                        menuTarget = null
                    },
                    onDelete = {
                        onDelete(target.id)
                        menuTarget = null
                    },
                )
            }
        }
    }

    if (showEffects) {
        EffectPicker(
            current = selectedEffect,
            onSelect = { onEffectSelected(it) },
            onDismiss = { showEffects = false },
        )
    }
    if (showSendLater) {
        SendLaterSheet(
            onPick = { at ->
                onSetSendLater(at)
                showSendLater = false
            },
            onDismiss = { showSendLater = false },
        )
    }
    if (showStickers) {
        StickerSheet(
            onSend = { sticker ->
                onSendText(sticker)
                showStickers = false
            },
            onDismiss = { showStickers = false },
        )
    }
    viewing?.let { ImageViewer(it, onClose = { viewing = null }) }
    scheduledTarget?.let { msg ->
        AlertDialog(
            onDismissRequest = { scheduledTarget = null },
            containerColor = colors.groupedCell,
            title = { Text("Send Later", style = IosType.headline) },
            text = { Text(TimeFormat.sendLaterLabel(context, msg.sendAt), style = IosType.footnote) },
            confirmButton = {
                TextButton(onClick = {
                    onSendScheduledNow(msg.id)
                    scheduledTarget = null
                }) { Text("Send Now", color = colors.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onCancelScheduled(msg.id)
                    scheduledTarget = null
                }) { Text("Delete Message", color = colors.destructive) }
            },
        )
    }
}

/**
 * iOS 26 chat header: floating glass back button, the contact's avatar
 * centred with a glass name pill ("Name ›") overlapping its bottom, and a
 * glass video button.
 */
@Composable
private fun ChatHeader(
    name: String,
    photoUri: String?,
    onBack: () -> Unit,
    onTitleClick: () -> Unit,
    onVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LiquidTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(HeaderHeight)
            .padding(horizontal = 16.dp),
    ) {
        GlassCircleButton(
            icon = IosIcons.ChevronLeft,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 6.dp),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTitleClick,
                ),
        ) {
            ContactAvatar(name = name, photoUri = photoUri, size = 54.dp)
            GlassCapsule(
                modifier = Modifier
                    .offset(y = (-8).dp)
                    .widthIn(max = 220.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
                ) {
                    Text(
                        text = name,
                        style = IosType.footnote,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = IosIcons.ChevronRight,
                        contentDescription = null,
                        tint = colors.tertiaryText,
                        modifier = Modifier.padding(start = 1.dp).size(11.dp),
                    )
                }
            }
        }

        GlassCircleButton(
            icon = IosIcons.Video,
            contentDescription = "Video call",
            onClick = onVideo,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp),
        )
    }
}

/** iOS timestamp header: "**Today** 9:41 AM", centred, small grey. */
@Composable
private fun TimestampHeader(day: String, time: String) {
    val colors = LiquidTheme.colors
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(day) }
            append(" ")
            append(time)
        },
        style = IosType.caption2,
        color = colors.secondaryText,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
    )
}

/** Dimmed overlay with the tapback bar, the lifted bubble and an action menu. */
@Composable
private fun MessageMenuOverlay(
    target: Message,
    selectedReaction: Reaction?,
    onDismiss: () -> Unit,
    onReact: (Reaction) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LiquidTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (colors.isDark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.25f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalAlignment = if (target.isOutgoing) Alignment.End else Alignment.Start,
        ) {
            ReactionBar(
                selected = selectedReaction,
                onSelect = onReact,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
            MessageBubble(
                message = target,
                isFirstInGroup = true,
                isLastInGroup = true,
                showStatus = false,
                animatedIds = remember(target.id) { mutableSetOf(target.id) },
                reaction = selectedReaction,
                maxWidthFraction = 0.8f,
            )
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .width(230.dp)
                    .glassControl(RoundedCornerShape(16.dp), colors.glassShadow, colors, GlassStyle.Menu),
            ) {
                MenuItem("Reply", IosIcons.Reply, colors.primaryText, onReply)
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.divider))
                MenuItem("Copy", IosIcons.Copy, colors.primaryText, onCopy)
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.divider))
                MenuItem("Delete", IosIcons.Trash, colors.destructive, onDelete)
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = IosType.body, color = tint)
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Message", text))
}

/**
 * Expands a chronological [messages] list (oldest → newest) into display rows:
 * a timestamp header whenever an hour passes or the day changes, and grouping
 * flags so only the last bubble of a same-sender run gets a tail.
 */
private fun buildRows(
    context: Context,
    messages: List<Message>,
    lastOutgoingId: Long?,
): List<ChatRow> {
    if (messages.isEmpty()) return emptyList()
    val rows = ArrayList<ChatRow>(messages.size + messages.size / 4)

    fun breaksBetween(a: Message, b: Message): Boolean =
        (b.timestamp - a.timestamp) > SEPARATOR_GAP_MS || !isSameDay(a.timestamp, b.timestamp)

    for (i in messages.indices) {
        val msg = messages[i]
        val prev = messages.getOrNull(i - 1)
        val next = messages.getOrNull(i + 1)

        val needsSeparator = prev == null || breaksBetween(prev, msg)
        if (needsSeparator) {
            val (day, time) = TimeFormat.messageSeparatorParts(context, msg.timestamp)
            rows.add(ChatRow.Separator(day, time, msg.id))
        }

        fun sameSender(a: Message, b: Message) =
            a.isOutgoing == b.isOutgoing && (a.isOutgoing || a.address == b.address)
        val prevSameSender = prev != null && sameSender(prev, msg) && !needsSeparator
        val nextSameSender = next != null && sameSender(msg, next) && !breaksBetween(msg, next)

        rows.add(
            ChatRow.Bubble(
                message = msg,
                isFirstInGroup = !prevSameSender,
                isLastInGroup = !nextSameSender,
                showStatus = msg.isOutgoing && msg.id == lastOutgoingId,
            )
        )
    }
    return rows
}

/** True when two epoch-millis timestamps fall on the same local calendar day. */
private fun isSameDay(a: Long, b: Long): Boolean = localDayIndex(a) == localDayIndex(b)

private fun localDayIndex(millis: Long): Long {
    val tz = java.util.TimeZone.getDefault()
    return (millis + tz.getOffset(millis)) / 86_400_000L
}
