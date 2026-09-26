package com.liquidglass.messages.ui.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.liquidglass.messages.MessagesApplication
import com.liquidglass.messages.data.local.MessageMeta
import com.liquidglass.messages.data.local.MessageMetaStore
import com.liquidglass.messages.data.model.Contact
import com.liquidglass.messages.data.model.EffectTag
import com.liquidglass.messages.data.model.ReplyTag
import com.liquidglass.messages.data.model.Message
import com.liquidglass.messages.data.model.MessageEffect
import com.liquidglass.messages.data.model.Reaction
import com.liquidglass.messages.data.reaction.ReactionProtocol
import com.liquidglass.messages.data.reaction.ReactionSyncManager
import com.liquidglass.messages.data.schedule.ScheduledMessage
import com.liquidglass.messages.data.schedule.ScheduledMessageStore
import com.liquidglass.messages.data.sms.SmsRepository
import android.net.Uri
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Immutable UI state for a single chat thread.
 *
 * Field names are part of the screen-layer contract — do not rename.
 *
 * @param messages       the thread's messages, oldest first
 * @param contact        resolved contact for the other party (name/photo)
 * @param isSending      true while an outgoing send is in flight
 * @param subscriptionId SIM subscription used for outgoing sends (-1 = default)
 * @param meta           per-message tapback/effect metadata, keyed by message id
 */
@Immutable
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val contact: Contact,
    val isSending: Boolean = false,
    val subscriptionId: Int = -1,
    val meta: Map<Long, MessageMeta> = emptyMap(),
    /** "Send Later" messages queued for this conversation. */
    val scheduled: List<ScheduledMessage> = emptyList(),
    /** Every other participant; more than one = group MMS conversation. */
    val recipients: List<String> = emptyList(),
    /** Resolved contacts per participant address (group sender names/avatars). */
    val participants: Map<String, Contact> = emptyMap(),
) {
    val isGroup: Boolean get() = recipients.size > 1

    /** Header title: the contact, or "Ali, Sara & 2 more" for groups. */
    val title: String
        get() = if (!isGroup) contact.displayName else {
            val names = recipients.map { participants[it]?.displayName?.substringBefore(' ') ?: it }
            if (names.size <= 3) names.joinToString(", ") else names.take(2).joinToString(", ") + " & ${names.size - 2} more"
        }
}

/**
 * Drives one chat screen: resolves the contact, streams the thread's messages,
 * marks the thread read on entry, and sends outgoing messages.
 *
 * When [threadId] is unknown (`<= 0`) it is resolved lazily from [address] via
 * [SmsRepository.getOrCreateThreadId] before message observation begins.
 */
class ChatViewModel(
    private val appContext: android.content.Context,
    private val repository: SmsRepository,
    private val metaStore: MessageMetaStore,
    private val reactionSync: ReactionSyncManager,
    private val scheduler: ScheduledMessageStore,
    threadId: Long,
    val address: String
) : ViewModel() {

    /** Mutable thread id: may be resolved lazily for brand-new conversations. */
    var threadId: Long = threadId
        private set

    private val _uiState = MutableStateFlow(
        ChatUiState(contact = Contact(number = address))
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** Composer text, kept separate from [uiState] so typing doesn't churn it. */
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    /** Effect chosen in the composer, applied to the next outgoing message. */
    private val _selectedEffect = MutableStateFlow(MessageEffect.NONE)
    val selectedEffect: StateFlow<MessageEffect> = _selectedEffect.asStateFlow()

    /** The message being replied to (shown above the composer). */
    private val _replyTo = MutableStateFlow<Message?>(null)
    val replyTo: StateFlow<Message?> = _replyTo.asStateFlow()

    /** Photos / files staged in the composer (copied into our cache). */
    private val _attachments = MutableStateFlow<List<Uri>>(emptyList())
    val attachments: StateFlow<List<Uri>> = _attachments.asStateFlow()

    /** When set, the next send is scheduled for this time instead (iOS "Send Later"). */
    private val _sendLaterAt = MutableStateFlow<Long?>(null)
    val sendLaterAt: StateFlow<Long?> = _sendLaterAt.asStateFlow()

    /** In-flight reaction-sync jobs keyed by message id, for debouncing rapid taps. */
    private val reactionSendJobs = HashMap<Long, Job>()

    init {
        // Mirror the tapback/effect store into ui state so bubbles re-render the
        // instant a reaction or effect changes.
        viewModelScope.launch {
            metaStore.metas.collect { metas ->
                _uiState.update { it.copy(meta = metas) }
            }
        }
        viewModelScope.launch {
            scheduler.messages.collect { all ->
                _uiState.update { st -> st.copy(scheduled = all.filter { it.address == address }.sortedBy { it.sendAt }) }
            }
        }
        // Resolve the contact for the header.
        viewModelScope.launch {
            val contact = repository.resolveContact(address)
            _uiState.update { it.copy(contact = contact) }
        }
        // Resolve the thread id if needed, then stream messages and mark read.
        viewModelScope.launch {
            if (this@ChatViewModel.threadId <= 0L) {
                this@ChatViewModel.threadId = repository.getOrCreateThreadId(address)
            }
            markThreadRead()
            loadParticipants()
            repository.observeMessages(this@ChatViewModel.threadId).collect { messages ->
                // Dual-SIM: route replies AND reaction syncs over the SIM this
                // conversation actually uses (the incoming SIM, else any known one).
                val sub = messages.lastOrNull { !it.isOutgoing && it.subscriptionId >= 0 }?.subscriptionId
                    ?: messages.lastOrNull { it.subscriptionId >= 0 }?.subscriptionId
                    ?: -1
                _uiState.update { it.copy(messages = messages, subscriptionId = sub) }
                // A message that arrives while the chat is open is read already.
                if (visible && messages.any { !it.isOutgoing && !it.read }) markThreadRead()
            }
        }
    }

    private suspend fun loadParticipants() {
        val recipients = repository.getRecipients(threadId).ifEmpty { listOf(address) }
        val contacts = recipients.associateWith { repository.resolveContact(it) }
        _uiState.update { it.copy(recipients = recipients, participants = contacts) }
    }

    /**
     * Copies a picked photo/file into cache/outgoing right away: picker grants
     * are temporary, and the send (or a Send Later) may happen much later.
     */
    fun addAttachment(source: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val staged = stage(source) ?: return@launch
            _attachments.update { (it + staged).takeLast(MAX_ATTACHMENTS) }
        }
    }

    fun removeAttachment(uri: Uri) {
        _attachments.update { it - uri }
        runCatching { uri.path?.let { java.io.File(it).delete() } }
    }

    private fun stage(source: Uri): Uri? = runCatching {
        val resolver = appContext.contentResolver
        val type = resolver.getType(source)
        val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
            ?: source.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 1..5 }
            ?: "bin"
        val dir = java.io.File(appContext.cacheDir, "outgoing").apply { mkdirs() }
        val file = java.io.File(dir, "att-${System.nanoTime()}.$ext")
        resolver.openInputStream(source)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return null
        Uri.fromFile(file)
    }.getOrNull()

    /** Updates the composer text. */
    fun onInputChange(t: String) {
        _inputText.value = t
    }

    /** Selects the send-effect applied to the next outgoing message. */
    fun onEffectSelected(e: MessageEffect) {
        _selectedEffect.value = e
    }

    /**
     * Toggles a tapback reaction on a message. Tapping the same reaction again
     * clears it; the change is observed back via [metaStore] into [uiState].
     */
    fun setReaction(messageId: Long, reaction: Reaction) {
        // Apply locally (atomic toggle) so the badge updates instantly.
        metaStore.toggleReaction(messageId, reaction)
        val target = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return

        // Debounce the billable cross-device sync per message: a flurry of toggles
        // sends at most ONE control SMS reflecting the FINAL settled state. The send
        // is user-initiated, rate-guarded, and never echoed by the receiver.
        reactionSendJobs[messageId]?.cancel()
        reactionSendJobs[messageId] = viewModelScope.launch {
            delay(REACTION_SEND_DEBOUNCE_MS)
            val finalReaction = metaStore.metaFor(messageId).reaction
            val op = if (finalReaction != null) {
                ReactionProtocol.Op.ADD
            } else {
                ReactionProtocol.Op.REMOVE
            }
            reactionSync.sendReaction(
                address = address,
                subscriptionId = _uiState.value.subscriptionId,
                op = op,
                reaction = finalReaction ?: reaction,
                target = target,
            )
            reactionSendJobs.remove(messageId)
        }
    }

    /**
     * Sends the current composer text (if non-blank). Clears the input
     * immediately for a snappy feel, then hands the body to the repository; the
     * live message stream surfaces the sent row.
     *
     * Any pending composer [selectedEffect] is attached to the freshly-sent
     * outgoing message (located by re-reading the thread and taking the newest
     * outgoing row), then the composer effect resets to NONE.
     */
    fun sendMessage() {
        val text = _inputText.value.trim()
        val files = _attachments.value
        if (text.isBlank() && files.isEmpty()) return
        val effect = _selectedEffect.value
        val reply = _replyTo.value
        val group = _uiState.value.isGroup
        // The effect travels as iOS does over SMS: a readable "(Sent with … effect)"
        // line that Liquid Messages on the other phone turns back into the animation.
        val withEffect = if (text.isBlank()) text else EffectTag.append(text, effect)
        // Replies travel as a short quote line so the other phone can link them too.
        val wire = if (reply != null && withEffect.isNotBlank()) ReplyTag.format(reply, withEffect) else withEffect

        val later = _sendLaterAt.value
        if (later != null && files.isEmpty() && !group) {
            scheduler.schedule(address, wire, later, _uiState.value.subscriptionId)
            resetComposer()
            return
        }

        resetComposer()
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            if (files.isNotEmpty() || group) {
                // Photos/files and group conversations go out as one MMS.
                val to = _uiState.value.recipients.ifEmpty { listOf(address) }
                val result = runCatching { repository.sendMms(to, wire, files, _uiState.value.subscriptionId) }
                    .getOrElse { Result.failure(it) }
                if (result.isFailure) {
                    com.liquidglass.messages.data.mms.MmsLog.log("SEND failed in app: ${result.exceptionOrNull()?.message}")
                    com.liquidglass.messages.ui.components.IosDialogs.alert(
                        "Message Not Sent",
                        "This MMS couldn't be prepared. Check that mobile data is on, then try again. Details are in Settings › MMS Diagnostics.",
                    )
                }
            } else {
                repository.sendMessage(address, wire, _uiState.value.subscriptionId)
            }
            if ((effect != MessageEffect.NONE || reply != null) && threadId > 0L) {
                // The outbox row is inserted before send returns, so the newest
                // outgoing message is the one we just sent.
                val newestOutgoingId = repository.getMessages(threadId).lastOrNull { it.isOutgoing }?.id
                if (newestOutgoingId != null) {
                    if (effect != MessageEffect.NONE) metaStore.setEffect(newestOutgoingId, effect)
                    if (reply != null) metaStore.setReplyTo(newestOutgoingId, reply.id)
                }
            }
            _uiState.update { it.copy(isSending = false) }
        }
    }

    private fun resetComposer() {
        _inputText.value = ""
        _attachments.value = emptyList()
        _sendLaterAt.value = null
        _selectedEffect.value = MessageEffect.NONE
        _replyTo.value = null
    }

    /** Message the next send replies to (swipe-to-reply / menu → Reply). */
    fun setReplyTo(message: Message?) {
        _replyTo.value = message
    }

    /** Arms / clears "Send Later" for the next message. */
    fun setSendLater(at: Long?) {
        _sendLaterAt.value = at
    }

    fun cancelScheduled(id: Long) = scheduler.cancel(id)

    /** Sends a scheduled message immediately (iOS: "Send Now"). */
    fun sendScheduledNow(id: Long) {
        val msg = scheduler.find(id) ?: return
        scheduler.cancel(id)
        viewModelScope.launch { repository.sendMessage(msg.address, msg.body, msg.subscriptionId) }
    }

    /** Sends [text] right away, bypassing the composer (stickers). */
    fun sendText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { repository.sendMessage(address, text, _uiState.value.subscriptionId) }
    }

    /** Appends text to the composer (shared location / contact). */
    fun appendToComposer(text: String) {
        val cur = _inputText.value
        _inputText.value = if (cur.isBlank()) text else "$cur\n$text"
    }

    /** "Not Delivered — tap to try again": re-sends a failed message in place. */
    fun retry(messageId: Long) {
        viewModelScope.launch { repository.resendMessage(messageId) }
    }

    /** Deletes one message (long-press menu → Delete). */
    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            // Goes to Recently Deleted; its reaction/effect stay for a recovery.
            repository.deleteMessage(messageId)
        }
    }

    @Volatile private var visible = false

    /** Called on resume/pause of the chat screen. */
    fun setVisible(on: Boolean) {
        visible = on
        if (on) {
            if (threadId > 0L) ActiveChat.threadId = threadId
            markThreadRead()
        } else {
            if (ActiveChat.threadId == threadId) ActiveChat.threadId = -1L
            // Anything that slipped in while leaving is read too.
            markThreadRead()
        }
    }

    /** Marks the whole thread read (and clears its notification); safe before the id is resolved. */
    fun markThreadRead() {
        viewModelScope.launch {
            if (threadId > 0L) {
                if (visible) ActiveChat.threadId = threadId
                repository.markThreadRead(threadId)
                runCatching { (appContext.applicationContext as com.liquidglass.messages.MessagesApplication).container.notificationHelper.cancelThread(threadId) }
            }
        }
    }

    companion object {
        /** iOS lets you attach up to 10 items; MMS size limits make 5 sensible. */
        private const val MAX_ATTACHMENTS = 5

        /** Debounce window before a settled reaction is synced over SMS. */
        private const val REACTION_SEND_DEBOUNCE_MS = 700L

        /**
         * Factory carrying the per-thread [threadId]/[address] arguments while
         * pulling the repository from the application container.
         */
        fun factory(threadId: Long, address: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MessagesApplication
                    ChatViewModel(
                        appContext = app,
                        repository = app.container.smsRepository,
                        metaStore = app.container.messageMetaStore,
                        reactionSync = app.container.reactionSyncManager,
                        scheduler = app.container.scheduledMessages,
                        threadId = threadId,
                        address = address
                    )
                }
            }
    }
}
