package com.liquidglass.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidglass.messages.ui.glass.backdropSource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.liquidglass.messages.data.model.Contact
import com.liquidglass.messages.data.model.Conversation
import com.liquidglass.messages.data.model.Message
import com.liquidglass.messages.data.model.MessageStatus
import com.liquidglass.messages.ui.chat.ChatContent
import com.liquidglass.messages.ui.chat.ChatUiState
import com.liquidglass.messages.ui.conversations.ConversationListContent
import com.liquidglass.messages.ui.conversations.ConversationListUiState
import com.liquidglass.messages.ui.onboarding.SetupScreen
import com.liquidglass.messages.ui.theme.LiquidMessagesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens on the JVM (Robolectric native graphics) and writes
 * PNGs to build/outputs/roborazzi — a visual check of the iOS look without a
 * device. Run: gradlew :app:testDebugUnitTest --tests "*ScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()
    private val min = 60_000L

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { LiquidMessagesTheme(darkTheme = dark) { content() } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val chat = listOf(
        msg(1, "Let's stick with the jigsaws for now", false, 190),
        msg(2, "Anytime, neighbor!", true, 185),
        msg(3, "I found the perfect puzzle for you to challenge the kids", true, 184),
        msg(4, "But only if you carefully count all 1000 pieces before returning it 🤪", true, 183),
        msg(5, "Hmm. Maybe just a 500 piece one? 😂", false, 60),
        msg(6, "Or I can just put the kids on the task!", false, 59),
        msg(7, "Come by if you want them", true, 30, MessageStatus.DELIVERED),
        msg(8, "Thanks for the puzzles!", false, 5),
        msg(9, "Looking forward to Friday! Details: https://example.com/trip or call 09121234567", false, 4),
        msg(10, "😍", true, 3, MessageStatus.SENT),
        msg(13, "📍 My Location\nhttps://maps.google.com/?q=35.699700,51.338000", false, 3),
        msg(12, "Here: www.google.com/maps", true, 3, MessageStatus.SENT),
        msg(11, "سلام! فردا ساعت ۵ می‌بینمت؟", true, 2, MessageStatus.FAILED),
    )

    private fun msg(id: Long, body: String, out: Boolean, minsAgo: Long, status: MessageStatus? = null) =
        Message(
            id = id, threadId = 1, address = "+15551234567", body = body,
            timestamp = now - minsAgo * min, isOutgoing = out,
            status = status ?: if (out) MessageStatus.SENT else MessageStatus.RECEIVED,
        )

    private val conversations = listOf(
        Conversation(1, "+15551234567", "Orkun Kucuksevim", "Looking forward to Friday!", now - 4 * min, unreadCount = 2),
        Conversation(2, "+15557654321", "Olivia Rico", "Everyone's welcome to come by to work on it!", now - 3 * 60 * min),
        Conversation(3, "+15550001111", "Melody Cheung", "I can be at the workshop tomorrow evening", now - 26 * 60 * min),
        Conversation(4, "+989121234567", null, "Your verification code is 482913", now - 3 * 24 * 60 * min),
        Conversation(5, "+15552223333", "Fleur Lasseur", "Awesome, Olivia! Is there anything else we can do?", now - 9 * 24 * 60 * min),
        Conversation(6, "+15554445555", "مریم احمدی", "باشه، خبرت می‌کنم 🙏", now - 40 * 24 * 60 * min),
    )

    @Test fun chatLight() = shot("chat_light", false) { chatContent() }
    @Test fun chatDark() = shot("chat_dark", true) { chatContent() }
    @Test fun listLight() = shot("list_light", false) { listContent() }
    @Test fun listDark() = shot("list_dark", true) { listContent() }
    @Test fun setupLight() = shot("setup_light", false) {
        SetupScreen(false, false, false, false, {}, {}, {})
    }
    @Test fun setupBlocked() = shot("setup_blocked", false) {
        SetupScreen(false, false, true, false, {}, {}, {})
    }

    @Test fun chatMmsGroup() {
        // A real image file for Coil to load.
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val file = java.io.File(ctx.cacheDir, "sample.png")
        val bmp = android.graphics.Bitmap.createBitmap(600, 400, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(android.graphics.Color.rgb(255, 180, 60))
        c.drawCircle(420f, 150f, 90f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(255, 240, 120) })
        c.drawRect(0f, 280f, 600f, 400f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(60, 140, 80) })
        file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val img = com.liquidglass.messages.data.model.Attachment(android.net.Uri.fromFile(file).toString(), "image/png", "beach.png")
        val doc = com.liquidglass.messages.data.model.Attachment("content://x/1", "application/pdf", "Trip plan.pdf")
        val msgs = listOf(
            msg(1, "Who's in for Friday?", false, 30).copy(address = "+15550001"),
            msg(2, "Me!", false, 29).copy(address = "+15550002"),
            msg(3, "", false, 28).copy(address = "+15550002", attachments = listOf(img), isMms = true),
            msg(4, "Here's the plan", true, 5, MessageStatus.DELIVERED).copy(attachments = listOf(doc), isMms = true),
        )
        shot("chat_mms_group", false) {
            ChatContent(
                state = ChatUiState(
                    messages = msgs,
                    contact = Contact("+15550001", "Olivia Rico"),
                    recipients = listOf("+15550001", "+15550002"),
                    participants = mapOf(
                        "+15550001" to Contact("+15550001", "Olivia Rico"),
                        "+15550002" to Contact("+15550002", "Melody Cheung"),
                    ),
                ),
                inputText = "",
                onInputChange = {},
                onSend = {},
                onBack = {},
            )
        }
    }

    @Test fun settingsLight() = shot("settings_light", false) {
        com.liquidglass.messages.ui.settings.SettingsScreen(onBack = {})
    }

    @Test fun contactInfoLight() = shot("contact_info_light", false) {
        com.liquidglass.messages.ui.contactinfo.ContactInfoScreen(threadId = -1, address = "+15551234567", onBack = {})
    }

    @Test fun newMessageSheet() = shot("new_message", false) {
        com.liquidglass.messages.ui.newmessage.NewMessageSheet(onDismiss = {}, onMessageSent = { _, _ -> })
    }

    @Test fun appsMenu() = shot("apps_menu", false) {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            chatContent()
            com.liquidglass.messages.ui.chat.ChatAppsMenu(
                onPick = {},
                modifier = androidx.compose.ui.Modifier
                    .align(androidx.compose.ui.Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 70.dp),
            )
        }
    }

    /** Glass controls over busy, colourful content — shows refraction/blur/rim. */
    @Test fun glassDemo() = shot("glass_demo", false) {
        val backdrop = com.liquidglass.messages.ui.glass.rememberBackdrop()
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .backdropSource(backdrop)
                    .background(androidx.compose.ui.graphics.Color.White),
            ) {
                val palette = listOf(0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF007AFF, 0xFFAF52DE)
                repeat(12) { i ->
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .background(androidx.compose.ui.graphics.Color(palette[i % palette.size])),
                    ) {
                        androidx.compose.material3.Text(
                            "Feel Good · Like A Ribbon · All Of Me",
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 24.sp,
                        )
                    }
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(com.liquidglass.messages.ui.glass.LocalBackdrop provides backdrop) {
                com.liquidglass.messages.ui.components.GlassCapsule(
                    modifier = androidx.compose.ui.Modifier
                        .align(androidx.compose.ui.Alignment.Center)
                        .size(width = 320.dp, height = 70.dp),
                ) { androidx.compose.material3.Text("Home   New   Radio   Library", fontSize = 18.sp) }
                com.liquidglass.messages.ui.components.GlassCircleButton(
                    icon = com.liquidglass.messages.ui.components.IosIcons.Search,
                    contentDescription = null,
                    onClick = {},
                    size = 70.dp,
                    modifier = androidx.compose.ui.Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .padding(bottom = 180.dp),
                )
            }
        }
    }

    @Composable
    private fun chatContent() = ChatContent(
        state = ChatUiState(messages = chat, contact = Contact("+15551234567", "Orkun")),
        inputText = "",
        onInputChange = {},
        onSend = {},
        onBack = {},
    )

    @Composable
    private fun listContent() = ConversationListContent(
        state = ConversationListUiState(conversations = conversations, searchQuery = "", isLoading = false),
        onSearchChange = {},
        onConversationClick = { _, _ -> },
        onNewMessage = {},
        onDeleteThread = {},
    )
}
