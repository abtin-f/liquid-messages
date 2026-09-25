package com.liquidglass.messages.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.ui.chat.BubbleShape
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.components.pressableScale
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidMessagesTheme
import com.liquidglass.messages.ui.theme.LiquidTheme

/**
 * First-run gate, styled like an iOS "Welcome" sheet.
 *
 * Only the default-SMS role (and the SMS permissions it brings) is required.
 * When Android refuses the role without a real choice — the Android 13/15+
 * "restricted settings" block on sideloaded apps, which is exactly what made
 * the app look like it "won't open" on a Galaxy S25 — the screen switches to a
 * numbered guide with a button straight to the App-info page.
 */
@Composable
fun SetupScreen(
    isDefault: Boolean,
    hasPermissions: Boolean,
    roleBlocked: Boolean,
    permissionsBlocked: Boolean,
    onRequestDefault: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val blocked = (!isDefault && roleBlocked) || (isDefault && permissionsBlocked)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.listBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            HeroBubbles()
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Welcome to\nMessages",
                style = IosType.largeTitle,
                color = colors.primaryText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))

            if (blocked) {
                RestrictedSettingsHelp(onOpenAppSettings = onOpenAppSettings)
            } else {
                Feature(
                    icon = IosIcons.Compose,
                    title = "Your texts, beautifully",
                    body = "Send and receive SMS in a beautiful, fluid iOS-style design.",
                )
                Feature(
                    icon = IosIcons.PersonFill,
                    title = "Names and photos",
                    body = "Allow Contacts to see who you're talking to. Optional.",
                )
                Feature(
                    icon = IosIcons.Search,
                    title = "Private by design",
                    body = "Messages stay on this phone. Nothing is uploaded, ever.",
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val (label, action) = when {
                !isDefault -> "Set as Default SMS App" to onRequestDefault
                !hasPermissions && permissionsBlocked -> "Open Settings" to onOpenAppSettings
                else -> "Continue" to onRequestPermissions
            }
            PrimaryButton(text = if (blocked && !isDefault) "Try Again" else label, onClick = action)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Android only lets the default SMS app send and receive texts.",
                style = IosType.footnote,
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Numbered iOS-style help card for the "restricted setting" block. */
@Composable
private fun RestrictedSettingsHelp(onOpenAppSettings: () -> Unit) {
    val colors = LiquidTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.fieldBackground)
            .padding(16.dp),
    ) {
        Text("Android blocked this setting", style = IosType.headline, color = colors.primaryText)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Apps installed from an APK file can't become the SMS app until you allow it once.",
            style = IosType.footnote,
            color = colors.secondaryText,
        )
        Spacer(Modifier.height(14.dp))
        Step(1, "Tap Open App Info below.")
        Step(2, "Tap ⋮ (top-right) → Allow restricted settings, and confirm.")
        Step(3, "Come back here and tap Try Again.")
        Step(4, "Samsung: if the option is missing, turn off Settings → Security and privacy → Auto Blocker first.")
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Open App Info",
            style = IosType.body,
            fontWeight = FontWeight.SemiBold,
            color = colors.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenAppSettings)
                .padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun Step(n: Int, text: String) {
    val colors = LiquidTheme.colors
    Row(modifier = Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape).background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", style = IosType.caption1, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = IosType.subheadline, color = colors.primaryText)
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(18.dp))
        Column {
            Text(title, style = IosType.headline, color = colors.primaryText)
            Text(body, style = IosType.subheadline, color = colors.secondaryText)
        }
    }
}

/** Two overlapping chat bubbles as the app mark, drawn with the real bubble shape. */
@Composable
private fun HeroBubbles() {
    val colors = LiquidTheme.colors
    Box(Modifier.size(width = 150.dp, height = 104.dp)) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .size(width = 104.dp, height = 58.dp)
                .background(colors.receivedBubble, BubbleShape(outgoing = false, hasTail = true)),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(width = 110.dp, height = 58.dp)
                .background(Brush.verticalGradient(colors.sentBubbleGradient), BubbleShape(outgoing = true, hasTail = true)),
        )
    }
}

/** iOS filled button: 50dp tall, 14dp corners, accent fill. */
@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressableScale(interaction, pressedScale = 0.97f)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.accent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IosType.headline, color = Color.White)
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupPreview() {
    LiquidMessagesTheme(darkTheme = false) {
        SetupScreen(
            isDefault = false,
            hasPermissions = false,
            roleBlocked = false,
            permissionsBlocked = false,
            onRequestDefault = {},
            onRequestPermissions = {},
            onOpenAppSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupBlockedPreview() {
    LiquidMessagesTheme(darkTheme = true) {
        SetupScreen(
            isDefault = false,
            hasPermissions = false,
            roleBlocked = true,
            permissionsBlocked = false,
            onRequestDefault = {},
            onRequestPermissions = {},
            onOpenAppSettings = {},
        )
    }
}
