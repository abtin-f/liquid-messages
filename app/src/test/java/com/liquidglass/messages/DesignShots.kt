package com.liquidglass.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.liquidglass.messages.data.local.MessageMeta
import com.liquidglass.messages.data.model.Contact
import com.liquidglass.messages.data.model.Conversation
import com.liquidglass.messages.data.model.Message
import com.liquidglass.messages.data.model.MessageStatus
import com.liquidglass.messages.data.model.Reaction
import com.liquidglass.messages.ui.chat.ChatContent
import com.liquidglass.messages.ui.chat.ChatUiState
import com.liquidglass.messages.ui.chat.SendLaterSheet
import com.liquidglass.messages.ui.components.IosDialogHost
import com.liquidglass.messages.ui.components.IosDialogs
import com.liquidglass.messages.ui.conversations.ConversationListContent
import com.liquidglass.messages.ui.conversations.ConversationListUiState
import com.liquidglass.messages.ui.settings.PersonalizePage
import com.liquidglass.messages.ui.settings.SettingsScreen
import com.liquidglass.messages.ui.theme.LiquidMessagesTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.8 design checks: collapsing inbox bar, pins, iOS alerts / action sheets,
 * settings + personalization, picker wheels and two-sided tapbacks.
 * Run: gradlew :app:testDebugUnitTest --tests "*DesignShots*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class DesignShots {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()

    @After fun tearDown() = IosDialogs.dismissAll()

    private fun shot(name: String, dark: Boolean = false, before: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent { LiquidMessagesTheme(darkTheme = dark) { IosDialogHost { content() } } }
        compose.waitForIdle()
        before()
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/design_$name.png")
    }

    private val conversations = listOf(
        Conversation(1, "+989121112233", "Sara Ahmadi", "On my way!", now - 2 * 60_000, isOutgoingSnippet = true),
        Conversation(2, "+989351234567", "Ali Rezaei", "Did you see the photos? 📷", now - 35 * 60_000, unreadCount = 2),
        Conversation(3, "+15551234567", "Olivia Rico", "Let's catch up this weekend", now - 3 * 3600_000),
        Conversation(4, "+989120001111", null, "Your verification code is 482913", now - 26 * 3600_000),
        Conversation(5, "+989125556677", "مریم", "باشه، خبرت می‌کنم 🙏", now - 3 * 86_400_000),
        Conversation(7, "+15557654321", "Alex Morgan", "Thanks again! 🙌", now - 9 * 86_400_000),
        Conversation(8, "+15557654322", "Jordan Lee", "See you at 7", now - 10 * 86_400_000),
    )

    @Composable
    private fun inbox() = ConversationListContent(
        state = ConversationListUiState(conversations = conversations, isLoading = false),
        onSearchChange = {},
        onConversationClick = { _, _ -> },
        onNewMessage = {},
        onDeleteThread = {},
        pinned = listOf(1L, 2L, 3L),
        muted = setOf(7L),
    )

    @Test fun inboxLight() = shot("inbox") { inbox() }
    @Test fun inboxDark() = shot("inbox_dark", dark = true) { inbox() }

    @Test fun deleteAlert() = shot("alert", before = {
        IosDialogs.alert(
            "Delete Conversation?",
            "This conversation will be deleted from this phone. This action cannot be undone.",
            IosDialogs.Action("Cancel", IosDialogs.Role.CANCEL),
            IosDialogs.Action("Delete", IosDialogs.Role.DESTRUCTIVE),
        )
    }) { inbox() }

    @Test fun deleteAlertDark() = shot("alert_dark", dark = true, before = {
        IosDialogs.alert(
            "Delete Conversation?",
            "This conversation will be deleted from this phone. This action cannot be undone.",
            IosDialogs.Action("Cancel", IosDialogs.Role.CANCEL),
            IosDialogs.Action("Delete", IosDialogs.Role.DESTRUCTIVE),
        )
    }) { inbox() }

    @Test fun actionSheet() = shot("action_sheet", before = {
        IosDialogs.actionSheet(
            null,
            "This message will be deleted from this phone.",
            IosDialogs.Action("Delete Message", IosDialogs.Role.DESTRUCTIVE),
        )
    }) { chat() }

    @Test fun hud() = shot("hud", before = { IosDialogs.notice("Pinned", com.liquidglass.messages.ui.components.IosIcons.Pin) }) { inbox() }

    @Test fun settings() = shot("settings") { SettingsScreen(onBack = {}) }
    @Test fun personalize() = shot("personalize") { PersonalizePage(onBack = {}) }
    @Test fun personalizeDark() = shot("personalize_dark", dark = true) { PersonalizePage(onBack = {}) }
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun sendLater() {
        compose.setContent { LiquidMessagesTheme(darkTheme = false) { SendLaterSheet(onPick = {}, onDismiss = {}) } }
        compose.waitForIdle()
        compose.onNodeWithText("Custom…").performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/design_send_later.png")
    }

    private fun m(id: Long, body: String, out: Boolean, minsAgo: Long) = Message(
        id = id, threadId = 1, address = if (out) "" else "+989121112233", body = body,
        timestamp = now - minsAgo * 60_000, isOutgoing = out,
        status = if (out) MessageStatus.DELIVERED else MessageStatus.RECEIVED,
    )

    @Composable
    private fun chat() {
        val msgs = listOf(
            m(1, "Dinner at 8?", false, 30),
            m(2, "Perfect, see you there!", true, 28),
            m(3, "I'll bring dessert 🍰", false, 20),
        )
        ChatContent(
            state = ChatUiState(
                messages = msgs,
                contact = Contact("+989121112233", "Sara Ahmadi"),
                meta = mapOf(
                    // Both people reacted to the same bubble.
                    2L to MessageMeta(reaction = Reaction.LOVE, theirReaction = Reaction.LAUGH),
                    3L to MessageMeta(theirReaction = Reaction.LIKE),
                    1L to MessageMeta(reaction = Reaction.LIKE),
                ),
            ),
            inputText = "",
            onInputChange = {},
            onSend = {},
            onBack = {},
        )
    }

    @Test fun reactions() = shot("reactions") { chat() }

    @Test fun searchPage() = shot("conv_search") {
        val msgs = listOf(
            m(1, "Dinner at 8?", false, 300), m(2, "Dinner sounds great", true, 200), m(3, "Where for dinner?", false, 20),
        )
        com.liquidglass.messages.ui.contactinfo.SearchPage(msgs, "Sara", onBack = {})
    }

    @Test fun sharedGrid() = shot("conv_shared") {
        val msgs = listOf(
            m(1, "Look https://example.com/menu", false, 300),
            m(2, "📍 My Location\nhttps://maps.google.com/?q=35.6997,51.338", true, 200),
        )
        val shared = com.liquidglass.messages.ui.contactinfo.SharedContent.from(msgs)
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.fillMaxSize().background(com.liquidglass.messages.ui.theme.LiquidTheme.colors.groupedBackground),
        ) {
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(80.dp))
            com.liquidglass.messages.ui.contactinfo.SharedCategoryGrid(shared) {}
        }
    }

    @Test fun contactInfo() = shot("contact_info", dark = true) {
        com.liquidglass.messages.ui.contactinfo.ContactInfoScreen(threadId = -1, address = "+989121112233", onBack = {})
    }
    @Test fun contactInfoLight() = shot("contact_info_light") {
        com.liquidglass.messages.ui.contactinfo.ContactInfoScreen(threadId = -1, address = "+989121112233", onBack = {})
    }

    @Test fun recentlyDeleted() {
        val trash = androidx.test.core.app.ApplicationProvider.getApplicationContext<MessagesApplication>().container.trash
        val day = 86_400_000L
        trash.trashThread(com.liquidglass.messages.data.local.TrashStore.TrashedThread(11, now, now - 3 * day, "+989121112233", "Sara Ahmadi", "See you tomorrow 👋", 42))
        trash.trashThread(com.liquidglass.messages.data.local.TrashStore.TrashedThread(12, now, now - 20 * day, "+15551234567", null, "Your code is 482913", 3))
        trash.trashMessage(com.liquidglass.messages.data.local.TrashStore.TrashedMessage(99, 13, now - day, "+989351234567", "Oops, wrong chat"))
        shot("recently_deleted") { com.liquidglass.messages.ui.deleted.RecentlyDeletedScreen(onBack = {}) }
    }
}
