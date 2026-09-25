package com.liquidglass.messages.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The iOS Dynamic Type scale at the default ("Large") size, set in Inter.
 * Tracking values follow Apple's SF Pro tracking table for each size.
 *
 *  Large Title 34/41 bold · Headline 17/22 semibold · Body 17/22 ·
 *  Subheadline 15/20 · Footnote 13/18 · Caption 1 12/16 · Caption 2 11/13
 */
object IosType {
    val largeTitle = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = 0.37.sp)
    val title2 = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.26).sp)
    val headline = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.43).sp)
    val body = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.43).sp)
    val callout = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.31).sp)
    val subheadline = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.23).sp)
    val footnote = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.08).sp)
    val caption1 = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp)
    val caption2 = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.06.sp)
}

/** Material slots mapped onto the iOS scale so any Material widget still looks iOS. */
val LiquidTypography = Typography(
    displaySmall = IosType.largeTitle,
    headlineLarge = IosType.largeTitle,
    headlineMedium = IosType.title2,
    headlineSmall = IosType.title2,
    titleLarge = IosType.headline,
    titleMedium = IosType.headline,
    titleSmall = IosType.headline,
    bodyLarge = IosType.body,
    bodyMedium = IosType.subheadline,
    bodySmall = IosType.footnote,
    labelLarge = IosType.body,
    labelMedium = IosType.footnote,
    labelSmall = IosType.caption2,
)
