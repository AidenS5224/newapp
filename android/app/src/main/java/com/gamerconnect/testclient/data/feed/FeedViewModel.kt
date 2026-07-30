package com.gamerconnect.testclient.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamerconnect.testclient.data.feed.FeedFilter
import com.gamerconnect.testclient.data.feed.FeedGame
import com.gamerconnect.testclient.data.feed.FeedImageUpload
import com.gamerconnect.testclient.data.feed.FeedPost
import com.gamerconnect.testclient.data.feed.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FeedTab(
    val label: String,
    val emptyMessage: String,
    val filter: FeedFilter
) {
    DISCOVER(
        label = "Discover",
        emptyMessage = "No posts yet.",
        filter = FeedFilter.DISCOVER
    ),
    FRIENDS(
        label = "Friends",
        emptyMessage = "No posts from friends yet.",
        filter = FeedFilter.FRIENDS
    )
}

data class FeedTabState(
    val posts: List<FeedPost> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasLoaded: Boolean = false
)

data class FeedUiState(
    val selectedTab: FeedTab = FeedTab.DISCOVER,
    val tabStates: Map<FeedTab, FeedTabState> = FeedTab.entries.associateWith {
        FeedTabState()
    },
    val currentProfileId: String? = null,
    val postPendingDeletion: FeedPost? = null,
    val isDeletingPost: Boolean = false,
    val reactionErrorMessage: String? = null,
    val managementMessage: String? = null,
    val deletedPostId: String? = null
) {
    val currentTabState: FeedTabState
        get() = tabStates[selectedTab] ?: FeedTabState()
}

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
        refreshCurrentProfileId()
        loadPosts(FeedTab.DISCOVER)
        loadAvailableGames()
    }

    fun refreshCurrentProfileId() {
        _uiState.update {
            it.copy(currentProfileId = repository.getCurrentProfileId())
        }
    }

    fun selectTab(
        tab: FeedTab
    ) {
        _uiState.update {
            it.copy(selectedTab = tab)
        }

        if (!_uiState.value.currentTabState.hasLoaded) {
            loadPosts(tab)
        }
    }

    fun loadPosts(
        tab: FeedTab = _uiState.value.selectedTab
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.updateTab(
                    tab = tab,
                    tabState = (it.tabStates[tab] ?: FeedTabState()).copy(
                        isLoading = true,
                        errorMessage = null
                    )
                )
            }

            runCatching {
                repository.getFeedPosts(tab.filter)
            }.onSuccess { posts ->
                _uiState.update {
                    it.updateTab(
                        tab = tab,
                        tabState = FeedTabState(
                            posts = posts,
                            isLoading = false,
                            hasLoaded = true
                        )
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.updateTab(
                        tab = tab,
                        tabState = (it.tabStates[tab] ?: FeedTabState()).copy(
                            isLoading = false,
                            errorMessage = friendlyFeedLoadError(tab, error),
                            hasLoaded = true
                        )
                    )
                }
            }
        }
    }

    fun toggleReaction(
        postId: String
    ) {
        val currentPosts = _uiState.value.currentTabState.posts
        val targetPost = currentPosts.firstOrNull { post ->
            post.id == postId
        } ?: return

        if (targetPost.isReactionPending) {
            return
        }

        val nextReactedState = !targetPost.isReactedByCurrentUser
        val optimisticPost = targetPost.copy(
            isReactedByCurrentUser = nextReactedState,
            reactionCount = if (nextReactedState) {
                targetPost.reactionCount + 1
            } else {
                (targetPost.reactionCount - 1).coerceAtLeast(0)
            },
            isReactionPending = true
        )

        _uiState.update {
            it.copy(
                tabStates = it.updatePostInAllTabs(
                    postId = postId,
                    replacement = optimisticPost
                ),
                reactionErrorMessage = null
            )
        }

        viewModelScope.launch {
            runCatching {
                if (nextReactedState) {
                    repository.addReaction(postId)
                } else {
                    repository.removeReaction(postId)
                }
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        tabStates = state.updatePostInAllTabs(
                            postId = postId,
                            transform = { post ->
                                post.copy(isReactionPending = false)
                            }
                        )
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        tabStates = state.updatePostInAllTabs(
                            postId = postId,
                            replacement = targetPost.copy(isReactionPending = false)
                        ),
                        reactionErrorMessage = friendlyReactionError(error)
                    )
                }
            }
        }
    }

    fun consumeReactionError() {
        _uiState.update {
            it.copy(reactionErrorMessage = null)
        }
    }

    fun requestDeletePost(
        post: FeedPost
    ) {
        _uiState.update {
            it.copy(
                postPendingDeletion = post,
                managementMessage = null
            )
        }
    }

    fun cancelDeletePost() {
        if (_uiState.value.isDeletingPost) {
            return
        }

        _uiState.update {
            it.copy(postPendingDeletion = null)
        }
    }

    fun confirmDeletePost() {
        val post = _uiState.value.postPendingDeletion ?: return
        if (_uiState.value.isDeletingPost) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDeletingPost = true,
                    managementMessage = null
                )
            }

            repository.deleteFeedPost(post.id)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            tabStates = state.removePostFromAllTabs(post.id),
                            postPendingDeletion = null,
                            isDeletingPost = false,
                            managementMessage = "Post deleted.",
                            deletedPostId = post.id
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isDeletingPost = false,
                            managementMessage = friendlyManagementError(
                                error = error,
                                fallback = "Couldn't delete post. Try again."
                            )
                        )
                    }
                }
        }
    }

    fun applyUpdatedPost(
        post: FeedPost
    ) {
        _uiState.update {
            it.copy(
                tabStates = it.updatePostInAllTabs(
                    postId = post.id,
                    replacement = post
                ),
                managementMessage = "Post updated."
            )
        }
    }

    fun consumeManagementMessage() {
        _uiState.update {
            it.copy(managementMessage = null)
        }
    }

    fun consumeDeletedPostEvent() {
        _uiState.update {
            it.copy(deletedPostId = null)
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

    private fun friendlyReactionError(
        error: Throwable
    ): String {
        val message = error.message.orEmpty().lowercase()

        return when {
            "sign in" in message -> "Sign in to react to posts."
            else -> "Couldn't update reaction. Try again."
        }
    }

    private fun friendlyFeedLoadError(
        tab: FeedTab,
        error: Throwable
    ): String {
        val message = error.message.orEmpty().lowercase()

        if ("sign in" in message) {
            return when (tab) {
                FeedTab.DISCOVER -> "Sign in to view this feed."
                FeedTab.FRIENDS -> "Sign in to view posts from friends."
            }
        }

        return when (tab) {
            FeedTab.DISCOVER -> "Couldn't load this feed. Try again."
            FeedTab.FRIENDS -> "Couldn't load posts from friends."
        }
    }

    private fun friendlyManagementError(
        error: Throwable,
        fallback: String
    ): String {
        val message = error.message.orEmpty().lowercase()

        return when {
            "sign in" in message -> "Sign in to manage posts."
            "own" in message -> "You can only manage your own posts."
            "no longer" in message || "not found" in message -> "This post is no longer available."
            "add some text" in message -> "Add some text before saving."
            "network" in message -> "Network problem. Check your connection and try again."
            else -> fallback
        }
    }
}

private fun FeedUiState.updateTab(
    tab: FeedTab,
    tabState: FeedTabState
): FeedUiState {
    return copy(
        tabStates = tabStates + (tab to tabState)
    )
}

private fun FeedUiState.updatePostInAllTabs(
    postId: String,
    replacement: FeedPost
): Map<FeedTab, FeedTabState> {
    return updatePostInAllTabs(
        postId = postId,
        transform = {
            replacement
        }
    )
}

private fun FeedUiState.updatePostInAllTabs(
    postId: String,
    transform: (FeedPost) -> FeedPost
): Map<FeedTab, FeedTabState> {
    return tabStates.mapValues { (_, tabState) ->
        tabState.copy(
            posts = tabState.posts.map { post ->
                if (post.id == postId) {
                    transform(post)
                } else {
                    post
                }
            }
        )
    }
}

private fun FeedUiState.removePostFromAllTabs(
    postId: String
): Map<FeedTab, FeedTabState> {
    return tabStates.mapValues { (_, tabState) ->
        tabState.copy(
            posts = tabState.posts.filterNot { post ->
                post.id == postId
            }
        )
    }
}
