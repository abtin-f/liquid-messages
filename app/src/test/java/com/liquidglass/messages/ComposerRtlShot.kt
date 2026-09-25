package com.liquidglass.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.liquidglass.messages.ui.chat.MessageInputBar
import com.liquidglass.messages.ui.theme.LiquidMessagesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Composer with Persian / mixed text — checks right-to-left typing in the field. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h400dp-xxhdpi")
class ComposerRtlShot {
    @get:Rule val compose = createComposeRule()

    @Test fun composerRtl() {
        compose.setContent {
            LiquidMessagesTheme(darkTheme = false) {
                Column(Modifier.background(Color.White).padding(vertical = 8.dp)) {
                    listOf(
                        "سلام، فردا ساعت ۵ می‌بینمت؟",
                        "باشه ok، لینکش: example.com",
                        "Hello سلام",
                        "یک متن طولانی فارسی که باید چند خط بشه تا ببینیم تراز و جهتش توی کادر درسته یا نه",
                    ).forEach { t ->
                        MessageInputBar(text = t, onTextChange = {}, onSend = {}, isSending = false, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/composer_rtl.png")
    }
}
