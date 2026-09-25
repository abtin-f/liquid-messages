package com.liquidglass.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.ui.theme.LiquidMessagesTheme
import com.liquidglass.messages.ui.theme.LiquidTheme

/**
 * The blue unread indicator dot shown beside conversations with unread messages.
 * Uses the theme accent color so it matches the app's tint.
 */
@Composable
fun UnreadDot(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(10.dp)
            .background(color = LiquidTheme.colors.accent, shape = CircleShape)
    )
}

@Preview(showBackground = true)
@Composable
private fun UnreadDotPreview() {
    LiquidMessagesTheme {
        Box(Modifier.size(24.dp)) {
            UnreadDot(Modifier.size(10.dp))
        }
    }
}
