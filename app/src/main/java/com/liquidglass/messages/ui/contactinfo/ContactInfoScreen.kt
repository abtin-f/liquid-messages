package com.liquidglass.messages.ui.contactinfo

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.appContainer
import com.liquidglass.messages.data.model.Contact
import com.liquidglass.messages.data.model.Message
import com.liquidglass.messages.ui.components.ContactAvatar
import com.liquidglass.messages.ui.components.IconTile
import com.liquidglass.messages.ui.components.IosGroupedPage
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.components.IosRow
import com.liquidglass.messages.ui.components.IosSwitch
import com.liquidglass.messages.ui.components.iosSection
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Conversation details: the iOS 26 contact card (big avatar, name, a row of
 * action tiles) combined with what Google Messages offers for a thread —
 * shared links, conversation stats, hide alerts, block and delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactInfoScreen(
    threadId: Long,
    address: String,
    onBack: () -> Unit,
    onConversationDeleted: () -> Unit = onBack,
) {
    val colors = LiquidTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val container = context.appContainer

    var contact by remember(address) { mutableStateOf(Contact(number = address)) }
    var messages by remember(threadId) { mutableStateOf<List<Message>>(emptyList()) }
    var blocked by remember(address) { mutableStateOf(false) }
    var muted by remember(threadId) { mutableStateOf(container.threadPrefs.isMuted(threadId)) }
    var confirm by remember { mutableStateOf<ConfirmAction?>(null) }

    LaunchedEffect(address, threadId) {
        if (address.isNotBlank()) contact = container.smsRepository.resolveContact(address)
        if (threadId > 0) messages = container.smsRepository.getMessages(threadId)
        blocked = withContext(Dispatchers.IO) { isBlocked(context, address) }
    }
    val links = remember(messages) { extractLinks(messages) }

    IosGroupedPage(title = "", barTitle = contact.displayName, onBack = onBack) {
        item(key = "card") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ContactAvatar(name = contact.displayName, photoUri = contact.photoUri, size = 104.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = contact.displayName,
                    style = IosType.largeTitle.copy(fontSize = IosType.title2.fontSize * 1.25f),
                    color = colors.primaryText,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                if (contact.name != null) {
                    Text(contact.number, style = IosType.subheadline, color = colors.secondaryText)
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionTile("call", IosIcons.Phone, Modifier.weight(1f)) { dial(context, address) }
                    ActionTile("video", IosIcons.Video, Modifier.weight(1f)) {
                        Toast.makeText(context, "Video calls aren't available yet", Toast.LENGTH_SHORT).show()
                    }
                    ActionTile(if (contact.name != null) "contact" else "add", IosIcons.PersonCircle, Modifier.weight(1f)) {
                        openOrAddContact(context, contact)
                    }
                    ActionTile("share", IosIcons.Link, Modifier.weight(1f)) { shareNumber(context, contact) }
                }
            }
        }

        iosSection("number") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { dial(context, address) },
                        onLongClick = {
                            clipboard.setText(AnnotatedString(contact.number))
                            Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                        },
                    )
                    .padding(horizontal = 20.dp, vertical = 11.dp),
            ) {
                Text("mobile", style = IosType.footnote, color = colors.primaryText)
                Text(contact.number, style = IosType.body, color = colors.accent)
            }
        }

        iosSection("alerts") {
            IosRow(title = "Hide Alerts", icon = IosIcons.BellSlash, iconBackground = Color(0xFF5856D6), showDivider = false) {
                IosSwitch(muted, { on ->
                    muted = on
                    container.threadPrefs.setMuted(threadId, on)
                })
            }
        }

        if (links.isNotEmpty()) {
            iosSection("links", header = "Links") {
                links.take(6).forEachIndexed { i, url ->
                    IosRow(
                        title = Uri.parse(url).host?.removePrefix("www.") ?: url,
                        subtitle = url,
                        icon = IosIcons.Link,
                        iconBackground = Color(0xFF007AFF),
                        chevron = true,
                        showDivider = i < minOf(links.size, 6) - 1,
                        onClick = { openUrl(context, url) },
                    )
                }
            }
        }

        if (messages.isNotEmpty()) {
            iosSection("stats", header = "Conversation") {
                IosRow(title = "Messages", value = messages.size.toString())
                IosRow(title = "Sent by You", value = messages.count { it.isOutgoing }.toString())
                IosRow(
                    title = "First Message",
                    value = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(messages.first().timestamp)),
                    showDivider = false,
                )
            }
        }

        iosSection("danger") {
            IosRow(
                title = if (blocked) "Unblock this Caller" else "Block this Caller",
                titleColor = colors.destructive,
                onClick = { confirm = if (blocked) ConfirmAction.UNBLOCK else ConfirmAction.BLOCK },
            )
            IosRow(
                title = "Delete Conversation",
                titleColor = colors.destructive,
                showDivider = false,
                onClick = { confirm = ConfirmAction.DELETE },
            )
        }
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(action.title, style = IosType.headline) },
            text = { Text(action.message, style = IosType.footnote) },
            containerColor = colors.groupedCell,
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        when (action) {
                            ConfirmAction.BLOCK, ConfirmAction.UNBLOCK -> {
                                val ok = withContext(Dispatchers.IO) { setBlocked(context, address, action == ConfirmAction.BLOCK) }
                                if (ok) blocked = action == ConfirmAction.BLOCK
                                else Toast.makeText(context, "Couldn't change the block list.", Toast.LENGTH_SHORT).show()
                            }
                            ConfirmAction.DELETE -> {
                                container.smsRepository.deleteThread(threadId)
                                onConversationDeleted()
                            }
                        }
                    }
                }) {
                    Text(action.confirmLabel, color = colors.destructive, style = IosType.body, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Cancel", color = colors.accent, style = IosType.body) }
            },
        )
    }
}

private enum class ConfirmAction(val title: String, val message: String, val confirmLabel: String) {
    BLOCK("Block this Caller?", "You won't receive calls or messages from this number.", "Block"),
    UNBLOCK("Unblock this Caller?", "You'll receive calls and messages from this number again.", "Unblock"),
    DELETE("Delete Conversation?", "All messages with this person will be deleted from this phone.", "Delete"),
}

/** iOS 26 contact action tile: rounded cell, accent glyph over a small label. */
@Composable
private fun ActionTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.groupedCell)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = IosType.caption1, color = colors.accent)
    }
}

private val UrlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"]+[^\s<>".,;:!?)\]])""")

/** Newest-first distinct links shared in the thread. */
private fun extractLinks(messages: List<Message>): List<String> =
    messages.asReversed()
        .flatMap { m -> UrlRegex.findAll(m.body).map { it.value }.toList() }
        .map { if (it.startsWith("www.", ignoreCase = true)) "https://$it" else it }
        .distinct()

private fun start(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, "No app can handle this.", Toast.LENGTH_SHORT).show() }
}

private fun dial(context: Context, number: String) =
    start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

private fun openUrl(context: Context, url: String) = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

private fun shareNumber(context: Context, contact: Contact) {
    val text = if (contact.name != null) "${contact.name}\n${contact.number}" else contact.number
    start(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}

/** Opens the saved contact, or the "add contact" editor pre-filled with the number. */
private fun openOrAddContact(context: Context, contact: Contact) {
    val lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(contact.number))
    val contactUri = runCatching {
        context.contentResolver.query(
            lookup, arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY), null, null, null,
        )?.use { c -> if (c.moveToFirst()) ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1)) else null }
    }.getOrNull()
    if (contactUri != null) {
        start(context, Intent(Intent.ACTION_VIEW, contactUri))
    } else {
        start(
            context,
            Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
                .putExtra(ContactsContract.Intents.Insert.PHONE, contact.number),
        )
    }
}

/** System block list (the default SMS app may read and write it). */
private fun isBlocked(context: Context, number: String): Boolean = runCatching {
    BlockedNumberContract.canCurrentUserBlockNumbers(context) && BlockedNumberContract.isBlocked(context, number)
}.getOrDefault(false)

private fun setBlocked(context: Context, number: String, block: Boolean): Boolean = runCatching {
    if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) return false
    if (block) {
        val values = ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
        context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
    } else {
        BlockedNumberContract.unblock(context, number)
    }
    true
}.getOrDefault(false)
