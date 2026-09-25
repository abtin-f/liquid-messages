package com.liquidglass.messages.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.StartOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.ui.theme.BubbleCornerRadius
import com.liquidglass.messages.ui.theme.BubbleTailCornerRadius
import com.liquidglass.messages.ui.theme.LiquidTheme

/**
 * An animated "typing…" indicator rendered as three bouncing dots inside a
 * received-style (left-aligned, gray) bubble — mirroring iMessage's incoming
 * typing affordance.
 *
 * Each dot runs the same looping bounce on a staggered phase so the wave reads
 * left-to-right. Purely decorative; takes no state.
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
) {
    val colors = LiquidTheme.colors

    // Received bubble with a small tail on the bottom-start corner.
    val bubbleShape = RoundedCornerShape(
        topStart = BubbleCornerRadius,
        topEnd = BubbleCornerRadius,
        bottomEnd = BubbleCornerRadius,
        bottomStart = BubbleTailCornerRadius,
    )

    val transition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Row(
            modifier = Modifier
                .clip(bubbleShape)
                .background(colors.receivedBubble)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Three dots, each phase-shifted so the bounce travels across them.
            // Each dot animates a 0..1 "lift" fraction that we map to an upward
            // pixel translation; the StartOffset staggers the wave.
            repeat(3) { index ->
                val lift by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 900
                            0f at 0 using LinearEasing
                            1f at 200 using LinearEasing
                            0f at 400 using LinearEasing
                            0f at 900 using LinearEasing
                        },
                        repeatMode = RepeatMode.Restart,
                        // Stagger each dot by 150ms via an initial start offset.
                        initialStartOffset = StartOffset(index * 150),
                    ),
                    label = "dot$index",
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer {
                            // Lift up to 5dp at the peak of this dot's phase.
                            translationY = -lift * 5.dp.toPx()
                        }
                        .clip(CircleShape)
                        .background(colors.secondaryText),
                )
            }
        }
    }
}
