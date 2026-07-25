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


data class MessagesUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class MessagesViewModel(
    private val repository: MessagesRepository = MessagesRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    init {
        loadConversations()
    }

    fun loadConversations() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            runCatching {
                repository.getMyConversations()
            }.onSuccess { conversations ->
                _uiState.update {
                    it.copy(
                        conversations = conversations,
                        isLoading = false
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