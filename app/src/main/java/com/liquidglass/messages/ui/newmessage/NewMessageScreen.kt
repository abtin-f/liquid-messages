package com.liquidglass.messages.ui.newmessage

import android.app.Activity
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liquidglass.messages.data.model.Contact
import com.liquidglass.messages.ui.chat.MessageInputBar
import com.liquidglass.messages.ui.components.ContactAvatar
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.glass.LocalBackdrop
import com.liquidglass.messages.ui.glass.backdropSource
import com.liquidglass.messages.ui.glass.rememberBackdrop
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme

/**
 * The iOS "New Message" page sheet: title + Cancel, a "To:" field with the
 * blue ⊕ contact picker, live suggestions, and the composer pinned above the
 * keyboard. Presentation (slide-up, background scale) is done by the host.
 */
@Composable
fun NewMessageSheet(
    onDismiss: () -> Unit,
    onMessageSent: (threadId: Long, address: String) -> Unit,
    viewModel: NewMessageViewModel = viewModel(factory = NewMessageViewModel.factory()),
) {
    val colors = LiquidTheme.colors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val backdrop = rememberBackdrop()

    fun close() {
        viewModel.reset()
        onDismiss()
    }
    BackHandler(onBack = ::close)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // ⊕ opens the system contact picker (phone numbers only) — no permission needed.
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    viewModel.onPickContact(Contact(number = c.getString(0), name = c.getString(1), photoUri = c.getString(2)))
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.chatBackground),
    ) {
        // Grabber + header.
        Box(
            Modifier
                .padding(top = 6.dp)
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 5.dp)
                .clip(CircleShape)
                .background(colors.tertiaryText),
        )
        Box(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp)) {
            Text("New Message", style = IosType.headline, color = colors.primaryText, modifier = Modifier.align(Alignment.Center))
            Text(
                "Cancel",
                style = IosType.body,
                color = colors.accent,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(CircleShape)
                    .clickable(onClick = ::close)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }

        // To: row.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp).height(46.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("To:", style = IosType.body, color = colors.secondaryText)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (state.picked != null) {
                    // A picked contact shows as an iOS recipient token.
                    Text(
                        state.picked!!.displayName,
                        style = IosType.body,
                        color = colors.accent,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.12f))
                            .clickable { viewModel.onRecipientChange("") }
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                } else {
                    BasicTextField(
                        value = state.recipient,
                        onValueChange = viewModel::onRecipientChange,
                        singleLine = true,
                        textStyle = IosType.body.copy(color = colors.primaryText),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            Icon(
                imageVector = IosIcons.PlusCircle,
                contentDescription = "Add contact",
                tint = colors.accent,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable {
                        runCatching {
                            pickContact.launch(
                                android.content.Intent(android.content.Intent.ACTION_PICK)
                                    .setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE),
                            )
                        }
                    }
                    .padding(4.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.divider))

        // Suggestions + composer share one backdrop so the composer's glass refracts them.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .backdropSource(backdrop)
                    .background(colors.chatBackground),
            ) {
                items(state.results, key = { it.number + it.displayName }) { c ->
                    SuggestionRow(c) { viewModel.onPickContact(c) }
                }
            }
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                MessageInputBar(
                    text = state.inputText,
                    onTextChange = viewModel::onInputChange,
                    onSend = { viewModel.send(onMessageSent) },
                    isSending = false,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(contact: Contact, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(name = contact.displayName, photoUri = contact.photoUri, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(contact.displayName, style = IosType.body, color = colors.primaryText)
            Text("mobile  ${contact.number}", style = IosType.footnote, color = colors.secondaryText)
        }
    }
}
