package com.gamerconnect.testclient.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamerconnect.testclient.data.messages.Conversation
import com.gamerconnect.testclient.data.messages.MessagesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

data class MessagesUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val searchQuery: String = ""
)

class MessagesViewModel(
    private val repository: MessagesRepository = MessagesRepository()
) : ViewModel() {

    fun updateSearchQuery(
        query: String
    ) {
        _uiState.update {
            it.copy(searchQuery = query)
        }
    }

    fun clearUnreadCount(
        conversationId: String
    ) {
        _uiState.update { state ->
            state.copy(
                conversations = state.conversations.map { conversation ->
                    if (conversation.id == conversationId) {
                        conversation.copy(unreadCount = 0)
                    } else {
                        conversation
                    }
                }
            )
        }
    }

    private var realtimeJob: kotlinx.coroutines.Job? = null

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    init {
        loadConversations()

        realtimeJob = viewModelScope.launch {
            repository.observeConversationMessageChanges()
                .collect {
                    loadConversations(showLoading = false)
                }
        }
    }

    fun loadConversations(
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
                repository.getMyConversations()
            }.onSuccess { conversations ->
                _uiState.update {
                    it.copy(
                        conversations = conversations,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message
                            ?: "Unable to load conversations."
                    )
                }
            }
        }
    }
}