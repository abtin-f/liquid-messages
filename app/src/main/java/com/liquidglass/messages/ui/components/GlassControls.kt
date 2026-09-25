package com.liquidglass.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.ui.theme.LiquidTheme
import com.liquidglass.messages.ui.glass.GlassStyle
import com.liquidglass.messages.ui.glass.liquidGlass

/**
 * iOS 26 "Liquid Glass" control surface: a floating, near-opaque frosted fill
 * with a bright top rim and a soft drop shadow.
 */
fun Modifier.glassControl(
    shape: Shape,
    shadowColor: Color,
    glass: com.liquidglass.messages.ui.theme.LiquidColors,
    style: GlassStyle = GlassStyle(),
): Modifier =
    this
        .shadow(elevation = if (style.menu) 16.dp else 8.dp, shape = shape, ambientColor = shadowColor, spotColor = shadowColor)
        .liquidGlass(shape = shape, style = style)

/** Round glass button (back, video, "+", compose…), iOS 26 style. */
@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = LiquidTheme.colors.primaryText,
) {
    val colors = LiquidTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressableScale(interaction, pressedScale = 0.9f)
            .size(size)
            .glassControl(CircleShape, colors.glassShadow, colors)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Glass capsule container (name pill, grouped toolbar buttons, the composer field). */
@Composable
fun GlassCapsule(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LiquidTheme.colors
    val shape = if (cornerRadius == null) RoundedCornerShape(50) else RoundedCornerShape(cornerRadius)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.pressableScale(interaction, pressedScale = 0.95f) else Modifier)
            .glassControl(shape, colors.glassShadow, colors)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * The soft fade iOS 26 puts where content scrolls under floating chrome —
 * opaque background at the screen edge dissolving to clear.
 */
@Composable
fun EdgeFade(
    top: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val bg = LiquidTheme.colors.chatBackground
    val stops = listOf(bg, bg.copy(alpha = 0.85f), bg.copy(alpha = 0f))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(if (top) stops else stops.reversed())),
    )
}
