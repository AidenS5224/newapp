package com.gamerconnect.testclient.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamerconnect.testclient.data.feed.FeedPost
import com.gamerconnect.testclient.data.feed.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val posts: List<FeedPost> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class FeedViewModel(
    private val repository: FeedRepository = FeedRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        loadPosts()
    }

    fun loadPosts() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            runCatching {
                repository.getFeedPosts()
            }.onSuccess { posts ->
                _uiState.update {
                    it.copy(
                        posts = posts,
                        isLoading = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message
                            ?: "Unable to load feed."
                    )
                }
            }
        }
    }
}