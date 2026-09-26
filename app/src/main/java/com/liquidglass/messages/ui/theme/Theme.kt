package com.liquidglass.messages.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.liquidglass.messages.MessagesApplication
import com.liquidglass.messages.data.local.AppearanceMode
import com.liquidglass.messages.data.local.BubbleStyle
import com.liquidglass.messages.data.local.GlassLook
import com.liquidglass.messages.data.local.TextSize
import com.liquidglass.messages.ui.components.LocalShowContactPhotos
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = iosBlueLight,
    background = Color.White,
    surface = Color.White,
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    error = iosRedLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = iosBlue,
    background = iosBackgroundDark,
    surface = iosBackgroundDark,
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    error = iosRedDark,
)

/** Provides the app's extended [LiquidColors] tokens down the tree. */
val LocalLiquidColors = staticCompositionLocalOf { lightLiquidColors() }

/** Convenience accessor: `LiquidTheme.colors.sentBubbleGradient`. */
object LiquidTheme {
    val colors: LiquidColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLiquidColors.current
}

@Composable
fun LiquidMessagesTheme(
    /** Forces light/dark (previews, tests); null follows the Appearance setting. */
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    // Bubble colour, appearance, glass look and text size follow Settings.
    // Previews have no Application, so they fall back to the defaults.
    val view = LocalView.current
    val settings = if (view.isInEditMode) null else
        (view.context.applicationContext as? MessagesApplication)?.container?.appSettings
    val bubbleStyle = settings?.bubbleStyle?.collectAsState()?.value ?: BubbleStyle.BLUE
    val showPhotos = settings?.showContactPhotos?.collectAsState()?.value ?: true
    val appearance = settings?.appearance?.collectAsState()?.value ?: AppearanceMode.SYSTEM
    val tinted = (settings?.glassLook?.collectAsState()?.value ?: GlassLook.CLEAR) == GlassLook.TINTED
    val textScale = (settings?.textSize?.collectAsState()?.value ?: TextSize.DEFAULT).scale
    val systemDark = isSystemInDarkTheme()
    val hapticsOn = settings?.haptics?.collectAsState()?.value ?: true
    val baseHaptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val haptics = remember(baseHaptics, hapticsOn) {
        if (hapticsOn) baseHaptics else object : androidx.compose.ui.hapticfeedback.HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) = Unit
        }
    }
    val darkTheme = darkTheme ?: when (appearance) {
        AppearanceMode.SYSTEM -> systemDark
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    val liquidColors = remember(darkTheme, bubbleStyle, tinted) {
        if (darkTheme) darkLiquidColors(bubbleStyle, tinted) else lightLiquidColors(bubbleStyle, tinted)
    }
    val baseDensity = androidx.compose.ui.platform.LocalDensity.current
    val density = remember(baseDensity, textScale) {
        androidx.compose.ui.unit.Density(baseDensity.density, baseDensity.fontScale * textScale)
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Compose draws edge-to-edge; keep system bar icons readable.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalLiquidColors provides liquidColors,
        LocalShowContactPhotos provides showPhotos,
        androidx.compose.ui.platform.LocalDensity provides density,
        androidx.compose.ui.platform.LocalHapticFeedback provides haptics,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LiquidTypography,
            shapes = LiquidShapes,
            content = content
        )
    }
}
