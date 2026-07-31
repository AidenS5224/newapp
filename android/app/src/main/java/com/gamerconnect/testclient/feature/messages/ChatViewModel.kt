package com.gamerconnect.testclient.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamerconnect.testclient.data.messages.ChatMessage
import com.gamerconnect.testclient.data.messages.MessageSearchResult
import com.gamerconnect.testclient.data.messages.MessagesRepository
import com.gamerconnect.testclient.data.messages.TypingUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.time.Instant
import kotlinx.coroutines.launch
import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.auth.auth

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentUserId: String? = null,
    val messageDraft: String = "",
    val typingUsers: List<TypingUser> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingOlder: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val errorMessage: String? = null,
    val isSending: Boolean = false,
    val scrollToBottomSignal: Int = 0,
    val isSearchMode: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<MessageSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchErrorMessage: String? = null,
    val searchHighlightMessageId: String? = null,
    val scrollToMessageId: String? = null
)

class ChatViewModel(
    private val repository: MessagesRepository = MessagesRepository()
) : ViewModel() {

    private var realtimeJob: Job? = null
    private var typingRealtimeJob: Job? = null
    private var typingIdleJob: Job? = null
    private var typingExpiryJob: Job? = null
    private var typingSendJob: Job? = null
    private var searchJob: Job? = null
    private var highlightJob: Job? = null
    private val messagePageSize = 50
    private var oldestLoadedPage = 0
    private var activeConversationId: String? = null
    private var localTypingConversationId: String? = null
    private var isLocalTyping = false
    private var lastTypingTrueSentAtMillis = 0L

    fun observeMessages(
        conversationId: String
    ) {
        realtimeJob?.cancel()

        realtimeJob = viewModelScope.launch {
            runCatching {
                repository.observeInsertedMessages(conversationId)
                    .collect {
                        loadMessages(
                            conversationId = conversationId,
                            showLoading = false
                        )
                    }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        errorMessage = state.errorMessage
                            ?: "Live message updates are temporarily unavailable."
                    )
                }
            }
        }
    }

    fun observeTyping(
        conversationId: String
    ) {
        typingRealtimeJob?.cancel()
        typingExpiryJob?.cancel()

        typingRealtimeJob = viewModelScope.launch {
            runCatching {
                refreshTypingUsers(conversationId)
                repository.observeTypingChanges(conversationId)
                    .collect {
                        refreshTypingUsers(conversationId)
                    }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        typingUsers = emptyList()
                    )
                }
            }
        }

        typingExpiryJob = viewModelScope.launch {
            while (true) {
                delay(TYPING_EXPIRY_CHECK_MS)
                expireTypingUsers()
            }
        }
    }

    fun updateDraft(
        conversationId: String,
        draft: String
    ) {
        _uiState.update {
            it.copy(messageDraft = draft)
        }

        handleDraftTypingChange(
            conversationId = conversationId,
            draft = draft
        )
    }

    fun enterSearchMode() {
        _uiState.update {
            it.copy(
                isSearchMode = true,
                searchErrorMessage = null
            )
        }
    }

    fun closeSearchMode() {
        searchJob?.cancel()
        highlightJob?.cancel()
        _uiState.update {
            it.copy(
                isSearchMode = false,
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
                searchErrorMessage = null,
                searchHighlightMessageId = null,
                scrollToMessageId = null
            )
        }
    }

    fun updateSearchQuery(
        conversationId: String,
        query: String
    ) {
        val safeQuery = query.take(MAX_SEARCH_QUERY_LENGTH)
        searchJob?.cancel()

        _uiState.update {
            it.copy(
                searchQuery = safeQuery,
                searchErrorMessage = null
            )
        }

        val trimmedQuery = safeQuery.trim()
        if (trimmedQuery.isBlank()) {
            _uiState.update {
                it.copy(
                    searchResults = emptyList(),
                    isSearching = false
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)

            _uiState.update {
                it.copy(isSearching = true)
            }

            runCatching {
                repository.searchMessages(
                    conversationId = conversationId,
                    query = trimmedQuery,
                    limit = SEARCH_RESULT_LIMIT
                )
            }.onSuccess { results ->
                if (_uiState.value.searchQuery.trim() == trimmedQuery) {
                    _uiState.update {
                        it.copy(
                            searchResults = results,
                            isSearching = false,
                            searchErrorMessage = null
                        )
                    }
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        searchErrorMessage =
                            "Unable to search messages right now."
                    )
                }
            }
        }
    }

    fun selectSearchResult(
        conversationId: String,
        result: MessageSearchResult
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    searchResults = emptyList(),
                    searchErrorMessage = null
                )
            }

            val loaded = ensureMessageLoaded(
                conversationId = conversationId,
                messageId = result.messageId
            )

            if (!loaded) {
                _uiState.update {
                    it.copy(
                        searchErrorMessage =
                            "That message is too far back to load quickly."
                    )
                }
                return@launch
            }

            highlightJob?.cancel()
            _uiState.update {
                it.copy(
                    isSearchMode = false,
                    searchQuery = "",
                    searchResults = emptyList(),
                    isSearching = false,
                    searchErrorMessage = null,
                    searchHighlightMessageId = result.messageId,
                    scrollToMessageId = result.messageId
                )
            }

            highlightJob = viewModelScope.launch {
                delay(SEARCH_HIGHLIGHT_MS)
                _uiState.update {
                    if (it.searchHighlightMessageId == result.messageId) {
                        it.copy(searchHighlightMessageId = null)
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun consumeSearchScrollTarget() {
        _uiState.update {
            it.copy(scrollToMessageId = null)
        }
    }

    fun sendMessage(
        conversationId: String,
        body: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSending = true,
                    errorMessage = null
                )
            }

            clearLocalTyping(conversationId)

            runCatching {
                repository.sendMessage(
                    conversationId = conversationId,
                    body = body
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        messageDraft = "",
                        isSending = false,
                        scrollToBottomSignal = it.scrollToBottomSignal + 1
                    )
                }

                onSuccess()

                loadMessages(
                    conversationId = conversationId,
                    showLoading = false
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = "Message failed to send. Check your connection and try again."
                    )
                }
            }
        }
    }

    private val _uiState = MutableStateFlow(
        ChatUiState(
            currentUserId = SupabaseProvider.client.auth
                .currentUserOrNull()
                ?.id
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun loadOlderMessages(
        conversationId: String
    ) {
        val state = _uiState.value

        if (
            state.isLoading ||
            state.isLoadingOlder ||
            !state.hasMoreMessages
        ) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingOlder = true,
                    errorMessage = null
                )
            }

            val nextPage = oldestLoadedPage + 1

            runCatching {
                repository.getMessages(
                    conversationId = conversationId,
                    page = nextPage,
                    pageSize = messagePageSize
                )
            }.onSuccess { olderMessages ->
                oldestLoadedPage = nextPage

                _uiState.update { currentState ->
                    currentState.copy(
                        messages = (
                                olderMessages + currentState.messages
                                ).distinctBy { message -> message.id },
                        isLoadingOlder = false,
                        hasMoreMessages =
                            olderMessages.size == messagePageSize,
                        errorMessage = null
                    )
                }
            }.onFailure {
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoadingOlder = false,
                        errorMessage =
                            "Unable to load older messages."
                    )
                }
            }
        }
    }

    fun loadMessages(
        conversationId: String,
        showLoading: Boolean = true
    ) {

        if (activeConversationId != conversationId) {
            activeConversationId = conversationId
            oldestLoadedPage = 0
            searchJob?.cancel()
            highlightJob?.cancel()
            _uiState.update {
                it.copy(
                    messageDraft = "",
                    typingUsers = emptyList(),
                    isSearchMode = false,
                    searchQuery = "",
                    searchResults = emptyList(),
                    isSearching = false,
                    searchErrorMessage = null,
                    searchHighlightMessageId = null,
                    scrollToMessageId = null
                )
            }
        }

        viewModelScope.launch {
            if (showLoading) {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        errorMessage = null
                    )
                }
            }

            runCatching {
                repository.getMessages(
                    conversationId = conversationId,
                    page = 0,
                    pageSize = (oldestLoadedPage + 1) * messagePageSize
                )
            }.onSuccess { messages ->
                repository.markConversationAsRead(conversationId)

                _uiState.update {
                    it.copy(
                        messages = messages,
                        isLoading = false,
                        hasMoreMessages = messages.size == messagePageSize,
                        errorMessage = null
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = state.errorMessage
                            ?: "Unable to refresh messages. Check your connection."
                    )
                }
            }
        }
    }
    fun leaveConversation(
        conversationId: String
    ) {
        typingRealtimeJob?.cancel()
        typingRealtimeJob = null
        typingExpiryJob?.cancel()
        typingExpiryJob = null
        typingIdleJob?.cancel()
        typingIdleJob = null
        typingSendJob?.cancel()
        typingSendJob = null
        searchJob?.cancel()
        searchJob = null
        highlightJob?.cancel()
        highlightJob = null

        _uiState.update {
            it.copy(
                typingUsers = emptyList(),
                messageDraft = "",
                isSearchMode = false,
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
                searchErrorMessage = null,
                searchHighlightMessageId = null,
                scrollToMessageId = null
            )
        }

        if (isLocalTyping && localTypingConversationId == conversationId) {
            viewModelScope.launch {
                runCatching {
                    repository.setTyping(
                        conversationId = conversationId,
                        isTyping = false,
                        expiresAt = Instant.now()
                    )
                }
            }
        }

        isLocalTyping = false
        localTypingConversationId = null
        lastTypingTrueSentAtMillis = 0L
    }

    override fun onCleared() {
        val conversationId = localTypingConversationId
        realtimeJob?.cancel()
        typingRealtimeJob?.cancel()
        typingExpiryJob?.cancel()
        typingIdleJob?.cancel()
        typingSendJob?.cancel()
        searchJob?.cancel()
        highlightJob?.cancel()

        if (conversationId != null && isLocalTyping) {
            viewModelScope.launch {
                runCatching {
                    repository.setTyping(
                        conversationId = conversationId,
                        isTyping = false,
                        expiresAt = Instant.now()
                    )
                }
            }
        }

        super.onCleared()
    }

    private fun handleDraftTypingChange(
        conversationId: String,
        draft: String
    ) {
        if (draft.isBlank()) {
            typingIdleJob?.cancel()
            viewModelScope.launch {
                clearLocalTyping(conversationId)
            }
            return
        }

        val now = System.currentTimeMillis()
        if (!isLocalTyping ||
            localTypingConversationId != conversationId ||
            now - lastTypingTrueSentAtMillis >= TYPING_TRUE_THROTTLE_MS
        ) {
            sendTypingState(
                conversationId = conversationId,
                isTyping = true
            )
        }

        typingIdleJob?.cancel()
        typingIdleJob = viewModelScope.launch {
            delay(TYPING_IDLE_TIMEOUT_MS)
            clearLocalTyping(conversationId)
        }
    }

    private fun sendTypingState(
        conversationId: String,
        isTyping: Boolean
    ) {
        if (isTyping) {
            isLocalTyping = true
            localTypingConversationId = conversationId
            lastTypingTrueSentAtMillis = System.currentTimeMillis()
        } else {
            isLocalTyping = false
            localTypingConversationId = null
            lastTypingTrueSentAtMillis = 0L
        }

        typingSendJob?.cancel()
        typingSendJob = viewModelScope.launch {
            runCatching {
                repository.setTyping(
                    conversationId = conversationId,
                    isTyping = isTyping,
                    expiresAt = if (isTyping) {
                        Instant.now().plusMillis(TYPING_REMOTE_EXPIRY_MS)
                    } else {
                        Instant.now()
                    }
                )
            }
        }
    }

    private suspend fun clearLocalTyping(
        conversationId: String
    ) {
        typingIdleJob?.cancel()

        if (!isLocalTyping || localTypingConversationId != conversationId) {
            return
        }

        isLocalTyping = false
        localTypingConversationId = null
        lastTypingTrueSentAtMillis = 0L
        typingSendJob?.cancel()
        typingSendJob = null

        runCatching {
            repository.setTyping(
                conversationId = conversationId,
                isTyping = false,
                expiresAt = Instant.now()
            )
        }
    }

    private suspend fun refreshTypingUsers(
        conversationId: String
    ) {
        runCatching {
            repository.getTypingUsers(conversationId)
        }.onSuccess { users ->
            val currentUserId = _uiState.value.currentUserId
            val now = System.currentTimeMillis()

            _uiState.update {
                it.copy(
                    typingUsers = users
                        .filter { user ->
                            user.profileId != currentUserId &&
                                user.expiresAtMillis > now
                        }
                        .distinctBy { user -> user.profileId }
                )
            }
        }
    }

    private fun expireTypingUsers() {
        val now = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                typingUsers = it.typingUsers.filter { user ->
                    user.expiresAtMillis > now
                }
            )
        }
    }

    private suspend fun ensureMessageLoaded(
        conversationId: String,
        messageId: String
    ): Boolean {
        if (_uiState.value.messages.any { message -> message.id == messageId }) {
            return true
        }

        var page = oldestLoadedPage + 1

        while (page <= MAX_SEARCH_LOOKUP_PAGE) {
            val olderMessages = runCatching {
                repository.getMessages(
                    conversationId = conversationId,
                    page = page,
                    pageSize = messagePageSize
                )
            }.getOrElse {
                return false
            }

            oldestLoadedPage = page

            _uiState.update { currentState ->
                currentState.copy(
                    messages = (
                            olderMessages + currentState.messages
                            ).distinctBy { message -> message.id },
                    hasMoreMessages =
                        olderMessages.size == messagePageSize,
                    errorMessage = null
                )
            }

            if (olderMessages.any { message -> message.id == messageId }) {
                return true
            }

            if (olderMessages.size < messagePageSize) {
                return false
            }

            page += 1
        }

        return false
    }

    private companion object {
        const val TYPING_TRUE_THROTTLE_MS = 1500L
        const val TYPING_IDLE_TIMEOUT_MS = 2500L
        const val TYPING_REMOTE_EXPIRY_MS = 7000L
        const val TYPING_EXPIRY_CHECK_MS = 1000L
        const val SEARCH_DEBOUNCE_MS = 350L
        const val SEARCH_HIGHLIGHT_MS = 3500L
        const val SEARCH_RESULT_LIMIT = 25
        const val MAX_SEARCH_QUERY_LENGTH = 80
        const val MAX_SEARCH_LOOKUP_PAGE = 20
    }

}
