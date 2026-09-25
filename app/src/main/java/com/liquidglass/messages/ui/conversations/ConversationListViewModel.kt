package com.liquidglass.messages.ui.conversations

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.liquidglass.messages.MessagesApplication
import com.liquidglass.messages.data.model.Conversation
import com.liquidglass.messages.data.sms.SmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Immutable UI state for the conversation list screen.
 *
 * Field names are part of the screen-layer contract — do not rename.
 *
 * @param conversations the (search-filtered) conversation threads, newest first
 * @param searchQuery   current text in the search field
 * @param isLoading     true until the repository emits its first list
 */
@Immutable
data class ConversationListUiState(
    val conversations: List<Conversation> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

/**
 * Drives the conversation list: observes the repository's live conversation
 * stream, applies a case-insensitive search filter, and exposes the result as
 * a single [StateFlow] of [ConversationListUiState].
 */
class ConversationListViewModel(
    private val repository: SmsRepository
) : ViewModel() {

    /** Backing flow for the search box; drives re-filtering of the list. */
    private val _searchQuery = MutableStateFlow("")

    /** Current search text, exposed so the screen can render the field. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Combines the live conversation list with the current search query into the
     * filtered UI state. [stateIn] keeps the upstream observer (and its
     * ContentObserver) alive only while the screen is subscribed, with a small
     * grace window to survive configuration changes.
     */
    val uiState: StateFlow<ConversationListUiState> =
        combine(
            repository.observeConversations(),
            _searchQuery
        ) { conversations, query ->
            val trimmed = query.trim()
            val filtered = if (trimmed.isBlank()) {
                conversations
            } else {
                conversations.filter { it.matches(trimmed) }
            }
            ConversationListUiState(
                conversations = filtered,
                searchQuery = query,
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConversationListUiState()
        )

    /** Updates the search query, re-triggering the combine above. */
    fun onSearchChange(q: String) {
        _searchQuery.value = q
    }

    /** Deletes an entire thread; the live stream removes it from the list. */
    fun deleteThread(threadId: Long) {
        viewModelScope.launch { repository.deleteThread(threadId) }
    }

    /** Marks every message in a thread read; clears its unread badge. */
    fun markRead(threadId: Long) {
        viewModelScope.launch { repository.markThreadRead(threadId) }
    }

    /** Case-insensitive match on display name, raw address, and snippet. */
    private fun Conversation.matches(query: String): Boolean {
        val q = query.lowercase()
        return displayName.lowercase().contains(q) ||
            address.lowercase().contains(q) ||
            snippet.lowercase().contains(q)
    }

    companion object {
        /** ViewModel factory that pulls the repository from the application container. */
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MessagesApplication
                ConversationListViewModel(app.container.smsRepository)
            }
        }
    }
}
