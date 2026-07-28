package com.gamerconnect.testclient.feature.feed

import android.util.Log
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage


@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    feedViewModel: FeedViewModel = viewModel()
) {
    val uiState = feedViewModel.uiState.collectAsStateWithLifecycle().value
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            FeedTab(
                title = "For You",
                selected = true
            )

            FeedTab(
                title = "Following",
                selected = false
            )

            FeedTab(
                title = "Groups",
                selected = false
            )
        }

        when {
            uiState.isLoading -> {
                Text(
                    text = "Loading feed...",
                    color = Color.White
                )
            }

            uiState.errorMessage != null -> {
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }

            uiState.posts.isEmpty() -> {
                Text(
                    text = "No posts yet.",
                    color = Color(0xFF9CA3AF)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(
                        items = uiState.posts,
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
                            createdAt = post.createdAt
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedTab(
    title: String,
    selected: Boolean
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            }
        )
    ) {
        Text(
            text = title,
            color = if (selected) Color.White else Color(0xFFB8BFCC),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(
                horizontal = 18.dp,
                vertical = 10.dp
            )
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
    createdAt: String
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
                timestamp = createdAt
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
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Like",
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Comments",
                    color = Color(0xFFB8BFCC)
                )

                Text(
                    text = "Share",
                    color = Color(0xFFB8BFCC)
                )
            }
        }
    }
}

@Composable
private fun FeedAuthorHeader(
    displayName: String,
    avatarUrl: String?,
    timestamp: String,
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
    modifier: Modifier = Modifier
) {
    when (feedMediaKind(mediaType, mediaUrl)) {
        FeedMediaKind.Image -> FeedMediaPreview(
            mediaUrl = mediaUrl,
            authorName = authorName,
            modifier = modifier
        )

        FeedMediaKind.Video -> FeedMediaFallback(
            text = "Video preview coming soon",
            modifier = modifier
        )

        FeedMediaKind.Unknown -> FeedMediaFallback(
            text = "Media unavailable",
            modifier = modifier
        )
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
