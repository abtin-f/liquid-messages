package com.liquidglass.messages.ui.newmessage

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.liquidglass.messages.MessagesApplication
import com.liquidglass.messages.data.model.Contact
import com.liquidglass.messages.data.sms.SmsRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Immutable UI state for the compose / new-message screen.
 *
 * Field names are part of the screen-layer contract — do not rename.
 *
 * @param recipient  the picked or typed recipient address (search box text)
 * @param results    contact search results for the current [recipient] query
 * @param inputText  the message body composed for the new conversation
 */
@Immutable
data class NewMessageUiState(
    val recipient: String = "",
    val results: List<Contact> = emptyList(),
    val inputText: String = "",
    /** Recipient tokens already added (contacts or typed numbers). */
    val picked: List<Contact> = emptyList(),
) {
    /** Everyone a send goes to: the tokens plus a number still being typed. */
    val addresses: List<String>
        get() = (picked.map { it.number } + listOfNotNull(recipient.trim().takeIf { it.looksLikeNumber() }))
            .distinctBy { it.filter(Char::isDigit).takeLast(10) }

    /** Kept for the single-recipient callers. */
    val address: String get() = addresses.firstOrNull() ?: recipient.trim()

    val isGroup: Boolean get() = addresses.size > 1
}

/** A typed recipient that can be texted: mostly digits, optionally with + ( ) - and spaces. */
internal fun String.looksLikeNumber(): Boolean {
    val t = trim()
    return t.count(Char::isDigit) >= 3 && t.all { it.isDigit() || it in "+()- " }
}

/**
 * Drives the compose screen: debounced contact search by name/number, recipient
 * selection, and sending the first message (resolving a thread id on the way).
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NewMessageViewModel(
    private val repository: SmsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewMessageUiState())

    /** Drives the debounced contact search downstream. */
    private val _recipientQuery = MutableStateFlow("")

    /**
     * Search results react to the recipient query: debounced to avoid hammering
     * the contacts provider, and [flatMapLatest] cancels stale queries so only
     * the newest result set survives. Pushed back into [_uiState] via collection.
     */
    val uiState: StateFlow<NewMessageUiState> = _uiState

    init {
        viewModelScope.launch {
            _recipientQuery
                .debounce(200)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    flow {
                        if (query.isBlank()) {
                            emit(emptyList())
                        } else {
                            emit(repository.searchContacts(query))
                        }
                    }
                }
                .collect { results ->
                    _uiState.update { it.copy(results = results) }
                }
        }
    }

    /** Updates the recipient text and re-triggers debounced contact search. */
    fun onRecipientChange(q: String) {
        // A comma or semicolon after a number turns it into a token, like iOS.
        if ((q.endsWith(",") || q.endsWith(";")) && q.dropLast(1).looksLikeNumber()) {
            commitTyped(q.dropLast(1))
            return
        }
        _uiState.update { it.copy(recipient = q) }
        _recipientQuery.value = q
    }

    /** Turns a typed number into a recipient token (Return key, comma). */
    fun commitTyped(text: String = _uiState.value.recipient) {
        val t = text.trim()
        if (!t.looksLikeNumber()) return
        onPickContact(Contact(number = t))
    }

    /** Adds a contact from the results / picker as a recipient token. */
    fun onPickContact(contact: Contact) {
        _uiState.update { s ->
            val key = contact.number.filter(Char::isDigit).takeLast(10)
            val already = s.picked.any { it.number.filter(Char::isDigit).takeLast(10) == key }
            s.copy(recipient = "", results = emptyList(), picked = if (already) s.picked else s.picked + contact)
        }
        // Keep the search dormant so picking a contact doesn't re-open results.
        _recipientQuery.value = ""
    }

    /** Removes a recipient token (tapping it, or backspace on an empty field). */
    fun removeRecipient(contact: Contact) {
        _uiState.update { it.copy(picked = it.picked - contact) }
    }

    /** Updates the composed message body. */
    fun onInputChange(t: String) {
        _uiState.update { it.copy(inputText = t) }
    }

    /**
     * Resolves (or creates) the thread for the current recipient, sends the
     * composed message, and invokes [onSent] with the thread id + address so the
     * caller can navigate into the live chat. No-op if recipient/body is blank.
     */
    fun send(onSent: (threadId: Long, address: String) -> Unit) {
        val addresses = _uiState.value.addresses
        val body = _uiState.value.inputText.trim()
        if (addresses.isEmpty() || body.isBlank()) return
        viewModelScope.launch {
            if (addresses.size > 1) {
                // A group: one MMS thread shared by everyone, like iOS group texts.
                val threadId = repository.getOrCreateGroupThreadId(addresses)
                repository.sendMms(addresses, body, emptyList())
                _uiState.value = NewMessageUiState()
                onSent(threadId, addresses.first())
            } else {
                val address = addresses.first()
                val threadId = repository.getOrCreateThreadId(address)
                repository.sendMessage(address, body, -1)
                _uiState.value = NewMessageUiState()
                onSent(threadId, address)
            }
        }
    }

    /** Clears everything (sheet dismissed). */
    fun reset() {
        _recipientQuery.value = ""
        _uiState.value = NewMessageUiState()
    }

    companion object {
        /** ViewModel factory that pulls the repository from the application container. */
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MessagesApplication
                NewMessageViewModel(app.container.smsRepository)
            }
        }
    }
}
