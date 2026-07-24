package com.gamerconnect.testclient.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamerconnect.testclient.data.lfg.LfgPost
import com.gamerconnect.testclient.data.lfg.LfgRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LfgUiState(
    val posts: List<LfgPost> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class LfgViewModel(
    private val repository: LfgRepository = LfgRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LfgUiState())
    val uiState: StateFlow<LfgUiState> = _uiState.asStateFlow()

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
                repository.getOpenLfgPosts()
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
                            ?: "Unable to load LFG posts."
                    )
                }
            }
        }
    }
}