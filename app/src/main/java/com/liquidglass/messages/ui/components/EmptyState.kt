package com.liquidglass.messages.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.ui.theme.LiquidMessagesTheme
import com.liquidglass.messages.ui.theme.LiquidTheme

/**
 * A centered empty-state placeholder: an icon above a [title] and [subtitle],
 * all rendered in the theme's secondary text tone. Used when a list (e.g. the
 * conversation list or an empty thread) has no content.
 */
@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = LiquidTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.secondaryText,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.primaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    LiquidMessagesTheme {
        EmptyState(
            title = "No Messages",
            subtitle = "Start a conversation and it will show up here.",
            icon = Icons.Rounded.ChatBubbleOutline,
        )
    }
}
