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
    val errorMessage: String? = null,
    val isSending: Boolean = false,
    val scrollToBottomSignal: Int = 0
)

class ChatViewModel(
    private val repository: MessagesRepository = MessagesRepository()
) : ViewModel() {

    private var realtimeJob: kotlinx.coroutines.Job? = null

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

    fun loadMessages(
        conversationId: String,
        showLoading: Boolean = true
    ) {
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
                repository.getMessages(conversationId)
            }.onSuccess { messages ->
                repository.markConversationAsRead(conversationId)

                _uiState.update {
                    it.copy(
                        messages = messages,
                        isLoading = false,
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
