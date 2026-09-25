package com.liquidglass.messages.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.ui.theme.LiquidTheme

/**
 * The circular gradient "send" button used in the message input bar.
 *
 * Enabled: a blue vertical gradient ([LiquidTheme.colors.sentBubbleGradient])
 * with a white upward arrow. Disabled: a flat gray fill. Pressing springs the
 * button down for tactile feedback.
 *
 * @param enabled  whether the button is interactive (false when input is empty).
 * @param onClick  invoked on tap when [enabled].
 */
@Composable
fun GradientSendButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LiquidTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring the button slightly down while pressed (and only when enabled).
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "sendButtonScale",
    )

    val fill: Brush = if (enabled) {
        Brush.verticalGradient(colors.sentBubbleGradient)
    } else {
        // Disabled: subtle gray using the theme's received-bubble tone.
        SolidColor(colors.receivedBubble)
    }

    Box(
        modifier = modifier
            .size(34.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(brush = fill, shape = CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.ArrowUpward,
            contentDescription = "Send",
            tint = if (enabled) Color.White else colors.secondaryText,
            modifier = Modifier.size(20.dp),
        )
    }
}
