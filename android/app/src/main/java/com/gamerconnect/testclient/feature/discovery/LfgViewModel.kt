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
import com.gamerconnect.testclient.data.lfg.LfgJoinRequest

data class LfgUiState(
    val posts: List<LfgPost> = emptyList(),
    val pendingOwnerRequests: List<LfgJoinRequest> = emptyList(),
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val requestingPostId: String? = null,
    val requestedPostIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val creationMessage: String? = null,
    val joinRequestMessage: String? = null
)

class LfgViewModel(
    private val repository: LfgRepository = LfgRepository()
) : ViewModel() {

    fun acceptJoinRequest(
        requestId: String
    ) {
        viewModelScope.launch {
            runCatching {
                repository.acceptJoinRequest(requestId)
            }.onSuccess {
                loadPendingOwnerRequests()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        errorMessage = error.message
                            ?: "Unable to accept join request."
                    )
                }
            }
        }
    }

    fun rejectJoinRequest(
        requestId: String
    ) {
        viewModelScope.launch {
            runCatching {
                repository.rejectJoinRequest(requestId)
            }.onSuccess {
                loadPendingOwnerRequests()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        errorMessage = error.message
                            ?: "Unable to reject join request."
                    )
                }
            }
        }
    }

    fun loadPendingOwnerRequests() {
        viewModelScope.launch {
            runCatching {
                repository.getPendingRequestsForOwnedPosts()
            }.onSuccess { requests ->
                _uiState.update {
                    it.copy(
                        pendingOwnerRequests = requests
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        errorMessage = error.message
                            ?: "Unable to load pending requests."
                    )
                }
            }
        }
    }

    fun requestToJoin(
        lfgPostId: String
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    requestingPostId = lfgPostId,
                    errorMessage = null,
                    joinRequestMessage = null
                )
            }

            runCatching {
                repository.requestToJoin(lfgPostId)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        requestingPostId = null,
                        requestedPostIds = it.requestedPostIds + lfgPostId,
                        joinRequestMessage = "Join request sent."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        requestingPostId = null,
                        errorMessage = error.message
                            ?: "Unable to send join request."
                    )
                }
            }
        }
    }

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
        loadPendingOwnerRequests()
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