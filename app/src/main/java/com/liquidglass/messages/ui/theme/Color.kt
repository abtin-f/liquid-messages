package com.liquidglass.messages.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.liquidglass.messages.data.local.BubbleStyle

/* ----------------------------- Raw palette -----------------------------
 * iOS system colours (UIKit dynamic colours, light / dark). Using the real
 * values — not "close enough" approximations — is most of what makes the app
 * read as Apple rather than Apple-ish.
 */

val iosBlueLight = Color(0xFF007AFF)
val iosBlueDark = Color(0xFF0A84FF)
val iosGreenLight = Color(0xFF34C759)
val iosGreenDark = Color(0xFF30D158)
val iosRedLight = Color(0xFFFF3B30)
val iosRedDark = Color(0xFFFF453A)

// Kept for existing call sites (tint of buttons / links).
val iosBlue = iosBlueDark

// Messages bubble fills. The sent bubble carries a very soft top-to-bottom
// gradient on iOS 17+; received bubbles are flat.
private val blueBubbleLight = listOf(Color(0xFF2E9BFF), Color(0xFF0A7AFF))
private val blueBubbleDark = listOf(Color(0xFF2E9BFF), Color(0xFF0A78F5))
private val greenBubbleLight = listOf(Color(0xFF4CD964), Color(0xFF30BE50))
private val greenBubbleDark = listOf(Color(0xFF3FD45D), Color(0xFF28AE45))

val iosReceivedLight = Color(0xFFE9E9EB)
val iosReceivedDark = Color(0xFF262629)
val iosBackgroundDark = Color(0xFF000000)

/* ----------------------- Semantic colours ----------------------- */

/**
 * App-specific colour tokens not covered by Material's ColorScheme (bubble
 * fills, glass surfaces, iOS label hierarchy). Exposed through [LiquidTheme].
 */
@Immutable
data class LiquidColors(
    val isDark: Boolean,
    val sentBubbleTop: Color,
    val sentBubbleBottom: Color,
    val sentText: Color,
    val receivedBubble: Color,
    val receivedText: Color,
    val chatBackground: Color,
    val listBackground: Color,
    val groupedBackground: Color,
    /** iOS secondarySystemGroupedBackground — cells on a grouped background. */
    val groupedCell: Color,
    val primaryText: Color,
    /** iOS secondaryLabel. */
    val secondaryText: Color,
    /** iOS tertiaryLabel (placeholder text, chevrons). */
    val tertiaryText: Color,
    val accent: Color,
    val destructive: Color,
    /** iOS separator (hairlines). */
    val divider: Color,
    /** iOS tertiarySystemFill — search field / text field background. */
    val fieldBackground: Color,
    // Liquid-glass controls (iOS 26 floating buttons / bars).
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassShadow: Color,
    /** Default contact avatar (no photo): iOS grey gradient. */
    val avatarTop: Color,
    val avatarBottom: Color,
) {
    val sentBubbleGradient: List<Color> get() = listOf(sentBubbleTop, sentBubbleBottom)
}

fun lightLiquidColors(style: BubbleStyle = BubbleStyle.BLUE): LiquidColors {
    val sent = if (style == BubbleStyle.GREEN) greenBubbleLight else blueBubbleLight
    return LiquidColors(
        isDark = false,
        sentBubbleTop = sent[0],
        sentBubbleBottom = sent[1],
        sentText = Color.White,
        receivedBubble = iosReceivedLight,
        receivedText = Color.Black,
        chatBackground = Color.White,
        listBackground = Color.White,
        groupedBackground = Color(0xFFF2F2F7),
        groupedCell = Color.White,
        primaryText = Color.Black,
        secondaryText = Color(0x993C3C43),
        tertiaryText = Color(0x4D3C3C43),
        accent = iosBlueLight,
        destructive = iosRedLight,
        divider = Color(0x4A3C3C43),
        fieldBackground = Color(0x1F767680),
        glassFill = Color(0xC7FFFFFF),
        glassFillStrong = Color(0xF7FFFFFF),
        glassBorder = Color(0x14000000),
        glassHighlight = Color(0xFFFFFFFF),
        glassShadow = Color(0x26000000),
        avatarTop = Color(0xFFA5ABB9),
        avatarBottom = Color(0xFF858B98),
    )
}

fun darkLiquidColors(style: BubbleStyle = BubbleStyle.BLUE): LiquidColors {
    val sent = if (style == BubbleStyle.GREEN) greenBubbleDark else blueBubbleDark
    return LiquidColors(
        isDark = true,
        sentBubbleTop = sent[0],
        sentBubbleBottom = sent[1],
        sentText = Color.White,
        receivedBubble = iosReceivedDark,
        receivedText = Color.White,
        chatBackground = iosBackgroundDark,
        listBackground = iosBackgroundDark,
        groupedBackground = iosBackgroundDark,
        groupedCell = Color(0xFF1C1C1E),
        primaryText = Color.White,
        secondaryText = Color(0x99EBEBF5),
        tertiaryText = Color(0x4DEBEBF5),
        accent = iosBlueDark,
        destructive = iosRedDark,
        divider = Color(0x99545458),
        fieldBackground = Color(0x3D767680),
        glassFill = Color(0xB82C2C2E),
        glassFillStrong = Color(0xF52C2C2E),
        glassBorder = Color(0x1FFFFFFF),
        glassHighlight = Color(0x33FFFFFF),
        glassShadow = Color(0x66000000),
        avatarTop = Color(0xFF8E93A0),
        avatarBottom = Color(0xFF6B707C),
    )
}
