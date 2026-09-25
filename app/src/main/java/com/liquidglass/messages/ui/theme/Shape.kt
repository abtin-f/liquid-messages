package com.liquidglass.messages.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val LiquidShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Corner radius used by message bubbles. */
val BubbleCornerRadius = 20.dp

/** The small "tail-side" corner radius of a message bubble. */
val BubbleTailCornerRadius = 6.dp
