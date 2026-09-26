package com.liquidglass.messages.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.liquidglass.messages.data.local.ChatWallpaper

/**
 * Conversation backgrounds (iOS 27 "Conversation Backgrounds"): soft vertical
 * gradients, deepened in dark mode so bubbles keep their contrast.
 * Returns null for [ChatWallpaper.NONE] (plain system background).
 */
fun wallpaperBrush(wallpaper: ChatWallpaper, dark: Boolean): Brush? {
    val stops = when (wallpaper) {
        ChatWallpaper.NONE -> return null
        ChatWallpaper.SKY -> if (dark) listOf(Color(0xFF0B2545), Color(0xFF13315C), Color(0xFF0B1A2E)) else listOf(Color(0xFFDDEEFF), Color(0xFFC4E0FF), Color(0xFFEAF4FF))
        ChatWallpaper.SUNSET -> if (dark) listOf(Color(0xFF3A1C32), Color(0xFF5B2333), Color(0xFF2B1720)) else listOf(Color(0xFFFFE3D3), Color(0xFFFFCFC4), Color(0xFFFFEBDD))
        ChatWallpaper.MINT -> if (dark) listOf(Color(0xFF0F2E2A), Color(0xFF143D35), Color(0xFF0B201D)) else listOf(Color(0xFFDDF7EE), Color(0xFFC8F0E2), Color(0xFFEBFBF5))
        ChatWallpaper.LAVENDER -> if (dark) listOf(Color(0xFF231C3D), Color(0xFF2E2352), Color(0xFF17132A)) else listOf(Color(0xFFEDE5FF), Color(0xFFDCCFFF), Color(0xFFF5F0FF))
        ChatWallpaper.MIDNIGHT -> listOf(Color(0xFF0A0F1F), Color(0xFF1B2440), Color(0xFF05070F))
        ChatWallpaper.WATER -> if (dark) listOf(Color(0xFF0E3A4F), Color(0xFF1C5E77), Color(0xFF0B2633)) else listOf(Color(0xFFBFE6F2), Color(0xFF9FD4E8), Color(0xFFDDF3FA))
        ChatWallpaper.AURORA -> listOf(Color(0xFF071A2E), Color(0xFF0F5C6E), Color(0xFF3A2A7A), Color(0xFF0A1024))
    }
    return Brush.verticalGradient(stops)
}
