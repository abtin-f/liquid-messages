package com.liquidglass.messages.ui.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.ui.theme.LiquidTheme

/**
 * A centered date/time separator inserted between message groups when the gap
 * between consecutive messages crosses a day boundary or a sizeable time gap.
 *
 * Renders as small, secondary-tinted text with generous vertical padding —
 * matching iMessage's subtle inline timestamps.
 *
 * @param label pre-formatted text (e.g. "Today 9:41 AM"), built with
 *   [com.liquidglass.messages.util.TimeFormat.messageSeparator].
 */
@Composable
fun DateSeparator(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        // Slightly bolder so the date reads as a header, not body text.
        fontWeight = FontWeight.SemiBold,
        color = LiquidTheme.colors.secondaryText,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
    )
}
