package com.gamerconnect.testclient.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamerconnect.testclient.data.feed.FeedGame
import com.gamerconnect.testclient.data.feed.FeedImageUpload
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

data class CreateFeedPostUiState(
    val title: String = "",
    val text: String = "",
    val availableGames: List<FeedGame> = emptyList(),
    val selectedGameId: String? = null,
    val isLoadingGames: Boolean = false,
    val selectedImageUri: String? = null,
    val isPublishing: Boolean = false,
    val errorMessage: String? = null,
    val publishedSuccessfully: Boolean = false
)

class FeedViewModel(
    private val repository: FeedRepository = FeedRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _createPostUiState = MutableStateFlow(CreateFeedPostUiState())
    val createPostUiState: StateFlow<CreateFeedPostUiState> =
        _createPostUiState.asStateFlow()

    init {
        loadPosts()
        loadAvailableGames()
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

    fun loadAvailableGames() {
        viewModelScope.launch {
            _createPostUiState.update {
                it.copy(isLoadingGames = true)
            }

            runCatching {
                repository.getCurrentProfileGames()
            }.onSuccess { games ->
                _createPostUiState.update {
                    it.copy(
                        availableGames = games,
                        isLoadingGames = false
                    )
                }
            }.onFailure {
                _createPostUiState.update {
                    it.copy(isLoadingGames = false)
                }
            }
        }
    }

    fun updateDraftTitle(
        title: String
    ) {
        _createPostUiState.update {
            it.copy(
                title = title,
                errorMessage = null
            )
        }
    }

    fun updateDraftText(
        text: String
    ) {
        _createPostUiState.update {
            it.copy(
                text = text,
                errorMessage = null
            )
        }
    }

    fun updateSelectedGame(
        gameId: String?
    ) {
        _createPostUiState.update {
            it.copy(
                selectedGameId = gameId,
                errorMessage = null
            )
        }
    }

    fun updateSelectedImage(
        uri: String?
    ) {
        _createPostUiState.update {
            it.copy(
                selectedImageUri = uri,
                errorMessage = null
            )
        }
    }

    fun removeSelectedImage() {
        updateSelectedImage(null)
    }

    fun showCreatePostError(
        message: String
    ) {
        _createPostUiState.update {
            it.copy(errorMessage = message)
        }
    }

    fun publishPost(
        image: FeedImageUpload?
    ) {
        val currentState = _createPostUiState.value

        if (currentState.isPublishing) {
            return
        }

        val trimmedTitle = currentState.title.trim()
        val trimmedText = currentState.text.trim()
        if (trimmedTitle.isBlank()) {
            _createPostUiState.update {
                it.copy(
                    errorMessage = "Add a title before posting."
                )
            }
            return
        }

        if (trimmedText.isBlank() && image == null) {
            _createPostUiState.update {
                it.copy(
                    errorMessage = "Add some text or an image before posting."
                )
            }
            return
        }

        viewModelScope.launch {
            _createPostUiState.update {
                it.copy(
                    isPublishing = true,
                    errorMessage = null
                )
            }

            runCatching {
                repository.createPost(
                    title = trimmedTitle,
                    text = trimmedText,
                    gameId = currentState.selectedGameId,
                    image = image
                )
            }.onSuccess {
                _createPostUiState.update {
                    CreateFeedPostUiState(
                        publishedSuccessfully = true
                    )
                }
            }.onFailure { error ->
                _createPostUiState.update {
                    it.copy(
                        isPublishing = false,
                        errorMessage = friendlyCreatePostError(error)
                    )
                }
            }
        }
    }

    fun consumePublishedSuccessfully() {
        _createPostUiState.update {
            it.copy(publishedSuccessfully = false)
        }
    }

    private fun friendlyCreatePostError(
        error: Throwable
    ): String {
        val message = error.message.orEmpty().lowercase()

        return when {
            "sign in" in message -> "Sign in before creating a post."
            "image must be smaller" in message -> "Image must be smaller than 10 MB."
            "add a title" in message -> "Add a title before posting."
            "add some text" in message -> "Add some text or an image before posting."
            "network" in message -> "Network problem. Check your connection and try again."
            else -> "Unable to publish your post. Please try again."
        }
    }
}
