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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.data.model.Conversation
import com.liquidglass.messages.ui.components.ContactAvatar
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme
import com.liquidglass.messages.ui.theme.fontFamilyFor
import com.liquidglass.messages.util.TimeFormat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Width of the red "Delete" action revealed by swiping a row left. */
private val DeleteActionWidth = 84.dp

/** Leading gutter that holds the blue unread dot (iOS keeps it even when empty). */
private val DotGutter = 22.dp
private val AvatarSize = 42.dp

/**
 * One iOS Messages list row:
 *
 *  • [avatar]  Name                       9:41 AM ›
 *              Preview text on up to two lines…
 *
 * The blue dot marks unread threads. Swipe left to reveal Delete (with iOS's
 * confirmation), or long-press for the same action. A hairline separator is
 * inset to start under the text, as on iOS.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LiquidTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val maxReveal = with(density) { DeleteActionWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun settle(open: Boolean) {
        scope.launch {
            offsetX.animateTo(if (open) -maxReveal else 0f, spring(dampingRatio = 0.85f, stiffness = 500f))
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // Red action underneath the row.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(colors.destructive),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .width(DeleteActionWidth)
                    .fillMaxHeight()
                    .clickable { confirmDelete = true },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(IosIcons.Trash, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text("Delete", style = IosType.footnote, color = Color.White)
                }
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
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-maxReveal * 1.3f, 0f))
                        }
                    },
                    onDragStopped = { velocity ->
                        settle(open = offsetX.value < -maxReveal / 2 || velocity < -1200f)
                    },
                )
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = {
                        if (offsetX.value != 0f) settle(open = false) else onClick()
                    },
                    onLongClick = { confirmDelete = true },
                )
                .heightIn(min = 76.dp)
                .padding(end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(DotGutter), contentAlignment = Alignment.Center) {
                if (conversation.hasUnread) {
                    Box(Modifier.size(10.dp).background(colors.accent, CircleShape))
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
                    text = remember(conversation.snippet) {
                        val visible = com.liquidglass.messages.data.model.EffectTag.strip(conversation.snippet)
                        val loc = com.liquidglass.messages.data.location.LocationLink.parse(visible)
                        if (loc == null) visible
                        else loc.remainingText.ifBlank { "📍 " + (loc.label ?: "Location") }
                    },
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

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = {
                confirmDelete = false
                settle(open = false)
            },
            title = { Text("Delete Conversation?", style = IosType.headline) },
            text = { Text("This conversation will be deleted from this device.", style = IosType.footnote) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = colors.destructive, style = IosType.body, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    settle(open = false)
                }) { Text("Cancel", color = colors.accent, style = IosType.body) }
            },
            containerColor = colors.groupedCell,
        )
    }
}
