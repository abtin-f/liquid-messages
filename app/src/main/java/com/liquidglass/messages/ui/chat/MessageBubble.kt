package com.liquidglass.messages.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidglass.messages.data.location.LocationLink
import com.liquidglass.messages.data.model.Attachment
import com.liquidglass.messages.data.model.EffectTag
import com.liquidglass.messages.data.model.Message
import com.liquidglass.messages.data.model.MessageEffect
import com.liquidglass.messages.data.model.MessageStatus
import com.liquidglass.messages.data.model.Reaction
import com.liquidglass.messages.ui.chat.effects.EffectedBubble
import com.liquidglass.messages.ui.chat.reactions.ReactionBadge
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme
import com.liquidglass.messages.ui.theme.fontFamilyFor
import com.liquidglass.messages.util.TextDirection
import java.text.BreakIterator

/** Gap between bubbles of the same run vs. between runs (iOS Messages spacing). */
private val GroupedGap = 2.dp
private val GroupGap = 8.dp

/** Screen-edge inset of bubbles (the tail sits inside this). */
internal val BubbleEdgeInset = 12.dp

/**
 * A single iMessage bubble.
 *
 * Sent bubbles are right-aligned in the (user-selectable) blue/green fill with
 * white text; received bubbles are left-aligned light grey. Only the last
 * bubble of a same-sender run carries the curled tail. Messages made of 1–3
 * emoji render large without a bubble, exactly like iOS.
 *
 * Under the newest sent message a "Delivered"/"Sending…" caption appears; a
 * failed message gets iOS's red "!" badge and "Not Delivered" caption, and
 * tapping either retries the send.
 */
@Composable
fun MessageBubble(
    message: Message,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    modifier: Modifier = Modifier,
    showStatus: Boolean = false,
    animatedIds: MutableSet<Long> = mutableSetOf(),
    reaction: Reaction? = null,
    effect: MessageEffect = MessageEffect.NONE,
    effectTrigger: Int = 0,
    onLongPress: () -> Unit = {},
    onTap: () -> Unit = {},
    onRetry: () -> Unit = {},
    maxWidthFraction: Float = 0.75f,
    /** Group chats: sender name shown above the first bubble of their run. */
    senderName: String? = null,
    onOpenImage: (Attachment) -> Unit = {},
    /** The message this one answers, shown as a small quote above it. */
    replyPreview: ReplyPreview? = null,
    onQuoteClick: () -> Unit = {},
    /** Briefly pulses the bubble (after jumping to it from a reply). */
    highlighted: Boolean = false,
) {
    val colors = LiquidTheme.colors
    val outgoing = message.isOutgoing
    val failed = outgoing && message.status == MessageStatus.FAILED
    // A shared location renders as a map card; any other text stays a bubble.
    val visibleBody = remember(message.body) { com.liquidglass.messages.data.model.MessageText.visible(message.body) }
    val location = remember(visibleBody) { LocationLink.parse(visibleBody) }
    val bodyText = location?.remainingText ?: visibleBody
    val rtl = TextDirection.isRtl(bodyText)
    val jumbo = remember(message.body) {
        val visible = com.liquidglass.messages.data.model.MessageText.visible(message.body)
        jumboEmojiCount(LocationLink.parse(visible)?.remainingText ?: visible)
    }

    // Pop-in only the first time a message is ever shown (ids hoisted by the
    // list so recycled rows scrolled back into view don't re-animate).
    val appear = remember(message.id) {
        Animatable(if (message.id in animatedIds) 1f else 0f)
    }
    LaunchedEffect(message.id) {
        if (animatedIds.add(message.id)) {
            appear.animateTo(
                1f,
                spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    val pulse by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (highlighted) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "replyTargetPulse",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
                transformOrigin = TransformOrigin(if (outgoing) 1f else 0f, 0.5f)
            }
            .padding(
                start = BubbleEdgeInset,
                end = BubbleEdgeInset,
                top = if (isFirstInGroup) GroupGap else GroupedGap,
            ),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        if (senderName != null && isFirstInGroup && !outgoing) {
            Text(
                text = senderName,
                style = IosType.caption1,
                color = colors.secondaryText,
                modifier = Modifier.padding(start = 12.dp + BubbleTailWidth, bottom = 2.dp),
            )
        }
        if (replyPreview != null) {
            QuotedMessage(
                preview = replyPreview,
                outgoing = outgoing,
                onClick = onQuoteClick,
                modifier = Modifier.padding(
                    start = if (outgoing) 0.dp else BubbleTailWidth,
                    end = if (outgoing) BubbleTailWidth else 0.dp,
                    bottom = 3.dp,
                ),
            )
        }
        if (location != null) {
            LocationCard(
                location = location,
                outgoing = outgoing,
                onLongPress = onLongPress,
                modifier = Modifier.padding(
                    start = if (outgoing) 0.dp else BubbleTailWidth,
                    end = if (outgoing) BubbleTailWidth else 0.dp,
                    bottom = if (bodyText.isNotBlank()) 2.dp else 0.dp,
                ),
            )
        }
        if (message.attachments.isNotEmpty()) {
            AttachmentStack(
                attachments = message.attachments,
                outgoing = outgoing,
                onOpenImage = onOpenImage,
                onLongPress = onLongPress,
                modifier = Modifier.padding(
                    start = if (outgoing) 0.dp else BubbleTailWidth,
                    end = if (outgoing) BubbleTailWidth else 0.dp,
                    bottom = if (message.body.isNotBlank()) 2.dp else 0.dp,
                ),
            )
        }
        if (bodyText.isNotBlank() || (message.attachments.isEmpty() && location == null)) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(top = if (reaction != null) 16.dp else 0.dp),
            ) {
                EffectedBubble(
                    effect = effect,
                    trigger = effectTrigger,
                    modifier = Modifier.maxWidthFraction(maxWidthFraction),
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    // Grow out of the tail corner, like iOS.
                                    transformOrigin = TransformOrigin(if (outgoing) 1f else 0f, 1f)
                                    val s = 0.6f + 0.4f * appear.value
                                    scaleX = s
                                    scaleY = s
                                    alpha = appear.value.coerceIn(0f, 1f)
                                }
                                .pointerInput(message.id) {
                                    detectTapGestures(
                                        onLongPress = { onLongPress() },
                                        onTap = { if (failed) onRetry() else onTap() },
                                    )
                                },
                        ) {
                            if (jumbo > 0) {
                                JumboEmoji(bodyText, jumbo, outgoing)
                            } else {
                                BubbleBody(
                                    message = message,
                                    text = bodyText,
                                    outgoing = outgoing,
                                    hasTail = isLastInGroup,
                                    rtl = rtl,
                                )
                            }
                        }

                        if (reaction != null) {
                            ReactionBadge(
                                reaction = reaction,
                                outgoing = outgoing,
                                modifier = Modifier
                                    .align(if (outgoing) Alignment.TopStart else Alignment.TopEnd)
                                    .offset(x = if (outgoing) (-12).dp else 12.dp, y = (-18).dp),
                            )
                        }
                    }
                }
            }

            if (failed) {
                Spacer(Modifier.width(6.dp))
                FailedBadge(onClick = onRetry)
            }
        }

        // Status caption under the newest sent message (or any failed one).
        val caption = when {
            failed -> "Not Delivered"
            outgoing && showStatus -> message.status.caption()
            else -> null
        }
        if (caption != null) {
            Text(
                text = caption,
                style = IosType.caption2,
                fontWeight = FontWeight.SemiBold,
                color = if (failed) colors.destructive else colors.secondaryText,
                modifier = Modifier
                    .padding(top = 3.dp, bottom = 1.dp, end = if (failed) 30.dp else BubbleTailWidth + 2.dp)
                    .then(if (failed) Modifier.clickable(onClick = onRetry) else Modifier),
            )
        }
    }
}

/** The filled bubble with its text. */
@Composable
private fun BubbleBody(
    message: Message,
    text: String,
    outgoing: Boolean,
    hasTail: Boolean,
    rtl: Boolean,
) {
    val colors = LiquidTheme.colors
    val shape = remember(outgoing, hasTail) { BubbleShape(outgoing, hasTail) }
    val fill = if (outgoing) {
        Modifier.background(Brush.verticalGradient(colors.sentBubbleGradient), shape)
    } else {
        Modifier.background(colors.receivedBubble, shape)
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 36.dp)
            .then(fill)
            .clip(shape)
            .padding(
                // The tail side reserves BubbleTailWidth inside the shape.
                start = if (outgoing) 12.dp else 12.dp + BubbleTailWidth,
                end = if (outgoing) 12.dp + BubbleTailWidth else 12.dp,
                top = 7.dp,
                bottom = 7.dp,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Each message picks its own direction from its first strong character.
        CompositionLocalProvider(
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            // Links, e-mails and phone numbers are underlined and tappable.
            Text(
                text = rememberLinkifiedText(
                    text,
                    linkColor = if (outgoing) colors.sentText else colors.accent,
                ),
                style = IosType.body,
                fontFamily = fontFamilyFor(text),
                color = if (outgoing) colors.sentText else colors.receivedText,
            )
        }
    }
}

/** 1–3 emoji with no bubble, scaled like iOS (fewer emoji → bigger). */
@Composable
private fun JumboEmoji(text: String, count: Int, outgoing: Boolean) {
    Text(
        text = text,
        fontSize = when (count) {
            1 -> 48.sp
            2 -> 42.sp
            else -> 36.sp
        },
        lineHeight = when (count) {
            1 -> 56.sp
            2 -> 50.sp
            else -> 44.sp
        },
        modifier = Modifier.padding(
            start = if (outgoing) 0.dp else 4.dp,
            end = if (outgoing) 4.dp else 0.dp,
        ),
    )
}

/** iOS's red circled "!" beside a message that failed to send. */
@Composable
private fun FailedBadge(onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(colors.destructive)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "!",
            color = Color.White,
            style = IosType.subheadline,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun MessageStatus.caption(): String? = when (this) {
    MessageStatus.SENDING -> "Sending…"
    MessageStatus.SENT -> "Sent"
    MessageStatus.DELIVERED -> "Delivered"
    MessageStatus.READ -> "Read"
    MessageStatus.FAILED -> "Not Delivered"
    MessageStatus.RECEIVED, MessageStatus.NONE -> null
}

/**
 * Number of emoji when [text] consists solely of 1–3 emoji (the iOS "jumbo
 * emoji" rule), else 0. Counts grapheme clusters so a flag, a skin-toned or a
 * ZWJ family emoji each count as one.
 */
internal fun jumboEmojiCount(text: String): Int {
    val s = text.trim()
    if (s.isEmpty() || s.length > 40) return 0
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        if (!isEmojiCodePoint(cp)) return 0
        i += Character.charCount(cp)
    }
    val it = BreakIterator.getCharacterInstance()
    it.setText(s)
    var count = 0
    while (it.next() != BreakIterator.DONE) count++
    return if (count in 1..3) count else 0
}

private fun isEmojiCodePoint(cp: Int): Boolean =
    cp in 0x1F000..0x1FAFF || // pictographs, emoticons, transport, flags, supplemental
        cp in 0x2600..0x27BF || // misc symbols + dingbats
        cp in 0x2B00..0x2BFF ||
        cp in 0x2190..0x21FF ||
        cp in 0x2300..0x23FF ||
        cp in 0x25A0..0x25FF ||
        cp in 0xE0020..0xE007F || // tag sequences (subdivision flags)
        cp == 0x200D || cp == 0xFE0F || cp == 0x20E3 ||
        cp == 0x00A9 || cp == 0x00AE || cp == 0x203C || cp == 0x2049 ||
        cp == 0x2122 || cp == 0x2139 || cp == 0x3030 || cp == 0x303D ||
        cp == 0x3297 || cp == 0x3299

/**
 * Caps the measured width at [fraction] of the available width while still
 * letting short messages be narrower.
 */
private fun Modifier.maxWidthFraction(fraction: Float): Modifier = this.layout { measurable, constraints ->
    val maxW = if (constraints.maxWidth == Int.MAX_VALUE) {
        constraints.maxWidth
    } else {
        (constraints.maxWidth * fraction).toInt().coerceAtLeast(0)
    }
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = maxW))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}
