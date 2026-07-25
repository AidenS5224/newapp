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
)

class ChatViewModel(
    private val repository: MessagesRepository = MessagesRepository()
) : ViewModel() {

    fun sendMessage(
        conversationId: String,
        body: String
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
                        isSending = false
                    )
                }

                loadMessages(conversationId)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = error.message
                            ?: "Unable to send message."
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
        conversationId: String
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            runCatching {
                repository.getMessages(conversationId)
            }.onSuccess { messages ->
                _uiState.update {
                    it.copy(
                        messages = messages,
                        isLoading = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message
                            ?: "Unable to load messages."
                    )
                }
            }
        }
    }
}
