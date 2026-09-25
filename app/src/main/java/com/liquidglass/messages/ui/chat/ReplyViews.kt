package com.liquidglass.messages.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme
import com.liquidglass.messages.ui.theme.fontFamilyFor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What a reply shows of the message it answers. */
data class ReplyPreview(val author: String, val text: String)

private val ReplyThreshold = 56.dp
private val ReplyMaxDrag = 76.dp

/**
 * iOS swipe-to-reply: drag a message to the right, a reply arrow fades in behind
 * it, a haptic tick marks the threshold, and letting go past it replies. The row
 * springs back either way.
 */
@Composable
fun SwipeToReply(onReply: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val threshold = with(density) { ReplyThreshold.toPx() }
    val max = with(density) { ReplyMaxDrag.toPx() }
    val armed = remember { booleanArrayOf(false) }

    Box {
        // Reply arrow revealed under the sliding row.
        val progress = (offset.value / threshold).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp)
                .size(30.dp)
                .graphicsLayer {
                    alpha = progress
                    val s = 0.5f + 0.5f * progress
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(LiquidTheme.colors.fieldBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IosIcons.Reply, contentDescription = null, tint = LiquidTheme.colors.secondaryText, modifier = Modifier.size(17.dp))
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        // Rubber-band past the threshold, never left of the rest position.
                        val resistance = if (offset.value > threshold) 0.35f else 1f
                        val next = (offset.value + delta * resistance).coerceIn(0f, max)
                        scope.launch { offset.snapTo(next) }
                        if (!armed[0] && next >= threshold) {
                            armed[0] = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (armed[0] && next < threshold) {
                            armed[0] = false
                        }
                    },
                    onDragStopped = {
                        if (armed[0]) onReply()
                        armed[0] = false
                        offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
                    },
                ),
        ) { content() }
    }
}

/**
 * The quoted original above a reply bubble, iOS-style: a small, muted card on
 * the same side as the reply, with a hairline accent. Tapping jumps to it.
 */
@Composable
fun QuotedMessage(preview: ReplyPreview, outgoing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LiquidTheme.colors
    Row(
        modifier = modifier
            .widthIn(max = 250.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.receivedBubble.copy(alpha = if (colors.isDark) 0.6f else 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .width(2.5.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(if (outgoing) colors.sentBubbleBottom else colors.secondaryText),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(preview.author, style = IosType.caption2, fontWeight = FontWeight.SemiBold, color = colors.secondaryText, maxLines = 1)
            Text(
                preview.text,
                style = IosType.footnote,
                fontFamily = fontFamilyFor(preview.text),
                color = colors.secondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "Replying to …" bar shown inside the composer, with a cancel button. */
@Composable
fun ReplyComposerBar(preview: ReplyPreview, onCancel: () -> Unit) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(colors.accent),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("Replying to ${preview.author}", style = IosType.caption1, fontWeight = FontWeight.SemiBold, color = colors.accent, maxLines = 1)
            Text(
                preview.text,
                style = IosType.footnote,
                fontFamily = fontFamilyFor(preview.text),
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(colors.fieldBackground)
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IosIcons.Close, contentDescription = "Cancel reply", tint = colors.secondaryText, modifier = Modifier.size(10.dp))
        }
    }
}
