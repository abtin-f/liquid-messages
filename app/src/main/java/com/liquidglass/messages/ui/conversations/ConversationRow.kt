package com.liquidglass.messages.ui.conversations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.data.model.Conversation
import com.liquidglass.messages.ui.components.ContactAvatar
import com.liquidglass.messages.ui.components.IosDialogs
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme
import com.liquidglass.messages.ui.theme.fontFamilyFor
import com.liquidglass.messages.util.TimeFormat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Width of each swipe action button. */
private val ActionWidth = 78.dp

/** Leading gutter that holds the blue unread dot (iOS keeps it even when empty). */
private val DotGutter = 22.dp
private val AvatarSize = 42.dp

private val PinYellow = Color(0xFFFFB800)
private val AlertsIndigo = Color(0xFF5856D6)

/** A coloured button revealed by swiping a row. */
private data class SwipeAction(val label: String, val icon: ImageVector, val color: Color, val onClick: () -> Unit)

/** The iOS confirmation shown before a conversation is deleted. */
fun confirmDeleteConversation(onDelete: () -> Unit, onCancel: () -> Unit = {}) {
    IosDialogs.alert(
        "Delete Conversation?",
        "This conversation will be moved to Recently Deleted. You can recover it there for 30 days.",
        IosDialogs.Action("Cancel", IosDialogs.Role.CANCEL, onCancel),
        IosDialogs.Action("Delete", IosDialogs.Role.DESTRUCTIVE, onDelete),
    )
}

/**
 * One iOS Messages list row:
 *
 *  • [avatar]  Name                    🔕 9:41 AM ›
 *              Preview text on up to two lines…
 *
 * Swipe right for Read/Unread and Pin, left for Hide Alerts and Delete
 * (Delete asks first, like iOS); long-press opens the context menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    pinned: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onToggleMute: (() -> Unit)? = null,
    onToggleRead: (() -> Unit)? = null,
) {
    val colors = LiquidTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    fun close() {
        scope.launch { offsetX.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 500f)) }
    }

    val leading = buildList {
        onToggleRead?.let {
            add(SwipeAction(if (conversation.hasUnread) "Read" else "Unread", IosIcons.Envelope, colors.accent) { close(); it() })
        }
        onTogglePin?.let {
            add(SwipeAction(if (pinned) "Unpin" else "Pin", if (pinned) IosIcons.PinSlash else IosIcons.Pin, PinYellow) { close(); it() })
        }
    }
    val trailing = buildList {
        onToggleMute?.let {
            add(SwipeAction(if (muted) "Show Alerts" else "Hide Alerts", if (muted) IosIcons.Bell else IosIcons.BellSlash, AlertsIndigo) { close(); it() })
        }
        add(SwipeAction("Delete", IosIcons.Trash, colors.destructive) {
            confirmDeleteConversation(onDelete = onDelete, onCancel = ::close)
        })
    }
    val leadMax = with(density) { ActionWidth.toPx() } * leading.size
    val trailMax = with(density) { ActionWidth.toPx() } * trailing.size

    fun settle(velocity: Float) {
        val x = offsetX.value
        val target = when {
            x > 0 && (x > leadMax / 2 || velocity > 1200f) -> leadMax
            x < 0 && (x < -trailMax / 2 || velocity < -1200f) -> -trailMax
            else -> 0f
        }
        if (target != 0f) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch { offsetX.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 500f)) }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // Swipe actions underneath the row: leading from the left, trailing on the right.
        Row(Modifier.matchParentSize()) {
            if (offsetX.value > 0f) {
                leading.forEach { ActionButton(it) }
                Spacer(Modifier.weight(1f).fillMaxHeight().background(leading.lastOrNull()?.color ?: Color.Transparent))
            } else if (offsetX.value < 0f) {
                Spacer(Modifier.weight(1f).fillMaxHeight().background(trailing.firstOrNull()?.color ?: Color.Transparent))
                trailing.forEach { ActionButton(it) }
            }
        }

        Row(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .background(colors.listBackground)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val x = offsetX.value + delta
                            // Rubber-band past the last button, like UITableView.
                            val limited = when {
                                x > leadMax -> leadMax + (x - leadMax) * 0.3f
                                x < -trailMax -> -trailMax + (x + trailMax) * 0.3f
                                else -> x
                            }
                            offsetX.snapTo(limited.coerceIn(-trailMax * 1.4f, leadMax * 1.4f))
                        }
                    },
                    onDragStopped = { velocity -> settle(velocity) },
                )
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = { if (offsetX.value != 0f) close() else onClick() },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (onLongPress != null) onLongPress() else confirmDeleteConversation(onDelete)
                    },
                )
                .heightIn(min = 76.dp)
                .padding(end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(DotGutter), contentAlignment = Alignment.Center) {
                if (conversation.hasUnread) {
                    Box(Modifier.size(10.dp).background(if (muted) colors.tertiaryText else colors.accent, CircleShape))
                }
            }

            ContactAvatar(
                name = conversation.displayName,
                photoUri = conversation.photoUri,
                size = AvatarSize,
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.displayName,
                        style = IosType.headline,
                        fontFamily = fontFamilyFor(conversation.displayName),
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    if (muted) {
                        Icon(
                            IosIcons.BellSlash,
                            contentDescription = "Alerts hidden",
                            tint = colors.secondaryText,
                            modifier = Modifier.padding(end = 4.dp).size(14.dp),
                        )
                    }
                    Text(
                        text = TimeFormat.conversationStamp(context, conversation.timestamp),
                        style = IosType.subheadline,
                        color = colors.secondaryText,
                        maxLines = 1,
                    )
                    Icon(
                        imageVector = IosIcons.ChevronRight,
                        contentDescription = null,
                        tint = colors.tertiaryText,
                        modifier = Modifier.padding(start = 4.dp).size(12.dp),
                    )
                }
                Text(
                    text = remember(conversation.snippet) { snippetText(conversation.snippet) },
                    style = IosType.subheadline,
                    fontFamily = fontFamilyFor(conversation.snippet),
                    fontWeight = FontWeight.Normal,
                    color = colors.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Separator inset to the text column, like UITableView.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = DotGutter + AvatarSize + 12.dp)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.divider),
        )
    }
}

/** List preview text: metadata lines stripped, locations shown as "📍 Label". */
internal fun snippetText(snippet: String): String {
    val visible = com.liquidglass.messages.data.model.MessageText.visible(snippet)
    val loc = com.liquidglass.messages.data.location.LocationLink.parse(visible)
    return if (loc == null) visible else loc.remainingText.ifBlank { "📍 " + (loc.label ?: "Location") }
}

@Composable
private fun ActionButton(action: SwipeAction) {
    Box(
        modifier = Modifier
            .width(ActionWidth)
            .fillMaxHeight()
            .background(action.color)
            .clickable(onClick = action.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(action.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(3.dp))
            Text(action.label, style = IosType.caption1, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1)
        }
    }
}
