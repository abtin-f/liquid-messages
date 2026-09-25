package com.liquidglass.messages.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Photo-less avatar (grey iOS disc with initials / person silhouette).
 * Kept as a thin wrapper so existing call sites stay unchanged.
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    ContactAvatar(name = name, photoUri = null, modifier = modifier, size = size)
}
