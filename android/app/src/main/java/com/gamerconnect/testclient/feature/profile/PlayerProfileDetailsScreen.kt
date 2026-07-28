package com.gamerconnect.testclient.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.gamerconnect.testclient.data.messages.MessagesRepository
import com.gamerconnect.testclient.data.profile.FriendshipState
import com.gamerconnect.testclient.data.profile.FriendshipStatus
import com.gamerconnect.testclient.data.profile.PlayerSafetyState
import com.gamerconnect.testclient.data.profile.ProfileRepository
import com.gamerconnect.testclient.data.profile.UserProfile
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class ProfileLoadResult(
    val profile: UserProfile,
    val currentUserId: String?,
    val friendshipState: FriendshipState,
    val safetyState: PlayerSafetyState,
    val canMessage: Boolean
)

private val reportReasons = listOf(
    "Harassment",
    "Spam",
    "Hate or abusive content",
    "Impersonation",
    "Other"
)

@Composable
fun PlayerProfileDetailsScreen(
    profileId: String,
    onBack: () -> Unit,
    onDirectMessageOpened: (
        conversationId: String,
        conversationTitle: String,
        conversationType: String
    ) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val repository = remember {
        ProfileRepository()
    }
    val messagesRepository = remember {
        MessagesRepository()
    }
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember(profileId) {
        mutableStateOf(true)
    }
    var profile by remember(profileId) {
        mutableStateOf<UserProfile?>(null)
    }
    var errorMessage by remember(profileId) {
        mutableStateOf<String?>(null)
    }
    var currentUserId by remember(profileId) {
        mutableStateOf<String?>(null)
    }
    var canMessage by remember(profileId) {
        mutableStateOf(false)
    }
    var friendshipState by remember(profileId) {
        mutableStateOf(FriendshipState())
    }
    var isFriendActionLoading by remember(profileId) {
        mutableStateOf(false)
    }
    var friendshipError by remember(profileId) {
        mutableStateOf<String?>(null)
    }
    var safetyState by remember(profileId) {
        mutableStateOf(PlayerSafetyState())
    }
    var isSafetyActionLoading by remember(profileId) {
        mutableStateOf(false)
    }
    var safetyError by remember(profileId) {
        mutableStateOf<String?>(null)
    }
    var safetyMessage by remember(profileId) {
        mutableStateOf<String?>(null)
    }
    var showBlockConfirmation by remember(profileId) {
        mutableStateOf(false)
    }
    var showReportDialog by remember(profileId) {
        mutableStateOf(false)
    }
    var isOpeningDirectMessage by remember(profileId) {
        mutableStateOf(false)
    }
    var directMessageError by remember(profileId) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(profileId) {
        isLoading = true
        errorMessage = null
        directMessageError = null
        friendshipError = null
        safetyError = null
        safetyMessage = null

        runCatching {
            val loadedProfile = repository.getProfile(profileId)
            val userId = messagesRepository.getCurrentUserId()
            val relationship = if (userId != null && userId != profileId) {
                repository.getFriendshipWith(profileId)
            } else {
                FriendshipState()
            }
            val safety = if (userId != null && userId != profileId) {
                repository.getSafetyState(profileId)
            } else {
                PlayerSafetyState()
            }
            val eligible = if (userId != null && userId != profileId) {
                messagesRepository.canStartDirectMessage(profileId)
            } else {
                false
            }

            ProfileLoadResult(
                profile = loadedProfile,
                currentUserId = userId,
                friendshipState = relationship,
                safetyState = safety,
                canMessage = eligible
            )
        }.onSuccess { result ->
            profile = result.profile
            currentUserId = result.currentUserId
            friendshipState = result.friendshipState
            safetyState = result.safetyState
            canMessage = result.canMessage
            isLoading = false
        }.onFailure { error ->
            errorMessage = error.message
                ?: "Unable to load this player profile."
            isLoading = false
        }
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF111827)
                )
            ) {
                Text(
                    text = "Back",
                    color = Color.White
                )
            }

            Column {
                Text(
                    text = "Player Profile",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Public gamer details.",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp
                )
            }
        }

        when {
            isLoading -> {
                ProfileStatusCard {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Loading player profile...",
                        color = Color.White
                    )
                }
            }

            errorMessage != null -> {
                ProfileStatusCard {
                    Text(
                        text = "We couldn't load this player yet.",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = errorMessage ?: "Please try again shortly.",
                        color = Color(0xFFB8BFCC)
                    )
                }
            }

            profile == null -> {
                ProfileStatusCard {
                    Text(
                        text = "Player not found",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "This profile may no longer be available.",
                        color = Color(0xFFB8BFCC)
                    )
                }
            }

            else -> {
                val loadedProfile = profile
                if (loadedProfile != null) {
                    fun refreshRelationshipAndSafety() {
                        coroutineScope.launch {
                            runCatching {
                                val relationship = repository.getFriendshipWith(loadedProfile.id)
                                val safety = repository.getSafetyState(loadedProfile.id)
                                val eligible = messagesRepository.canStartDirectMessage(loadedProfile.id)
                                Triple(relationship, safety, eligible)
                            }.onSuccess { result ->
                                friendshipState = result.first
                                safetyState = result.second
                                canMessage = result.third
                            }.onFailure { error ->
                                friendshipError = error.message
                                    ?: "Unable to refresh player status."
                            }
                        }
                    }

                    fun runFriendAction(
                        action: suspend () -> Unit
                    ) {
                        if (isFriendActionLoading) {
                            return
                        }

                        isFriendActionLoading = true
                        friendshipError = null

                        coroutineScope.launch {
                            runCatching {
                                action()
                            }.onSuccess {
                                isFriendActionLoading = false
                                refreshRelationshipAndSafety()
                            }.onFailure { error ->
                                isFriendActionLoading = false
                                friendshipError = error.message
                                    ?: "Friendship action failed. Try again."
                            }
                        }
                    }

                    fun runSafetyAction(
                        successMessage: String,
                        action: suspend () -> Unit
                    ) {
                        if (isSafetyActionLoading) {
                            return
                        }

                        isSafetyActionLoading = true
                        safetyError = null
                        safetyMessage = null

                        coroutineScope.launch {
                            runCatching {
                                action()
                            }.onSuccess {
                                isSafetyActionLoading = false
                                safetyMessage = successMessage
                                refreshRelationshipAndSafety()
                            }.onFailure { error ->
                                isSafetyActionLoading = false
                                safetyError = error.message
                                    ?: "Safety action failed. Try again."
                            }
                        }
                    }

                    if (showBlockConfirmation) {
                        BlockConfirmationDialog(
                            isBlocked = safetyState.isBlockedByCurrentUser,
                            playerName = loadedProfile.displayName.ifBlank {
                                "this player"
                            },
                            onDismiss = {
                                showBlockConfirmation = false
                            },
                            onConfirm = {
                                showBlockConfirmation = false
                                if (safetyState.isBlockedByCurrentUser) {
                                    runSafetyAction("Player unblocked.") {
                                        repository.unblockPlayer(loadedProfile.id)
                                    }
                                } else {
                                    runSafetyAction("Player blocked.") {
                                        repository.blockPlayer(loadedProfile.id)
                                    }
                                }
                            }
                        )
                    }

                    if (showReportDialog) {
                        ReportPlayerDialog(
                            playerName = loadedProfile.displayName.ifBlank {
                                "this player"
                            },
                            isSubmitting = isSafetyActionLoading,
                            onDismiss = {
                                if (!isSafetyActionLoading) {
                                    showReportDialog = false
                                }
                            },
                            onSubmit = { reason, description ->
                                showReportDialog = false
                                runSafetyAction("Report submitted. Thanks for helping keep Gamer Connect safe.") {
                                    repository.reportPlayer(
                                        profileId = loadedProfile.id,
                                        reason = reason,
                                        description = description
                                    )
                                }
                            }
                        )
                    }

                    PlayerProfileContent(
                        profile = loadedProfile,
                        isOwnProfile = loadedProfile.id == currentUserId,
                        canMessage = canMessage,
                        friendshipState = friendshipState,
                        safetyState = safetyState,
                        isFriendActionLoading = isFriendActionLoading,
                        isSafetyActionLoading = isSafetyActionLoading,
                        friendshipError = friendshipError,
                        safetyError = safetyError,
                        safetyMessage = safetyMessage,
                        isOpeningDirectMessage = isOpeningDirectMessage,
                        directMessageError = directMessageError,
                        onAddFriendClick = {
                            runFriendAction {
                                repository.sendFriendRequest(loadedProfile.id)
                            }
                        },
                        onAcceptFriendClick = {
                            val connectionId = friendshipState.connectionId
                            if (connectionId != null) {
                                runFriendAction {
                                    repository.acceptFriendRequest(connectionId)
                                }
                            }
                        },
                        onDeclineFriendClick = {
                            val connectionId = friendshipState.connectionId
                            if (connectionId != null) {
                                runFriendAction {
                                    repository.declineFriendRequest(connectionId)
                                }
                            }
                        },
                        onRemoveFriendClick = {
                            runFriendAction {
                                repository.removeFriend(loadedProfile.id)
                            }
                        },
                        onBlockClick = {
                            showBlockConfirmation = true
                        },
                        onReportClick = {
                            showReportDialog = true
                        },
                        onMessageClick = {
                            if (!isOpeningDirectMessage) {
                                isOpeningDirectMessage = true
                                directMessageError = null

                                coroutineScope.launch {
                                    runCatching {
                                        messagesRepository.startDirectMessage(
                                            targetProfileId = loadedProfile.id
                                        )
                                    }.onSuccess { conversationId ->
                                        isOpeningDirectMessage = false
                                        onDirectMessageOpened(
                                            conversationId,
                                            loadedProfile.displayName.ifBlank {
                                                "Direct Chat"
                                            },
                                            "direct"
                                        )
                                    }.onFailure { error ->
                                        isOpeningDirectMessage = false
                                        directMessageError = error.message
                                            ?: "Unable to open this conversation."
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerProfileContent(
    profile: UserProfile,
    isOwnProfile: Boolean,
    canMessage: Boolean,
    friendshipState: FriendshipState,
    safetyState: PlayerSafetyState,
    isFriendActionLoading: Boolean,
    isSafetyActionLoading: Boolean,
    friendshipError: String?,
    safetyError: String?,
    safetyMessage: String?,
    isOpeningDirectMessage: Boolean,
    directMessageError: String?,
    onAddFriendClick: () -> Unit,
    onAcceptFriendClick: () -> Unit,
    onDeclineFriendClick: () -> Unit,
    onRemoveFriendClick: () -> Unit,
    onBlockClick: () -> Unit,
    onReportClick: () -> Unit,
    onMessageClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0B1220)
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileAvatar(
                    displayName = profile.displayName,
                    avatarUrl = profile.avatarUrl
                )

                Text(
                    text = profile.displayName.ifBlank {
                        "Unknown player"
                    },
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = profile.handle.takeIf { it.isNotBlank() }
                        ?.let { "@$it" }
                        ?: "Handle not set",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp
                )

                when {
                    isOwnProfile -> {
                        Text(
                            text = "This is your public profile.",
                            color = Color(0xFFB8BFCC),
                            fontSize = 13.sp
                        )
                    }

                    !isOwnProfile -> {
                        FriendshipActions(
                            friendshipState = friendshipState,
                            canMessage = canMessage && !safetyState.isBlockedByCurrentUser,
                            isBlocked = safetyState.isBlockedByCurrentUser,
                            isFriendActionLoading = isFriendActionLoading,
                            isOpeningDirectMessage = isOpeningDirectMessage,
                            onAddFriendClick = onAddFriendClick,
                            onAcceptFriendClick = onAcceptFriendClick,
                            onDeclineFriendClick = onDeclineFriendClick,
                            onRemoveFriendClick = onRemoveFriendClick,
                            onMessageClick = onMessageClick
                        )
                    }
                }

                if (friendshipError != null) {
                    Text(
                        text = friendshipError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                if (!isOwnProfile && !safetyState.isBlockedByCurrentUser && !canMessage) {
                    Text(
                        text = "Become friends to message this player.",
                        color = Color(0xFFB8BFCC),
                        fontSize = 13.sp
                    )
                }

                if (safetyState.isBlockedByCurrentUser) {
                    Text(
                        text = "You blocked this player. Friend requests and direct messages are disabled until you unblock them.",
                        color = Color(0xFFFCA5A5),
                        fontSize = 13.sp
                    )
                }

                if (directMessageError != null) {
                    Text(
                        text = directMessageError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (!isOwnProfile) {
            SafetyActionsSection(
                isBlocked = safetyState.isBlockedByCurrentUser,
                isLoading = isSafetyActionLoading,
                errorMessage = safetyError,
                successMessage = safetyMessage,
                onBlockClick = onBlockClick,
                onReportClick = onReportClick
            )
        }

        ProfileDetailsSection(
            title = "About",
            body = profile.bio.ifBlank {
                "No bio added yet."
            }
        )

        ProfileDetailsSection(
            title = "Region",
            body = profile.region.ifBlank {
                "Region not set."
            }
        )

        ProfileDetailsSection(
            title = "Platforms",
            body = profile.platforms
                .takeIf { it.isNotEmpty() }
                ?.joinToString("  |  ")
                ?: "No platforms added yet."
        )

        ProfileDetailsSection(
            title = "Games / Interests",
            body = profile.topGames
                .takeIf { it.isNotEmpty() }
                ?.joinToString("  |  ")
                ?: "No games added yet."
        )

        ProfileDetailsSection(
            title = "Play Style",
            body = profile.playStyle
                .takeIf { it.isNotEmpty() }
                ?.joinToString("  |  ")
                ?: "No play style added yet."
        )

        ProfileDetailsSection(
            title = "Joined",
            body = formatProfileCreatedDate(profile.createdAt)
        )
    }
}

@Composable
private fun FriendshipActions(
    friendshipState: FriendshipState,
    canMessage: Boolean,
    isBlocked: Boolean,
    isFriendActionLoading: Boolean,
    isOpeningDirectMessage: Boolean,
    onAddFriendClick: () -> Unit,
    onAcceptFriendClick: () -> Unit,
    onDeclineFriendClick: () -> Unit,
    onRemoveFriendClick: () -> Unit,
    onMessageClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (friendshipState.status) {
            FriendshipStatus.ACCEPTED -> {
                Text(
                    text = "Friends",
                    color = Color(0xFF86EFAC),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onMessageClick,
                        enabled = canMessage && !isBlocked && !isOpeningDirectMessage,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color(0xFF3F2A63)
                        )
                    ) {
                        Text(
                            text = if (isOpeningDirectMessage) {
                                "Opening..."
                            } else {
                                "Message"
                            },
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = onRemoveFriendClick,
                        enabled = !isFriendActionLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7F1D1D),
                            disabledContainerColor = Color(0xFF3F1D1D)
                        )
                    ) {
                        Text(
                            text = if (isFriendActionLoading) {
                                "Removing..."
                            } else {
                                "Remove friend"
                            },
                            color = Color.White
                        )
                    }
                }
            }

            FriendshipStatus.OUTGOING_PENDING -> {
                Button(
                    onClick = {},
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = Color(0xFF111827)
                    )
                ) {
                    Text(
                        text = "Request sent",
                        color = Color.White
                    )
                }
            }

            FriendshipStatus.INCOMING_PENDING -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onAcceptFriendClick,
                        enabled = !isBlocked && !isFriendActionLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF15803D),
                            disabledContainerColor = Color(0xFF12351F)
                        )
                    ) {
                        Text(
                            text = if (isFriendActionLoading) {
                                "Accepting..."
                            } else {
                                "Accept"
                            },
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = onDeclineFriendClick,
                        enabled = !isFriendActionLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7F1D1D),
                            disabledContainerColor = Color(0xFF3F1D1D)
                        )
                    ) {
                        Text(
                            text = if (isFriendActionLoading) {
                                "Declining..."
                            } else {
                                "Decline"
                            },
                            color = Color.White
                        )
                    }
                }
            }

            FriendshipStatus.REJECTED,
            FriendshipStatus.NONE -> {
                Button(
                    onClick = onAddFriendClick,
                    enabled = !isBlocked && !isFriendActionLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = Color(0xFF3F2A63)
                    )
                ) {
                    Text(
                        text = if (isFriendActionLoading) {
                            "Sending..."
                        } else {
                            "Add friend"
                        },
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyActionsSection(
    isBlocked: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onBlockClick: () -> Unit,
    onReportClick: () -> Unit
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
                text = "Safety",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Block or report players who break the vibe.",
                color = Color(0xFFB8BFCC),
                fontSize = 13.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onBlockClick,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBlocked) {
                            Color(0xFF111827)
                        } else {
                            Color(0xFF7F1D1D)
                        },
                        disabledContainerColor = Color(0xFF3F1D1D)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = when {
                            isLoading -> "Working..."
                            isBlocked -> "Unblock"
                            else -> "Block player"
                        },
                        color = Color.White
                    )
                }

                Button(
                    onClick = onReportClick,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF25104B),
                        disabledContainerColor = Color(0xFF111827)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Report player",
                        color = Color.White
                    )
                }
            }

            if (!successMessage.isNullOrBlank()) {
                Text(
                    text = successMessage,
                    color = Color(0xFF86EFAC),
                    fontSize = 13.sp
                )
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun BlockConfirmationDialog(
    isBlocked: Boolean,
    playerName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBlocked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color(0xFF7F1D1D)
                    }
                )
            ) {
                Text(
                    text = if (isBlocked) {
                        "Unblock"
                    } else {
                        "Block"
                    },
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        },
        title = {
            Text(
                text = if (isBlocked) {
                    "Unblock $playerName?"
                } else {
                    "Block $playerName?"
                }
            )
        },
        text = {
            Text(
                text = if (isBlocked) {
                    "They will be able to send future friend requests after you unblock them."
                } else {
                    "This removes any friendship and prevents new friend requests or direct messages. Shared group chats stay available."
                }
            )
        },
        containerColor = Color(0xFF0B1220),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFB8BFCC)
    )
}

@Composable
private fun ReportPlayerDialog(
    playerName: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var selectedReason by remember {
        mutableStateOf(reportReasons.first())
    }
    var description by remember {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(selectedReason, description)
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = Color(0xFF3F2A63)
                )
            ) {
                Text(
                    text = if (isSubmitting) {
                        "Submitting..."
                    } else {
                        "Submit report"
                    },
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("Cancel")
            }
        },
        title = {
            Text("Report $playerName")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Choose a reason. Reports are private.",
                    color = Color(0xFFB8BFCC),
                    fontSize = 13.sp
                )

                reportReasons.forEach { reason ->
                    Button(
                        onClick = {
                            selectedReason = reason
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedReason == reason) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color(0xFF111827)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = reason,
                            color = Color.White
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { value ->
                        description = value.take(500)
                    },
                    label = {
                        Text("Optional details")
                    },
                    placeholder = {
                        Text("Short description")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    minLines = 3
                )
            }
        },
        containerColor = Color(0xFF0B1220),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFB8BFCC)
    )
}

@Composable
private fun ProfileAvatar(
    displayName: String,
    avatarUrl: String?
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(Color(0xFF25104B)),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "$displayName avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = displayName
                    .trim()
                    .firstOrNull()
                    ?.uppercase()
                    ?: "?",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ProfileStatusCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1220)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun ProfileDetailsSection(
    title: String,
    body: String
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = body,
                color = Color(0xFFB8BFCC)
            )
        }
    }
}

private fun formatProfileCreatedDate(
    timestamp: String
): String {
    if (timestamp.isBlank()) {
        return "Join date not available."
    }

    return runCatching {
        Instant.parse(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }.getOrDefault(timestamp)
}
