package com.liquidglass.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.ui.theme.LiquidTheme
import com.liquidglass.messages.ui.theme.liquidGlass

/**
 * A translucent "liquid glass" top bar. Slot-based and generic: the caller
 * supplies the bar's content (title, back button, trailing actions, etc).
 *
 * Applies [statusBarsPadding] so content clears the status bar, fills the
 * width with the frosted glass material, and draws a hairline bottom divider.
 */
@Composable
fun GlassTopBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LiquidTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(colors = colors, shape = RectangleShape, borderWidth = 0.dp)
            .statusBarsPadding(),
    ) {
        content()
        // Hairline bottom divider separating the bar from scrolling content.
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.divider)
        )
    }
}

/**
 * A translucent "liquid glass" bottom container — the slot for the message
 * input bar / compose area. Applies a hairline top divider and
 * [navigationBarsPadding] so the content clears the gesture/nav bar.
 *
 * IME padding is intentionally left to the caller (the input field) so the
 * container can sit flush above the keyboard when focused.
 */
@Composable
fun GlassBottomContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LiquidTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(colors = colors, shape = RectangleShape, borderWidth = 0.dp),
    ) {
        // Hairline top divider separating the input bar from scrolling content.
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.divider)
        )
        Column(Modifier.navigationBarsPadding()) {
            content()
        }
    }
}
