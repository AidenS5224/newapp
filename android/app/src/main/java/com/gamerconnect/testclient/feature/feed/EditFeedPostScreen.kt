package com.gamerconnect.testclient.feature.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamerconnect.testclient.data.feed.FeedPost
import com.gamerconnect.testclient.data.feed.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditFeedPostUiState(
    val post: FeedPost? = null,
    val draftBody: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
    val savedPost: FeedPost? = null
)

class EditFeedPostViewModel(
    private val repository: FeedRepository = FeedRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditFeedPostUiState())
    val uiState: StateFlow<EditFeedPostUiState> = _uiState.asStateFlow()

    fun load(
        postId: String
    ) {
        val currentState = _uiState.value
        if (currentState.post?.id == postId && !currentState.isLoading) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadError = null,
                    saveError = null
                )
            }

            runCatching {
                repository.getOwnedFeedPost(postId)
            }.onSuccess { post ->
                _uiState.update {
                    it.copy(
                        post = post,
                        draftBody = post.body,
                        isLoading = false,
                        loadError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = friendlyEditError(
                            error = error,
                            fallback = "Couldn't load this post. Try again."
                        )
                    )
                }
            }
        }
    }

    fun updateDraftBody(
        body: String
    ) {
        _uiState.update {
            it.copy(
                draftBody = body,
                saveError = null
            )
        }
    }

    fun save() {
        val currentState = _uiState.value
        val post = currentState.post ?: return

        if (currentState.isSaving) {
            return
        }

        val trimmedBody = currentState.draftBody.trim()
        if (trimmedBody.isBlank() && post.mediaUrl.isNullOrBlank()) {
            _uiState.update {
                it.copy(saveError = "Add some text before saving.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveError = null
                )
            }

            repository.updateFeedPost(
                postId = post.id,
                body = trimmedBody
            ).onSuccess { updatedPost ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        savedPost = updatedPost
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = friendlyEditError(
                            error = error,
                            fallback = "Couldn't save changes. Try again."
                        )
                    )
                }
            }
        }
    }

    private fun friendlyEditError(
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

@Composable
fun EditFeedPostScreen(
    postId: String,
    onBack: () -> Unit,
    onSaved: (FeedPost) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditFeedPostViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(postId) {
        viewModel.load(postId)
    }

    LaunchedEffect(uiState.savedPost) {
        uiState.savedPost?.let(onSaved)
    }

    BackHandler(enabled = uiState.isSaving) {
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                enabled = !uiState.isSaving
            ) {
                Text(
                    text = "Cancel",
                    color = Color(0xFFB8BFCC)
                )
            }

            Text(
                text = "Edit post",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = viewModel::save,
                enabled = !uiState.isLoading &&
                    !uiState.isSaving &&
                    uiState.loadError == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.semantics {
                    contentDescription = "Save changes"
                }
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        color = Color.White
                    )
                } else {
                    Text("Save")
                }
            }
        }

        when {
            uiState.isLoading -> {
                Text(
                    text = "Loading post...",
                    color = Color.White
                )
            }

            uiState.loadError != null -> {
                FriendlyFeedManagementCard(
                    title = "Post unavailable",
                    message = uiState.loadError.orEmpty()
                )
            }

            else -> {
                val post = uiState.post

                if (post != null) {
                    Text(
                        text = post.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = uiState.draftBody,
                        onValueChange = viewModel::updateDraftBody,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Post text"
                            },
                        minLines = 5,
                        enabled = !uiState.isSaving,
                        placeholder = {
                            Text("Share a highlight, update, or moment...")
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedPlaceholderColor = Color(0xFF9CA3AF),
                            unfocusedPlaceholderColor = Color(0xFF9CA3AF)
                        )
                    )

                    if (!post.mediaUrl.isNullOrBlank()) {
                        FriendlyFeedManagementCard(
                            title = "Media attached",
                            message = "Your existing media will stay attached to this post."
                        )
                    }

                    uiState.saveError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendlyFeedManagementCard(
    title: String,
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF111A2B)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                color = Color(0xFFB8BFCC)
            )
        }
    }
}
