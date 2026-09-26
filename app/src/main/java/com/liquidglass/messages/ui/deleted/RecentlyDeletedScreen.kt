package com.liquidglass.messages.ui.deleted

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.appContainer
import com.liquidglass.messages.data.local.TrashStore
import com.liquidglass.messages.data.model.Contact
import com.liquidglass.messages.ui.components.ContactAvatar
import com.liquidglass.messages.ui.components.GlassCapsule
import com.liquidglass.messages.ui.components.IosDialogs
import com.liquidglass.messages.ui.components.IosGroupedPage
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.components.iosSection
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme
import com.liquidglass.messages.ui.theme.fontFamilyFor
import kotlinx.coroutines.launch

/** One row of Recently Deleted: a whole conversation, or some messages of one. */
private data class DeletedItem(
    val key: String,
    val threadId: Long,
    val address: String,
    val name: String?,
    val preview: String,
    val deletedAt: Long,
    val wholeThread: Boolean,
    val messageIds: List<Long>,
)

/**
 * iOS Messages › Recently Deleted: deleted conversations and messages, each
 * with the days left before they're removed for good. Tap one to Recover or
 * Delete it; Recover All / Delete All sit at the bottom.
 */
@Composable
fun RecentlyDeletedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val colors = LiquidTheme.colors
    val scope = rememberCoroutineScope()
    val trash by container.trash.state.collectAsState()
    val repo = container.smsRepository

    val items = buildList {
        trash.threads.values.forEach { t ->
            add(
                DeletedItem(
                    key = "t${t.threadId}", threadId = t.threadId, address = t.address, name = t.name,
                    preview = t.snippet.ifBlank { if (t.messageCount > 0) "${t.messageCount} Messages" else "Conversation" },
                    deletedAt = t.deletedAt, wholeThread = true, messageIds = emptyList(),
                ),
            )
        }
        trash.messages.values
            .filter { it.threadId !in trash.threads }
            .groupBy { it.threadId }
            .forEach { (threadId, msgs) ->
                val newest = msgs.maxBy { it.deletedAt }
                add(
                    DeletedItem(
                        key = "m$threadId", threadId = threadId, address = newest.address, name = null,
                        preview = if (msgs.size == 1) newest.snippet.ifBlank { "1 Message" } else "${msgs.size} Messages",
                        deletedAt = newest.deletedAt, wholeThread = false, messageIds = msgs.map { it.messageId },
                    ),
                )
            }
    }.sortedByDescending { it.deletedAt }

    fun recover(item: DeletedItem) = scope.launch {
        if (item.wholeThread) repo.restoreThread(item.threadId) else item.messageIds.forEach { repo.restoreMessage(it) }
        IosDialogs.notice("Recovered", IosIcons.Check)
    }

    fun purge(item: DeletedItem) = scope.launch {
        if (item.wholeThread) repo.purgeThread(item.threadId) else item.messageIds.forEach { repo.purgeMessage(it) }
    }

    Box(Modifier.fillMaxSize()) {
        IosGroupedPage(title = "Recently Deleted", onBack = onBack, backLabel = "Messages") {
            if (items.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 90.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("No Messages", style = IosType.title2, color = colors.primaryText)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Deleted conversations and messages stay here for 30 days.",
                            style = IosType.subheadline,
                            color = colors.secondaryText,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                iosSection(
                    "items",
                    footer = "Messages show the days remaining before deletion. After that time, messages will be permanently deleted. This may take up to 40 days.",
                ) {
                    items.forEachIndexed { i, item ->
                        DeletedRow(item, divider = i < items.lastIndex) {
                            IosDialogs.actionSheet(
                                null,
                                if (item.wholeThread) "This conversation will be recovered." else "These messages will be recovered.",
                                IosDialogs.Action(if (item.wholeThread) "Recover Conversation" else "Recover Messages") { recover(item) },
                                IosDialogs.Action("Delete", IosDialogs.Role.DESTRUCTIVE) {
                                    IosDialogs.alert(
                                        "Delete Permanently?",
                                        "This can't be undone.",
                                        IosDialogs.Action("Cancel", IosDialogs.Role.CANCEL),
                                        IosDialogs.Action("Delete", IosDialogs.Role.DESTRUCTIVE) { purge(item) },
                                    )
                                },
                            )
                        }
                    }
                }
                item(key = "spacer") { Spacer(Modifier.height(80.dp)) }
            }
        }

        if (items.isNotEmpty()) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                GlassCapsule(
                    modifier = Modifier.height(48.dp),
                    onClick = {
                        IosDialogs.alert(
                            "Delete All?",
                            "All ${items.size} items will be permanently deleted. This can't be undone.",
                            IosDialogs.Action("Cancel", IosDialogs.Role.CANCEL),
                            IosDialogs.Action("Delete All", IosDialogs.Role.DESTRUCTIVE) { items.forEach { purge(it) } },
                        )
                    },
                ) {
                    Text("Delete All", style = IosType.body, fontWeight = FontWeight.SemiBold, color = colors.destructive, modifier = Modifier.padding(horizontal = 20.dp))
                }
                GlassCapsule(
                    modifier = Modifier.height(48.dp),
                    onClick = {
                        IosDialogs.alert(
                            "Recover All?",
                            "All ${items.size} items will go back to your conversations.",
                            IosDialogs.Action("Cancel", IosDialogs.Role.CANCEL),
                            IosDialogs.Action("Recover All", IosDialogs.Role.PREFERRED) { items.forEach { recover(it) } },
                        )
                    },
                ) {
                    Text("Recover All", style = IosType.body, fontWeight = FontWeight.SemiBold, color = colors.accent, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun DeletedRow(item: DeletedItem, divider: Boolean, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    val context = LocalContext.current
    val contact by produceState(Contact(number = item.address, name = item.name), item.address) {
        if (item.address.isNotBlank()) value = context.appContainer.smsRepository.resolveContact(item.address).let {
            if (item.name != null) it.copy(name = item.name) else it
        }
    }
    val daysLeft = (TrashStore.RETENTION_DAYS - (System.currentTimeMillis() - item.deletedAt) / TrashStore.DAY_MS).coerceAtLeast(0)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactAvatar(name = contact.displayName, photoUri = contact.photoUri, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.displayName,
                        style = IosType.headline,
                        fontFamily = fontFamilyFor(contact.displayName),
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (daysLeft == 1L) "1 day" else "$daysLeft days",
                        style = IosType.subheadline,
                        color = colors.secondaryText,
                    )
                }
                Text(
                    item.preview,
                    style = IosType.subheadline,
                    fontFamily = fontFamilyFor(item.preview),
                    color = colors.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (divider) {
            Box(Modifier.align(Alignment.BottomStart).padding(start = 70.dp).fillMaxWidth().height(0.5.dp).background(colors.divider))
        }
    }
}
