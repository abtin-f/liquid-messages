package com.liquidglass.messages.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import com.liquidglass.messages.ui.chat.ChatContent
import com.liquidglass.messages.ui.chat.ChatUiState
import com.liquidglass.messages.ui.chat.MessageBubble
import com.liquidglass.messages.ui.chat.MessageInputBar
import com.liquidglass.messages.ui.chat.TypingIndicator
import com.liquidglass.messages.ui.components.Avatar
import com.liquidglass.messages.ui.components.GradientSendButton
import com.liquidglass.messages.ui.conversations.ConversationListContent
import com.liquidglass.messages.ui.conversations.ConversationListUiState
import com.liquidglass.messages.ui.conversations.ConversationRow
import com.liquidglass.messages.ui.onboarding.SetupScreen
import com.liquidglass.messages.ui.theme.LiquidMessagesTheme
import com.liquidglass.messages.ui.theme.LiquidTheme

/*
 * ============================================================================
 *  Compose @Preview gallery — the project's living design reference.
 *
 *  Every preview wraps a REAL, already-authored stateless composable (verified
 *  against its source signature) in [LiquidMessagesTheme] and feeds it the
 *  deterministic fixtures from [SampleData]. The two full screens (conversation
 *  list and chat) plus the onboarding screen each ship a dark
 *  (UI_MODE_NIGHT_YES) variant alongside their light variant.
 *
 *  Nothing here touches a ViewModel, the SMS provider, or a live clock, so the
 *  whole file renders in Android Studio's preview pane.
 * ============================================================================
 */

/* ------------------------------------------------------------------------- */
/*  ConversationRow — read + unread                                          */
/* ------------------------------------------------------------------------- */

@Preview(showBackground = true, name = "Row · unread")
@Composable
private fun ConversationRowUnreadPreview() {
    LiquidMessagesTheme {
        ConversationRow(
            conversation = SampleData.unreadConversation,
            onClick = {},
            onDelete = {},
        )
    }
}

@Preview(showBackground = true, name = "Row · read")
@Composable
private fun ConversationRowReadPreview() {
    LiquidMessagesTheme {
        ConversationRow(
            conversation = SampleData.readConversation,
            onClick = {},
            onDelete = {},
        )
    }
}

@Preview(showBackground = true, name = "Row · read + unread stack")
@Composable
private fun ConversationRowStackPreview() {
    LiquidMessagesTheme {
        Column {
            ConversationRow(
                conversation = SampleData.unreadConversation,
                onClick = {},
                onDelete = {},
            )
            ConversationRow(
                conversation = SampleData.readConversation,
                onClick = {},
                onDelete = {},
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/*  Conversation list (stateless Content) — light + dark                     */
/* ------------------------------------------------------------------------- */

private fun conversationListState(): ConversationListUiState =
    ConversationListUiState(
        conversations = SampleData.sampleConversations,
        searchQuery = "",
        isLoading = false,
    )

@Composable
private fun ConversationListPreviewBody() {
    ConversationListContent(
        state = conversationListState(),
        onSearchChange = {},
        onConversationClick = { _, _ -> },
        onNewMessage = {},
        onDeleteThread = {},
    )
}

@Preview(showBackground = true, name = "Conversation list · light", showSystemUi = true)
@Composable
private fun ConversationListLightPreview() {
    LiquidMessagesTheme(darkTheme = false) {
        ConversationListPreviewBody()
    }
}

@Preview(
    showBackground = true,
    name = "Conversation list · dark",
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ConversationListDarkPreview() {
    LiquidMessagesTheme(darkTheme = true) {
        ConversationListPreviewBody()
    }
}

/* ------------------------------------------------------------------------- */
/*  MessageBubble — incoming, outgoing, outgoing-with-status                 */
/* ------------------------------------------------------------------------- */

@Preview(showBackground = true, name = "Bubble · incoming")
@Composable
private fun MessageBubbleIncomingPreview() {
    LiquidMessagesTheme {
        Box(Modifier.background(LiquidTheme.colors.chatBackground)) {
            MessageBubble(
                message = SampleData.sampleMessages(1L).first { !it.isOutgoing },
                isFirstInGroup = true,
                isLastInGroup = true,
            )
        }
    }
}

@Preview(showBackground = true, name = "Bubble · outgoing")
@Composable
private fun MessageBubbleOutgoingPreview() {
    LiquidMessagesTheme {
        Box(Modifier.background(LiquidTheme.colors.chatBackground)) {
            MessageBubble(
                message = SampleData.sampleMessages(1L).first { it.isOutgoing },
                isFirstInGroup = true,
                isLastInGroup = true,
            )
        }
    }
}

@Preview(showBackground = true, name = "Bubble · outgoing + status caption")
@Composable
private fun MessageBubbleStatusPreview() {
    LiquidMessagesTheme {
        Column(
            modifier = Modifier
                .background(LiquidTheme.colors.chatBackground)
                .padding(vertical = 8.dp),
        ) {
            // One bubble per outgoing status, each showing its delivery caption.
            SampleData.statusShowcase.forEach { message ->
                MessageBubble(
                    message = message,
                    isFirstInGroup = true,
                    isLastInGroup = true,
                    showStatus = true,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/*  Chat screen (stateless Content) — light + dark                          */
/* ------------------------------------------------------------------------- */

private fun chatState(): ChatUiState =
    ChatUiState(
        messages = SampleData.sampleMessages(1L),
        contact = SampleData.primaryContact,
        isSending = false,
        subscriptionId = -1,
    )

@Composable
private fun ChatPreviewBody() {
    ChatContent(
        state = chatState(),
        inputText = "",
        onInputChange = {},
        onSend = {},
        onBack = {},
    )
}

@Preview(showBackground = true, name = "Chat · light", showSystemUi = true)
@Composable
private fun ChatLightPreview() {
    LiquidMessagesTheme(darkTheme = false) {
        ChatPreviewBody()
    }
}

@Preview(
    showBackground = true,
    name = "Chat · dark",
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ChatDarkPreview() {
    LiquidMessagesTheme(darkTheme = true) {
        ChatContent(
            state = ChatUiState(
                messages = SampleData.sampleMessages(2L),
                contact = SampleData.sampleContacts[1],
                isSending = false,
                subscriptionId = -1,
            ),
            inputText = "See you then!",
            onInputChange = {},
            onSend = {},
            onBack = {},
        )
    }
}

/* ------------------------------------------------------------------------- */
/*  MessageInputBar — empty + filled                                         */
/* ------------------------------------------------------------------------- */

@Preview(showBackground = true, name = "Input bar · empty")
@Composable
private fun MessageInputBarEmptyPreview() {
    LiquidMessagesTheme {
        Box(Modifier.background(LiquidTheme.colors.chatBackground)) {
            MessageInputBar(
                text = "",
                onTextChange = {},
                onSend = {},
                isSending = false,
            )
        }
    }
}

@Preview(showBackground = true, name = "Input bar · filled")
@Composable
private fun MessageInputBarFilledPreview() {
    LiquidMessagesTheme {
        Box(Modifier.background(LiquidTheme.colors.chatBackground)) {
            MessageInputBar(
                text = "On my way — see you in five!",
                onTextChange = {},
                onSend = {},
                isSending = false,
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/*  TypingIndicator                                                          */
/* ------------------------------------------------------------------------- */

@Preview(showBackground = true, name = "Typing indicator")
@Composable
private fun TypingIndicatorPreview() {
    LiquidMessagesTheme {
        Box(
            modifier = Modifier
                .background(LiquidTheme.colors.chatBackground)
                .padding(vertical = 8.dp),
        ) {
            TypingIndicator()
        }
    }
}

/* ------------------------------------------------------------------------- */
/*  Avatar                                                                   */
/* ------------------------------------------------------------------------- */

@Preview(showBackground = true, name = "Avatar · gallery")
@Composable
private fun AvatarGalleryPreview() {
    LiquidMessagesTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Each name hashes to its own stable gradient; the unknown number
            // exercises the numeric/initials fallback.
            SampleData.sampleContacts.forEach { contact ->
                Avatar(name = contact.displayName, size = 52.dp)
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/*  GradientSendButton — enabled + disabled                                  */
/* ------------------------------------------------------------------------- */

@Preview(showBackground = true, name = "Send button · enabled + disabled")
@Composable
private fun GradientSendButtonPreview() {
    LiquidMessagesTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientSendButton(enabled = true, onClick = {})
            GradientSendButton(enabled = false, onClick = {})
        }
    }
}

/* ------------------------------------------------------------------------- */
/*  SetupScreen (onboarding) — light + dark                                  */
/* ------------------------------------------------------------------------- */

@Preview(showBackground = true, name = "Setup · light (step 1)", showSystemUi = true)
@Composable
private fun SetupScreenLightPreview() {
    LiquidMessagesTheme(darkTheme = false) {
        SetupScreen(
            onRequestDefault = {},
            onRequestPermissions = {},
            isDefault = false,
            hasPermissions = false,
            roleBlocked = false,
            permissionsBlocked = false,
            onOpenAppSettings = {},
        )
    }
}

@Preview(
    showBackground = true,
    name = "Setup · dark (step 2)",
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SetupScreenDarkPreview() {
    LiquidMessagesTheme(darkTheme = true) {
        SetupScreen(
            onRequestDefault = {},
            onRequestPermissions = {},
            isDefault = true,
            hasPermissions = false,
            roleBlocked = false,
            permissionsBlocked = false,
            onOpenAppSettings = {},
        )
    }
}
