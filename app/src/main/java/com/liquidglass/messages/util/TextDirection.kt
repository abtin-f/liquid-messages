package com.liquidglass.messages.util

import androidx.compose.ui.unit.LayoutDirection

/**
 * Per-message bidirectional-text helpers. An SMS thread freely mixes LTR
 * (English/numbers) and RTL (Persian/Arabic) messages, so each bubble's
 * direction is decided from the message's own first strong character rather than
 * from the app/system locale.
 */
object TextDirection {

    /** True when [text]'s first strong directional character is right-to-left. */
    fun isRtl(text: String): Boolean {
        for (ch in text) {
            when (Character.getDirectionality(ch)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> return true

                Character.DIRECTIONALITY_LEFT_TO_RIGHT,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> return false

                else -> { /* neutral (digits, punctuation, emoji): keep scanning */ }
            }
        }
        return false
    }

    /** Compose [LayoutDirection] appropriate for [text]. */
    fun layoutDirection(text: String): LayoutDirection =
        if (isRtl(text)) LayoutDirection.Rtl else LayoutDirection.Ltr
}
