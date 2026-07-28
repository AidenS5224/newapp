package com.gamerconnect.testclient.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamerconnect.testclient.data.feed.FeedComment
import com.gamerconnect.testclient.data.feed.FeedPost
import com.gamerconnect.testclient.data.feed.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedCommentsUiState(
    val post: FeedPost? = null,
    val comments: List<FeedComment> = emptyList(),
    val draft: String = "",
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val loadError: String? = null,
    val message: String? = null
)

class FeedCommentsViewModel(
    private val repository: FeedRepository = FeedRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedCommentsUiState())
    val uiState: StateFlow<FeedCommentsUiState> = _uiState.asStateFlow()

    fun load(
        postId: String
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadError = null,
                    message = null
                )
            }

            runCatching {
                repository.getFeedPost(postId) to repository.getComments(postId)
            }.onSuccess { (post, comments) ->
                _uiState.update {
                    it.copy(
                        post = post,
                        comments = comments,
                        isLoading = false
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = "Couldn't load comments. Try again."
                    )
                }
            }
        }
    }

    fun updateDraft(
        draft: String
    ) {
        _uiState.update {
            it.copy(
                draft = draft,
                message = null
            )
        }
    }

    fun sendComment() {
        val currentState = _uiState.value
        val postId = currentState.post?.id ?: return
        val trimmedDraft = currentState.draft.trim()

        if (currentState.isSubmitting) {
            return
        }

        if (trimmedDraft.isBlank()) {
            _uiState.update {
                it.copy(message = "Add a comment before sending.")
            }
            return
        }

        if (trimmedDraft.length > MAX_COMMENT_LENGTH) {
            _uiState.update {
                it.copy(message = "Comment is too long.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    message = null
                )
            }

            runCatching {
                repository.createComment(
                    postId = postId,
                    body = trimmedDraft
                )
            }.onSuccess { comment ->
                _uiState.update { state ->
                    state.copy(
                        comments = state.comments + comment,
                        post = state.post?.copy(
                            commentCount = state.post.commentCount + 1
                        ),
                        draft = "",
                        isSubmitting = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        message = friendlyCommentError(error)
                    )
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update {
            it.copy(message = null)
        }
    }

    private fun friendlyCommentError(
        error: Throwable
    ): String {
        val message = error.message.orEmpty().lowercase()

        return when {
            "sign in" in message -> "Sign in to comment."
            "too long" in message -> "Comment is too long."
            "add a comment" in message -> "Add a comment before sending."
            else -> "Couldn't post comment. Try again."
        }
    }

    private companion object {
        const val MAX_COMMENT_LENGTH = 1000
    }
}
