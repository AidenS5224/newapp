package com.gamerconnect.testclient.feature.feed

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.gamerconnect.testclient.data.feed.FeedComment
import com.gamerconnect.testclient.data.feed.FeedPost

@Composable
fun FeedCommentsScreen(
    postId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedCommentsViewModel = viewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(postId) {
        viewModel.load(postId)
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT
            ).show()
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(uiState.comments.size) {
        if (uiState.comments.isNotEmpty()) {
            listState.animateScrollToItem(uiState.comments.lastIndex + 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack
            ) {
                Text("Back")
            }

            Text(
                text = "Comments",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Loading comments"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.loadError != null -> {
                CommentStatusCard(
                    message = uiState.loadError,
                    actionLabel = "Retry",
                    onAction = {
                        viewModel.load(postId)
                    }
                )
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.post?.let { post ->
                        item(
                            key = "post-${post.id}"
                        ) {
                            CommentThreadPostCard(post = post)
                        }
                    }

                    if (uiState.comments.isEmpty()) {
                        item(
                            key = "empty-comments"
                        ) {
                            CommentStatusCard(
                                message = "No comments yet. Start the conversation."
                            )
                        }
                    } else {
                        items(
                            items = uiState.comments,
                            key = { comment -> comment.id }
                        ) { comment ->
                            CommentRow(comment = comment)
                        }
                    }
                }

                CommentComposer(
                    draft = uiState.draft,
                    isSubmitting = uiState.isSubmitting,
                    onDraftChange = viewModel::updateDraft,
                    onSend = viewModel::sendComment
                )
            }
        }
    }
}

@Composable
private fun CommentThreadPostCard(
    post: FeedPost
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1220)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CommentAvatar(
                    displayName = post.authorDisplayName ?: "Unknown player",
                    avatarUrl = post.authorAvatarUrl,
                    sizeDp = 40
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = post.authorDisplayName ?: "Unknown player",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = post.createdAt,
                        color = Color(0xFF8D94A3),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = post.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )

            if (post.body.isNotBlank()) {
                Text(
                    text = post.body,
                    color = Color.White,
                    fontSize = 15.sp
                )
            }

            if (!post.resolvedMediaUrl.isNullOrBlank()) {
                AsyncImage(
                    model = post.resolvedMediaUrl,
                    contentDescription = "Feed post image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF111A2B)),
                    contentScale = ContentScale.Crop
                )
            }

            Text(
                text = "${post.reactionCount.coerceAtLeast(0)} likes • ${post.commentCount.coerceAtLeast(0)} comments",
                color = Color(0xFFB8BFCC),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun CommentRow(
    comment: FeedComment
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CommentAvatar(
            displayName = comment.authorDisplayName ?: "Unknown player",
            avatarUrl = comment.authorAvatarUrl,
            sizeDp = 36
        )

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF111A2B)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = comment.authorDisplayName ?: "Unknown player",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = comment.createdAt,
                        color = Color(0xFF8D94A3),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = comment.body,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun CommentAvatar(
    displayName: String,
    avatarUrl: String?,
    sizeDp: Int
) {
    var imageFailed by remember(avatarUrl) {
        mutableStateOf(false)
    }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color(0xFF25104B)),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank() && !imageFailed) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "$displayName profile picture",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = {
                    imageFailed = true
                }
            )
        } else {
            Text(
                text = displayName
                    .trim()
                    .firstOrNull()
                    ?.uppercase()
                    ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CommentComposer(
    draft: String,
    isSubmitting: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = "Add a comment"
                },
            enabled = !isSubmitting,
            placeholder = {
                Text("Add a comment")
            },
            minLines = 1,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFF374151),
                focusedPlaceholderColor = Color(0xFF9CA3AF),
                unfocusedPlaceholderColor = Color(0xFF9CA3AF)
            )
        )

        Spacer(modifier = Modifier.width(10.dp))

        Button(
            onClick = onSend,
            enabled = !isSubmitting && draft.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.semantics {
                contentDescription = "Post comment"
            }
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text("Send")
            }
        }
    }
}

@Composable
private fun CommentStatusCard(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1220)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                color = Color(0xFFB8BFCC)
            )

            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
