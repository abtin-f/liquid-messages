package com.liquidglass.messages.ui.chat.reactions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.liquidglass.messages.ui.theme.liquidGlass

/**
 * The iMessage "Tapback" reaction picker: a horizontal translucent glass pill
 * holding [Reaction.bar]'s six emojis as evenly-spaced tappable circles. The
 * currently [selected] reaction sits inside an accent-tinted filled disc.
 *
 * Entry: the whole pill scales up from 0.6 + fades in, and each emoji staggers
 * in shortly after. Press feedback springs each emoji down slightly.
 */
@Composable
fun ReactionBar(
    selected: Reaction?,
    onSelect: (Reaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LiquidTheme.colors

    // Drives both the container pop-in and the per-emoji stagger.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                val s = 0.6f + 0.4f * appear.value
                scaleX = s
                scaleY = s
                alpha = appear.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .liquidGlass(colors = colors, shape = CircleShape, borderWidth = 0.75.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Reaction.bar.forEachIndexed { index, reaction ->
            // Stagger: each emoji's local progress lags the one before it.
            val count = Reaction.bar.size
            val start = index.toFloat() / (count + 2)
            val span = 1f - start
            val local = ((appear.value - start) / span).coerceIn(0f, 1f)

            ReactionEmoji(
                reaction = reaction,
                isSelected = reaction == selected,
                appear = local,
                onClick = { onSelect(reaction) },
            )
        }
    }
}

@Composable
private fun ReactionEmoji(
    reaction: Reaction,
    isSelected: Boolean,
    appear: Float,
    onClick: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "reactionPress",
    )

    // Accent disc behind the selected emoji fades/scales in smoothly.
    val selectFraction by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "reactionSelect",
    )

    Box(
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer {
                val s = (0.4f + 0.6f * appear) * pressScale
                scaleX = s
                scaleY = s
                alpha = appear
            }
            .clip(CircleShape)
            .clickableNoRipple(interaction, onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Selection highlight disc.
        Box(
            modifier = Modifier
                .size(38.dp)
                .graphicsLayer {
                    scaleX = selectFraction
                    scaleY = selectFraction
                    alpha = selectFraction
                }
                .clip(CircleShape)
                .background(colors.accent),
        )
        Text(
            text = reaction.emoji,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A press-tracking click that emits no Material ripple — the bar uses scale
 * feedback instead, matching the soft iOS tapback feel.
 */
private fun Modifier.clickableNoRipple(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = this.then(
    Modifier.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
)

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun ReactionBarPreview() {
    LiquidMessagesTheme(darkTheme = true) {
        Box(Modifier.padding(24.dp).clip(RoundedCornerShape(0.dp))) {
            ReactionBar(selected = Reaction.LOVE, onSelect = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReactionBarLightPreview() {
    LiquidMessagesTheme(darkTheme = false) {
        Box(Modifier.padding(24.dp)) {
            ReactionBar(selected = null, onSelect = {})
        }
    }
}
