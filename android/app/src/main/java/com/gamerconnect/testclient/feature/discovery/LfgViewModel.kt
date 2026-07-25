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
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
    val creationMessage: String? = null
)

class LfgViewModel(
    private val repository: LfgRepository = LfgRepository()
) : ViewModel() {

    fun createPost(
        title: String,
        mode: String,
        rankRange: String,
        partySize: String,
        startsAt: String
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCreating = true,
                    errorMessage = null,
                    creationMessage = null
                )
            }

            runCatching {
                repository.createLfgPost(
                    title = title,
                    mode = mode,
                    rankRange = rankRange,
                    partySize = partySize,
                    startsAt = startsAt
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isCreating = false,
                        creationMessage = "LFG post created."
                    )
                }

                loadPosts()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isCreating = false,
                        errorMessage = error.message
                            ?: "Unable to create LFG post."
                    )
                }
            }
        }
    }

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