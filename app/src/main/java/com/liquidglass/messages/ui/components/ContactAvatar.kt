package com.liquidglass.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.liquidglass.messages.ui.theme.InterFontFamily
import com.liquidglass.messages.ui.theme.LiquidTheme

/** iOS "Show Contact Photos" — when false, avatars always use monograms. */
val LocalShowContactPhotos = staticCompositionLocalOf { true }

/**
 * iOS contact avatar: the contact's photo when there is one; otherwise the
 * system grey gradient disc with white initials (first + last name), or a
 * person silhouette for a bare phone number — exactly what Messages shows.
 */
@Composable
fun ContactAvatar(
    name: String,
    photoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    val colors = LiquidTheme.colors
    val initials = remember(name) { iosInitials(name) }
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(colors.avatarTop, colors.avatarBottom))),
        contentAlignment = Alignment.Center,
    ) {
        if (initials != null) {
            Text(
                text = initials,
                color = Color.White,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.4f).sp,
                letterSpacing = 0.sp,
            )
        } else {
            Icon(
                imageVector = IosIcons.PersonFill,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(size * 0.56f),
            )
        }
        if (!photoUri.isNullOrBlank() && LocalShowContactPhotos.current) {
            // Decoded at the avatar's pixel size so a full-res contact photo never
            // lands in memory; the initials show through while loading / on error.
            AsyncImage(
                model = remember(photoUri, sizePx) {
                    ImageRequest.Builder(context).data(photoUri).size(sizePx).build()
                },
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}

/**
 * iOS initials: first letter of the first and last words ("Ada Byron Lovelace"
 * → "AL"). Returns null when the name has no letters (an unsaved number), so
 * the caller shows the person silhouette instead of digits.
 */
internal fun iosInitials(name: String): String? {
    val words = name.trim().split(Regex("\\s+")).filter { w -> w.any { it.isLetter() } }
    if (words.isEmpty()) return null
    val first = words.first().first { it.isLetter() }
    val last = if (words.size > 1) words.last().firstOrNull { it.isLetter() } else null
    return (if (last != null) "$first$last" else "$first").uppercase()
}
