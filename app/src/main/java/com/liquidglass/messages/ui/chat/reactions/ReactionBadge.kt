package com.liquidglass.messages.ui.chat.reactions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidglass.messages.data.model.Reaction
import com.liquidglass.messages.ui.theme.LiquidMessagesTheme
import com.liquidglass.messages.ui.theme.LiquidTheme

/**
 * The small circular tapback badge that overlaps a message bubble's top corner —
 * top-start for [outgoing] (right-aligned) bubbles, top-end for incoming, just
 * like iMessage. The chip is a [receivedBubble]-colored disc with a hairline
 * ring and the reaction [emoji][Reaction.emoji], plus a tiny "tail dot" pointing
 * toward the bubble.
 *
 * Pops in (scale 0 → 1 with an overshoot spring) the first time it appears, and
 * again whenever the [reaction] changes.
 */
@Composable
fun ReactionBadge(
    reaction: Reaction,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LiquidTheme.colors

    val pop = remember { Animatable(0f) }
    LaunchedEffect(reaction) {
        pop.snapTo(0f)
        pop.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    // The badge grows from the corner that touches the bubble.
    val origin = if (outgoing) TransformOrigin(1f, 1f) else TransformOrigin(0f, 1f)

    Box(
        modifier = modifier
            .size(28.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                alpha = pop.value.coerceIn(0f, 1f)
                transformOrigin = origin
            },
        contentAlignment = Alignment.Center,
    ) {
        // Tail dot — the smaller satellite disc nudged toward the bubble corner.
        Box(
            modifier = Modifier
                .align(if (outgoing) Alignment.BottomStart else Alignment.BottomEnd)
                .size(7.dp)
                .clip(CircleShape)
                .background(colors.receivedBubble)
                .border(0.5.dp, colors.divider, CircleShape),
        )

        // Main chip.
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(colors.receivedBubble)
                .border(0.5.dp, colors.divider, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = reaction.emoji,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ReactionBadgeOutgoingPreview() {
    LiquidMessagesTheme(darkTheme = true) {
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            ReactionBadge(reaction = Reaction.LOVE, outgoing = true)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReactionBadgeIncomingPreview() {
    LiquidMessagesTheme(darkTheme = false) {
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            ReactionBadge(reaction = Reaction.LAUGH, outgoing = false)
        }
    }
}
