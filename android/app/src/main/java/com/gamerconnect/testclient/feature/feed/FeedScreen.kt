package com.gamerconnect.testclient.feature.feed

import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView


@Composable
fun FeedScreen(
    onCreatePostClick: () -> Unit = {},
    onCommentsClick: (String) -> Unit = {},
    onEditPostClick: (String) -> Unit = {},
    refreshKey: Int = 0,
    modifier: Modifier = Modifier,
    feedViewModel: FeedViewModel = viewModel()
) {
    val uiState = feedViewModel.uiState.collectAsStateWithLifecycle().value
    val currentTabState = uiState.currentTabState
    val context = LocalContext.current
    val discoverListState = rememberLazyListState()
    val friendsListState = rememberLazyListState()
    var playingPostId by remember {
        mutableStateOf<String?>(null)
    }
    val listState = when (uiState.selectedTab) {
        FeedTab.DISCOVER -> discoverListState
        FeedTab.FRIENDS -> friendsListState
    }

    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            feedViewModel.loadPosts()
        }
    }

    LaunchedEffect(uiState.reactionErrorMessage) {
        uiState.reactionErrorMessage?.let { message ->
            Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT
            ).show()
            feedViewModel.consumeReactionError()
        }
    }

    LaunchedEffect(uiState.managementMessage) {
        uiState.managementMessage?.let { message ->
            Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT
            ).show()
            feedViewModel.consumeManagementMessage()
        }
    }

    LaunchedEffect(uiState.deletedPostId) {
        uiState.deletedPostId?.let { deletedPostId ->
            if (playingPostId == deletedPostId) {
                playingPostId = null
            }
            feedViewModel.consumeDeletedPostEvent()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Feed",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeedTab.entries.forEach { tab ->
                    FeedTabButton(
                        title = tab.label,
                        selected = uiState.selectedTab == tab,
                        onClick = {
                            feedViewModel.selectTab(tab)
                        }
                    )
                }
            }

            when {
                currentTabState.isLoading -> {
                    Text(
                        text = "Loading feed...",
                        color = Color.White
                    )
                }

                currentTabState.errorMessage != null -> {
                    Text(
                        text = currentTabState.errorMessage,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                currentTabState.posts.isEmpty() -> {
                    Text(
                        text = uiState.selectedTab.emptyMessage,
                        color = Color(0xFF9CA3AF)
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            items = currentTabState.posts,
                            key = { post -> post.id }
                        ) { post ->
                            FeedPostCard(
                                title = post.title,
                                body = post.body,
                                authorDisplayName = post.authorDisplayName
                                    ?: "Unknown player",
                                authorAvatarUrl = post.authorAvatarUrl,
                                mediaUrl = post.resolvedMediaUrl,
                                mediaType = post.mediaType,
                                isVideoPlaying = playingPostId == post.id,
                                onPlayVideo = {
                                    playingPostId = post.id
                                },
                                createdAt = post.createdAt,
                                reactionCount = post.reactionCount,
                                isReactedByCurrentUser = post.isReactedByCurrentUser,
                                isReactionPending = post.isReactionPending,
                                onReactionClick = {
                                    feedViewModel.toggleReaction(post.id)
                                },
                                commentCount = post.commentCount,
                                onCommentsClick = {
                                    onCommentsClick(post.id)
                                },
                                isOwnPost = uiState.currentProfileId == post.profileId,
                                onEditPost = {
                                    onEditPostClick(post.id)
                                },
                                onDeletePost = {
                                    feedViewModel.requestDeletePost(post)
                                }
                            )
                        }
                    }
                }
            }
        }

        uiState.postPendingDeletion?.let { post ->
            DeletePostConfirmationDialog(
                isDeleting = uiState.isDeletingPost,
                onConfirm = feedViewModel::confirmDeletePost,
                onDismiss = feedViewModel::cancelDeletePost
            )
        }

        FloatingActionButton(
            onClick = onCreatePostClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .semantics {
                    contentDescription = "Create post"
                },
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = "+",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DeletePostConfirmationDialog(
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isDeleting) {
                onDismiss()
            }
        },
        title = {
            Text("Delete post?")
        },
        text = {
            Text("This post and its comments will be permanently deleted.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isDeleting,
                modifier = Modifier.semantics {
                    contentDescription = "Confirm deletion"
                }
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text("Delete")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting,
                modifier = Modifier.semantics {
                    contentDescription = "Cancel deletion"
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FeedTabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.semantics {
            contentDescription = "$title tab"
        },
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            }
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = title,
            color = if (selected) Color.White else Color(0xFFB8BFCC),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun FeedPostCard(
    title: String,
    body: String,
    authorDisplayName: String,
    authorAvatarUrl: String?,
    mediaUrl: String?,
    mediaType: String?,
    isVideoPlaying: Boolean,
    onPlayVideo: () -> Unit,
    createdAt: String,
    reactionCount: Int,
    isReactedByCurrentUser: Boolean,
    isReactionPending: Boolean,
    onReactionClick: () -> Unit,
    commentCount: Int,
    onCommentsClick: () -> Unit,
    isOwnPost: Boolean,
    onEditPost: () -> Unit,
    onDeletePost: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1220)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FeedAuthorHeader(
                displayName = authorDisplayName,
                avatarUrl = authorAvatarUrl,
                timestamp = createdAt,
                isOwnPost = isOwnPost,
                onEditPost = onEditPost,
                onDeletePost = onDeletePost
            )

            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = body,
                color = Color.White,
                fontSize = 16.sp
            )

            if (!mediaUrl.isNullOrBlank()) {
                FeedMedia(
                    mediaUrl = mediaUrl,
                    mediaType = mediaType,
                    authorName = authorDisplayName,
                    isVideoPlaying = isVideoPlaying,
                    onPlayVideo = onPlayVideo,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeedReactionAction(
                    reactionCount = reactionCount,
                    isReactedByCurrentUser = isReactedByCurrentUser,
                    isReactionPending = isReactionPending,
                    onClick = onReactionClick
                )

                TextButton(
                    onClick = onCommentsClick,
                    modifier = Modifier.semantics {
                        contentDescription = "Open comments, ${commentCount.coerceAtLeast(0)} comments"
                    }
                ) {
                    Text(
                        text = "Comments ${commentCount.coerceAtLeast(0)}",
                        color = Color(0xFFB8BFCC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "Share",
                    color = Color(0xFFB8BFCC)
                )
            }
        }
    }
}

@Composable
private fun FeedReactionAction(
    reactionCount: Int,
    isReactedByCurrentUser: Boolean,
    isReactionPending: Boolean,
    onClick: () -> Unit
) {
    val safeCount = reactionCount.coerceAtLeast(0)
    val actionLabel = if (isReactedByCurrentUser) {
        "Unlike"
    } else {
        "Like"
    }

    TextButton(
        onClick = onClick,
        enabled = !isReactionPending,
        modifier = Modifier.semantics {
            contentDescription = "$actionLabel post, $safeCount likes"
        }
    ) {
        Text(
            text = if (isReactedByCurrentUser) {
                "✓ $actionLabel $safeCount"
            } else {
                "+ $actionLabel $safeCount"
            },
            color = if (isReactedByCurrentUser) {
                MaterialTheme.colorScheme.primary
            } else {
                Color(0xFFB8BFCC)
            },
            fontWeight = if (isReactedByCurrentUser) {
                FontWeight.Bold
            } else {
                FontWeight.Medium
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FeedAuthorHeader(
    displayName: String,
    avatarUrl: String?,
    timestamp: String,
    isOwnPost: Boolean,
    onEditPost: () -> Unit,
    onDeletePost: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FeedAuthorAvatar(
            displayName = displayName,
            avatarUrl = avatarUrl
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = displayName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = timestamp,
                color = Color(0xFF8D94A3),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isOwnPost) {
            PostOptionsMenu(
                onEditPost = onEditPost,
                onDeletePost = onDeletePost
            )
        }
    }
}

@Composable
private fun PostOptionsMenu(
    onEditPost: () -> Unit,
    onDeletePost: () -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    Box {
        IconButton(
            onClick = {
                expanded = true
            },
            modifier = Modifier.semantics {
                contentDescription = "Post options"
            }
        ) {
            Text(
                text = "...",
                color = Color(0xFFB8BFCC),
                fontWeight = FontWeight.Bold
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {
            DropdownMenuItem(
                text = {
                    Text("Edit post")
                },
                onClick = {
                    expanded = false
                    onEditPost()
                },
                modifier = Modifier.semantics {
                    contentDescription = "Edit post"
                }
            )

            DropdownMenuItem(
                text = {
                    Text("Delete post")
                },
                onClick = {
                    expanded = false
                    onDeletePost()
                },
                modifier = Modifier.semantics {
                    contentDescription = "Delete post"
                }
            )
        }
    }
}

@Composable
private fun FeedAuthorAvatar(
    displayName: String,
    avatarUrl: String?
) {
    var imageFailed by remember(avatarUrl) {
        mutableStateOf(false)
    }

    Box(
        modifier = Modifier
            .size(40.dp)
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
private fun FeedMedia(
    mediaUrl: String,
    mediaType: String?,
    authorName: String?,
    isVideoPlaying: Boolean,
    onPlayVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (feedMediaKind(mediaType, mediaUrl)) {
        FeedMediaKind.Image -> FeedMediaPreview(
            mediaUrl = mediaUrl,
            authorName = authorName,
            modifier = modifier
        )

        FeedMediaKind.Video -> FeedVideoPlayer(
            mediaUrl = mediaUrl,
            authorName = authorName,
            isPlaying = isVideoPlaying,
            onPlay = onPlayVideo,
            modifier = modifier
        )

        FeedMediaKind.Unknown -> FeedMediaFallback(
            text = "Media unavailable",
            modifier = modifier
        )
    }
}

@Composable
private fun FeedVideoPlayer(
    mediaUrl: String,
    authorName: String?,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var player by remember(mediaUrl) {
        mutableStateOf<ExoPlayer?>(null)
    }
    var isMuted by remember(mediaUrl) {
        mutableStateOf(true)
    }
    val maxMediaHeight = LocalConfiguration.current.screenHeightDp.dp * 0.48f

    LaunchedEffect(isPlaying, mediaUrl) {
        if (isPlaying) {
            if (player == null) {
                player = ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(mediaUrl))
                    volume = 0f
                    playWhenReady = true
                    prepare()
                }
                isMuted = true
            } else {
                player?.playWhenReady = true
                player?.play()
            }
        } else {
            player?.release()
            player = null
        }
    }

    LaunchedEffect(isMuted, player) {
        player?.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(mediaUrl) {
        onDispose {
            player?.release()
            player = null
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val previewHeight = (maxWidth / (16f / 9f))
            .coerceAtMost(maxMediaHeight)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF111A2B)),
            contentAlignment = Alignment.Center
        ) {
            val activePlayer = player

            if (activePlayer != null) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = true
                            this.player = activePlayer
                        }
                    },
                    update = { playerView ->
                        playerView.player = activePlayer
                    },
                    modifier = Modifier.fillMaxSize()
                )

                TextButton(
                    onClick = {
                        isMuted = !isMuted
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .semantics {
                            contentDescription = if (isMuted) {
                                "Enable video sound"
                            } else {
                                "Mute video sound"
                            }
                        },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = Color(0xCC070B14)
                    )
                ) {
                    Text(
                        text = if (isMuted) "Muted" else "Sound on",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Video attached",
                        color = Color(0xFFB8BFCC),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    TextButton(
                        onClick = onPlay,
                        modifier = Modifier.semantics {
                            contentDescription = authorName?.let {
                                "Play $it feed post video"
                            } ?: "Play feed post video"
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Play",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun FeedMediaPreview(
    mediaUrl: String,
    authorName: String?,
    modifier: Modifier = Modifier
) {
    var imageFailed by remember(mediaUrl) {
        mutableStateOf(false)
    }
    var imageAspectRatio by remember(mediaUrl) {
        mutableStateOf(16f / 9f)
    }
    val maxMediaHeight = LocalConfiguration.current.screenHeightDp.dp * 0.48f

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val previewHeight = (maxWidth / imageAspectRatio)
            .coerceAtMost(maxMediaHeight)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF111A2B)),
            contentAlignment = Alignment.Center
        ) {
            if (imageFailed) {
                Text(
                    text = "Media unavailable",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                AsyncImage(
                    model = mediaUrl,
                    contentDescription = authorName?.let { "$it feed post image" }
                        ?: "Feed post image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillWidth,
                    onSuccess = { state ->
                        val intrinsicSize = state.painter.intrinsicSize
                        if (
                            intrinsicSize.width.isFinite() &&
                            intrinsicSize.height.isFinite() &&
                            intrinsicSize.width > 0f &&
                            intrinsicSize.height > 0f
                        ) {
                            imageAspectRatio =
                                intrinsicSize.width / intrinsicSize.height
                        }
                    },
                    onError = { state ->
                        imageFailed = true
                        Log.w(
                            "FeedScreen",
                            "Feed image failed to load.",
                            state.result.throwable
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun FeedMediaFallback(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF111A2B)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFF9CA3AF),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )
    }
}

private fun feedMediaKind(
    mediaType: String?,
    mediaUrl: String
): FeedMediaKind {
    val normalizedMediaType = mediaType
        ?.trim()
        ?.lowercase()

    if (normalizedMediaType == "image" || normalizedMediaType?.startsWith("image/") == true) {
        return FeedMediaKind.Image
    }

    if (normalizedMediaType == "video" || normalizedMediaType?.startsWith("video/") == true) {
        return FeedMediaKind.Video
    }

    val path = mediaUrl
        .substringBefore("?")
        .substringBefore("#")
        .lowercase()

    return when {
        path.endsWith(".jpg") ||
            path.endsWith(".jpeg") ||
            path.endsWith(".png") ||
            path.endsWith(".webp") ||
            path.endsWith(".gif") -> FeedMediaKind.Image

        path.endsWith(".mp4") ||
            path.endsWith(".webm") ||
            path.endsWith(".mov") -> FeedMediaKind.Video

        else -> FeedMediaKind.Unknown
    }
}

private enum class FeedMediaKind {
    Image,
    Video,
    Unknown
}

