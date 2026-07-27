package com.gamerconnect.testclient.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamerconnect.testclient.data.messages.ChatMessage
import com.gamerconnect.testclient.data.messages.MessagesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.auth.auth

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentUserId: String? = null,
    val isLoading: Boolean = true,
    val isLoadingOlder: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val errorMessage: String? = null,
    val isSending: Boolean = false,
    val scrollToBottomSignal: Int = 0
)

class ChatViewModel(
    private val repository: MessagesRepository = MessagesRepository()
) : ViewModel() {

    private var realtimeJob: kotlinx.coroutines.Job? = null
    private val messagePageSize = 50
    private var oldestLoadedPage = 0
    private var activeConversationId: String? = null

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

            runCatching {
                repository.sendMessage(
                    conversationId = conversationId,
                    body = body
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
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
}
