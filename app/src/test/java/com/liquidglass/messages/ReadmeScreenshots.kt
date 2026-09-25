package com.liquidglass.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.liquidglass.messages.data.model.Contact
import com.liquidglass.messages.data.model.Conversation
import com.liquidglass.messages.data.model.Message
import com.liquidglass.messages.data.model.MessageStatus
import com.liquidglass.messages.ui.chat.ChatAppsMenu
import com.liquidglass.messages.ui.chat.ChatContent
import com.liquidglass.messages.ui.chat.ChatUiState
import com.liquidglass.messages.ui.conversations.ConversationListContent
import com.liquidglass.messages.ui.conversations.ConversationListUiState
import com.liquidglass.messages.ui.theme.LiquidMessagesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Curated screens for the README / GitHub release (docs/screenshots).
 * Run: gradlew :app:testDebugUnitTest --tests "*ReadmeScreenshots*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ReadmeScreenshots {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { LiquidMessagesTheme(darkTheme = dark) { content() } }
        compose.waitForIdle()
        // Give map tiles / images a moment to arrive.
        Thread.sleep(2500)
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/readme_$name.png")
    }

    private fun m(id: Long, body: String, out: Boolean, minsAgo: Long, status: MessageStatus? = null, from: String = "+989121112233") =
        Message(
            id = id, threadId = 1, address = if (out) "" else from, body = body,
            timestamp = now - minsAgo * 60_000, isOutgoing = out,
            status = status ?: if (out) MessageStatus.SENT else MessageStatus.RECEIVED,
        )

    private val chat = listOf(
        m(1, "Hey! Are we still on for tonight? 🎬", false, 42),
        m(2, "Yes! The 8:30 show", true, 40),
        m(3, "Tickets are here: https://cinema.example.com/tonight", true, 40),
        m(4, "Perfect 😍", false, 12),
        m(5, "📍 My Location\nhttps://maps.google.com/?q=35.699700,51.338000", false, 11),
        m(6, "I'm right by the square, come find me", false, 11),
        m(7, "On my way!", true, 2, MessageStatus.DELIVERED),
    )

    private val persian = listOf(
        m(1, "سلام! فردا کلاس ساعت چنده؟", false, 30),
        m(2, "ساعت ۱۰ صبح، یادت نره جزوه رو بیاری", true, 28),
        m(3, "مرسی 🙏 تا فردا", false, 25),
        m(4, "👍", true, 24, MessageStatus.DELIVERED),
    )

    private val conversations = listOf(
        Conversation(1, "+989121112233", "Sara Ahmadi", "On my way!", now - 2 * 60_000, isOutgoingSnippet = true),
        Conversation(2, "+989351234567", "Ali Rezaei", "Did you see the photos? 📷", now - 35 * 60_000, unreadCount = 2),
        Conversation(3, "+15551234567", "Olivia Rico", "Let's catch up this weekend", now - 3 * 3600_000),
        Conversation(4, "+989120001111", null, "Your verification code is 482913", now - 26 * 3600_000),
        Conversation(5, "+989125556677", "مریم", "باشه، خبرت می‌کنم 🙏", now - 3 * 86_400_000),
        Conversation(
            6, "+15550001", null, "📍 My Location", now - 5 * 86_400_000,
            recipients = listOf("+15550001", "+15550002", "+15550003"),
            recipientNames = listOf("Melody Cheung", "Fleur Lasseur", "Jan Novak"),
        ),
        Conversation(7, "+15557654321", "Alex Morgan", "Thanks again! 🙌", now - 9 * 86_400_000),
    )

    @Composable
    private fun chatScreen(messages: List<Message>, name: String) = ChatContent(
        state = ChatUiState(messages = messages, contact = Contact("+989121112233", name)),
        inputText = "",
        onInputChange = {},
        onSend = {},
        onBack = {},
    )

    @Composable
    private fun listScreen() = ConversationListContent(
        state = ConversationListUiState(conversations = conversations, searchQuery = "", isLoading = false),
        onSearchChange = {},
        onConversationClick = { _, _ -> },
        onNewMessage = {},
        onDeleteThread = {},
    )

    @Test fun list() = shot("list", false) { listScreen() }
    @Test fun listDark() = shot("list_dark", true) { listScreen() }
    @Test fun chat() = shot("chat", false) { chatScreen(chat, "Sara Ahmadi") }
    @Test fun chatDark() = shot("chat_dark", true) { chatScreen(chat, "Sara Ahmadi") }
    @Test fun persianChat() = shot("chat_fa", false) { chatScreen(persian, "Reza") }
    @Test fun settings() = shot("settings", false) { com.liquidglass.messages.ui.settings.SettingsScreen(onBack = {}) }
    @Test fun apps() = shot("apps", false) {
        Box(Modifier.fillMaxSize()) {
            chatScreen(persian, "Reza")
            ChatAppsMenu(onPick = {}, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 70.dp))
        }
    }
    @Test fun newMessage() = shot("new_message", false) {
        com.liquidglass.messages.ui.newmessage.NewMessageSheet(onDismiss = {}, onMessageSent = { _, _ -> })
    }
}
