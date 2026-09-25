package com.liquidglass.messages.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.liquidglass.messages.R
import com.liquidglass.messages.util.TextDirection

/**
 * Inter (SIL OFL, bundled in res/font) — the closest freely-licensed match to
 * Apple's SF Pro, which may not be shipped outside Apple platforms. Its
 * metrics, x-height and apertures read almost identically at iOS sizes.
 */
val InterFontFamily = FontFamily(
    Font(com.liquidglass.messages.R.font.inter_regular, FontWeight.Normal),
    Font(com.liquidglass.messages.R.font.inter_medium, FontWeight.Medium),
    Font(com.liquidglass.messages.R.font.inter_semibold, FontWeight.SemiBold),
    Font(com.liquidglass.messages.R.font.inter_bold, FontWeight.Bold),
)

/**
 * Vazirmatn — a clean, modern Persian/Arabic typeface (bundled in res/font) used
 * for RTL text so Persian shapes and joins correctly.
 */
val PersianFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

/** Persian typeface for RTL text; Inter otherwise. */
fun fontFamilyFor(text: String): FontFamily =
    if (TextDirection.isRtl(text)) PersianFontFamily else InterFontFamily
