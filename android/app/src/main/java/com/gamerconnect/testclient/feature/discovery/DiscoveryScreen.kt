package com.gamerconnect.testclient.feature.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamerconnect.testclient.data.profile.UserProfile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gamerconnect.testclient.data.lfg.LfgPost
import com.gamerconnect.testclient.data.lfg.LfgJoinRequest

@Composable
fun DiscoveryScreen(
    modifier: Modifier = Modifier,
    discoveryViewModel: DiscoveryViewModel = viewModel(),
    lfgViewModel: LfgViewModel = viewModel(),
    onPlayerProfileClick: (String) -> Unit = {}
) {
    val uiState = discoveryViewModel.uiState.collectAsStateWithLifecycle().value
    val lfgState = lfgViewModel.uiState.collectAsStateWithLifecycle().value

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Discovery / LFG",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DiscoveryTab(
                title = "Looking For Group",
                selected = selectedTab == 0,
                onClick = {
                    selectedTab = 0
                }
            )

            DiscoveryTab(
                title = "Matches",
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                }
            )

            DiscoveryTab(
                title = "My Posts",
                selected = selectedTab == 2,
                onClick = {
                    selectedTab = 2
                }
            )
        }

        when (selectedTab) {
            0 -> {
                LfgSearchAndFilters(
                    searchQuery = lfgState.searchQuery,
                    statusFilter = lfgState.statusFilter,
                    onSearchQueryChange = lfgViewModel::updateSearchQuery,
                    onClearSearch = lfgViewModel::clearSearch,
                    onStatusFilterChange = lfgViewModel::updateStatusFilter
                )

                when {
                    lfgState.isLoading -> {
                        Text(
                            text = "Loading LFG posts...",
                            color = Color.White
                        )
                    }

                    lfgState.errorMessage != null -> {
                        Text(
                            text = lfgState.errorMessage,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    lfgState.posts.isEmpty() -> {
                        Text(
                            text = "No LFG posts yet.",
                            color = Color(0xFF9CA3AF)
                        )
                    }

                    lfgState.filteredPosts.isEmpty() -> {
                        Text(
                            text = "No LFG posts match your search and filters.",
                            color = Color(0xFF9CA3AF)
                        )
                    }

                    else -> {
                        lfgState.filteredPosts.forEach { post ->
                            LfgPostCard(
                                post = post,
                                isOwner = post.profileId == lfgState.currentUserId,
                                isRequesting = lfgState.requestingPostId == post.id,
                                isClosing = lfgState.closingPostId == post.id,
                                isRequested = post.id in lfgState.requestedPostIds,
                                onRequestToJoin = {
                                    lfgViewModel.requestToJoin(post.id)
                                },
                                onClose = {
                                    lfgViewModel.closePost(post.id)
                                },
                                onOwnerClick = {
                                    onPlayerProfileClick(post.profileId)
                                }
                            )
                        }
                    }
                }
            }

            1 -> {
                when {
                    uiState.isLoading -> {
                        Text(
                            text = "Loading players...",
                            color = Color.White
                        )
                    }

                    uiState.errorMessage != null -> {
                        Text(
                            text = uiState.errorMessage,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    uiState.profiles.isEmpty() -> {
                        Text(
                            text = "No players found.",
                            color = Color(0xFF9CA3AF)
                        )
                    }

                    else -> {
                        val profile = uiState.profiles[uiState.currentIndex]

                        PlayerCard(
                            profile = profile,
                            onPass = discoveryViewModel::nextProfile,
                            onPlay = discoveryViewModel::nextProfile
                        )
                    }
                }
            }

            2 -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CreateLfgForm(
                        isCreating = lfgState.isCreating,
                        errorMessage = lfgState.errorMessage,
                        creationMessage = lfgState.creationMessage,
                        onCreate = lfgViewModel::createPost
                    )

                    lfgState.lifecycleMessage?.let { message ->
                        Text(
                            text = message,
                            color = Color(0xFF86EFAC),
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = "Pending Join Requests",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (lfgState.pendingOwnerRequests.isEmpty()) {
                        Text(
                            text = "No pending requests.",
                            color = Color(0xFF9CA3AF)
                        )
                    } else {
                        lfgState.pendingOwnerRequests.forEach { request ->
                            OwnerJoinRequestCard(
                                request = request,
                                onAccept = {
                                    lfgViewModel.acceptJoinRequest(request.id)
                                },
                                onReject = {
                                    lfgViewModel.rejectJoinRequest(request.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LfgSearchAndFilters(
    searchQuery: String,
    statusFilter: LfgStatusFilter,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onStatusFilterChange: (LfgStatusFilter) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1220)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = {
                        Text("Search LFG")
                    },
                    placeholder = {
                        Text("Game, post, owner, rank...")
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                if (searchQuery.isNotBlank()) {
                    Button(
                        onClick = onClearSearch,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1F2937)
                        )
                    ) {
                        Text(
                            text = "Clear",
                            color = Color.White
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LfgStatusFilter.entries.forEach { filter ->
                    LfgStatusFilterChip(
                        label = filter.label,
                        selected = statusFilter == filter,
                        onClick = {
                            onStatusFilterChange(filter)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LfgStatusFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color(0xFF111827)
            }
        )
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun DiscoveryTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
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
            fontSize = 12.sp,
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp
            )
        )
    }
}

@Composable
private fun OwnerJoinRequestCard(
    request: LfgJoinRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "New join request",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Requester: ${request.requesterProfileId}",
                color = Color(0xFFB8BFCC),
                fontSize = 13.sp
            )

            Text(
                text = "LFG post: ${request.lfgPostId}",
                color = Color(0xFF8D94A3),
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF15803D)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Accept",
                        color = Color.White
                    )
                }

                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7F1D1D)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Reject",
                        color = Color.White
                    )
                }
            }
        }
    }
}


@Composable
private fun LfgPostCard(
    post: LfgPost,
    isOwner: Boolean,
    isRequesting: Boolean,
    isClosing: Boolean,
    isRequested: Boolean,
    onRequestToJoin: () -> Unit,
    onClose: () -> Unit,
    onOwnerClick: () -> Unit
) {
    val isOpen = post.status == "open"

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = post.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                LfgStatusBadge(
                    status = post.status
                )
            }

            Text(
                text = post.mode,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp
            )

            Text(
                text = "By ${post.ownerDisplayName.ifBlank { "Unknown player" }}",
                color = Color(0xFFB8BFCC),
                fontSize = 13.sp,
                modifier = Modifier.clickable(
                    onClick = onOwnerClick
                )
            )

            if (post.rankRange.isNotBlank()) {
                Text(
                    text = "Rank: ${post.rankRange}",
                    color = Color(0xFFB8BFCC),
                    fontSize = 13.sp
                )
            }

            if (post.partySize.isNotBlank()) {
                Text(
                    text = "Party size: ${post.partySize}",
                    color = Color(0xFFB8BFCC),
                    fontSize = 13.sp
                )
            }

            if (post.startsAt.isNotBlank()) {
                Text(
                    text = "Starts: ${post.startsAt}",
                    color = Color(0xFF8D94A3),
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRequestToJoin,
                    enabled = isOpen && !isOwner && !isRequesting && !isRequested,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            !isOpen || isOwner || isRequested -> Color(0xFF1F2937)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = when {
                            isRequesting -> "Sending..."
                            isRequested -> "Requested"
                            isOwner -> "Your post"
                            post.status == "filled" -> "Filled"
                            post.status == "closed" -> "Closed"
                            else -> "Request to Join"
                        },
                        color = Color.White
                    )
                }

                if (isOwner && post.status != "closed") {
                    Button(
                        onClick = onClose,
                        enabled = !isClosing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7F1D1D)
                        )
                    ) {
                        Text(
                            text = if (isClosing) "Closing..." else "Close",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LfgStatusBadge(
    status: String
) {
    val normalizedStatus = status.lowercase()
    val badgeColor = when (normalizedStatus) {
        "filled" -> Color(0xFF854D0E)
        "closed" -> Color(0xFF7F1D1D)
        else -> Color(0xFF14532D)
    }
    val label = when (normalizedStatus) {
        "filled" -> "Filled"
        "closed" -> "Closed"
        else -> "Open"
    }

    Box(
        modifier = Modifier
            .background(
                color = badgeColor,
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PlayerCard(
    profile: UserProfile,
    onPass: () -> Unit,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1220)
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(Color(0xFF111A2B)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.displayName
                        .firstOrNull()
                        ?.uppercase()
                        ?: "?",
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = profile.displayName,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = buildString {
                        profile.age?.let {
                            append(it)
                            append(" · ")
                        }

                        append(
                            profile.region.ifBlank {
                                "Region not set"
                            }
                        )
                    },
                    color = Color(0xFF8D94A3),
                    fontSize = 14.sp
                )

                Text(
                    text = "Looking for",
                    color = Color(0xFF8D94A3),
                    fontSize = 12.sp
                )

                Text(
                    text = profile.topGames
                        .firstOrNull()
                        ?: "No game selected",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = listOf(
                        profile.rank,
                        profile.playStyle.joinToString(", ")
                    )
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )

                if (profile.bio.isNotBlank()) {
                    Text(
                        text = profile.bio,
                        color = Color(0xFFB8BFCC),
                        fontSize = 14.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onPass,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF151B26)
                    )
                ) {
                    Text(
                        text = "Pass",
                        color = Color.White
                    )
                }

                Button(
                    onClick = onPlay,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Play",
                        color = Color.White
                    )
                }
            }
        }
    }
}
