package com.gamerconnect.testclient.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.gamerconnect.testclient.data.messages.Conversation
import com.gamerconnect.testclient.data.messages.GroupMember
import com.gamerconnect.testclient.data.messages.MessagesRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun GroupDetailsScreen(
    conversationId: String,
    onBack: () -> Unit,
    onLeaveSuccess: () -> Unit,
    onPlayerProfileClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val repository = remember {
        MessagesRepository()
    }
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember(conversationId) {
        mutableStateOf(true)
    }

    var isLeaving by remember(conversationId) {
        mutableStateOf(false)
    }

    var showLeaveConfirmation by remember(conversationId) {
        mutableStateOf(false)
    }

    var isTransferringOwnership by remember(conversationId) {
        mutableStateOf(false)
    }

    var showTransferConfirmation by remember(conversationId) {
        mutableStateOf(false)
    }

    var selectedTransferMember by remember(conversationId) {
        mutableStateOf<GroupMember?>(null)
    }

    var currentUserId by remember(conversationId) {
        mutableStateOf<String?>(null)
    }

    var groupConversation by remember(conversationId) {
        mutableStateOf<Conversation?>(null)
    }

    var groupMembers by remember(conversationId) {
        mutableStateOf<List<GroupMember>>(emptyList())
    }

    var errorMessage by remember(conversationId) {
        mutableStateOf<String?>(null)
    }

    var leaveErrorMessage by remember(conversationId) {
        mutableStateOf<String?>(null)
    }

    var transferErrorMessage by remember(conversationId) {
        mutableStateOf<String?>(null)
    }

    var transferSuccessMessage by remember(conversationId) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(conversationId) {
        isLoading = true
        errorMessage = null
        leaveErrorMessage = null
        transferErrorMessage = null
        transferSuccessMessage = null

        runCatching {
            val userId = repository.getCurrentUserId()
                ?: error("No signed-in user.")

            val conversation = repository
                .getMyConversations()
                .firstOrNull { conversation ->
                    conversation.id == conversationId &&
                            conversation.conversationType == "group"
                }

            val members = if (conversation == null) {
                emptyList()
            } else {
                repository.getGroupMembers(conversationId)
            }

            Triple(conversation, members, userId)
        }.onSuccess { groupDetails ->
            groupConversation = groupDetails.first
            groupMembers = groupDetails.second
            currentUserId = groupDetails.third
            isLoading = false
        }.onFailure { error ->
            errorMessage = error.message
                ?: "Something went wrong while loading this group."
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
                    text = "Group Details",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Basic info for this group chat.",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp
                )
            }
        }

        val loadedConversation = groupConversation
        val isCurrentUserOwner = groupMembers.any { member ->
            member.profileId == currentUserId &&
                    member.role.equals("owner", ignoreCase = true)
        }

        when {
            isLoading -> {
                GroupDetailsStatusCard {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Loading group details...",
                        color = Color.White
                    )
                }
            }

            errorMessage != null -> {
                GroupDetailsStatusCard {
                    Text(
                        text = "We couldn't load this group yet.",
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

            loadedConversation == null -> {
                GroupDetailsStatusCard {
                    Text(
                        text = "Group not found",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "This may be a direct chat, a deleted group, or a group you no longer have access to.",
                        color = Color(0xFFB8BFCC)
                    )
                }
            }

            else -> {
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
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = loadedConversation.title.ifBlank {
                                    "Untitled Group"
                                },
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )

                            GroupMetadataRow(
                                label = "Conversation ID",
                                value = loadedConversation.id
                            )

                            GroupMetadataRow(
                                label = "Created",
                                value = formatGroupCreatedDate(
                                    loadedConversation.createdAt
                                )
                            )
                        }
                    }

                    GroupMembersCard(
                        members = groupMembers,
                        onMemberClick = { member ->
                            onPlayerProfileClick(member.profileId)
                        }
                    )

                    if (transferSuccessMessage != null) {
                        GroupDetailsStatusCard {
                            Text(
                                text = transferSuccessMessage
                                    ?: "Ownership transferred.",
                                color = Color(0xFF34D399),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isCurrentUserOwner) {
                        OwnerControlsCard(
                            members = groupMembers,
                            currentUserId = currentUserId,
                            selectedMember = selectedTransferMember,
                            isTransferring = isTransferringOwnership,
                            errorMessage = transferErrorMessage,
                            onMemberSelected = { member ->
                                selectedTransferMember = member
                                transferErrorMessage = null
                                transferSuccessMessage = null
                            },
                            onTransferClick = {
                                if (selectedTransferMember == null) {
                                    transferErrorMessage =
                                        "Choose another member to make owner."
                                } else {
                                    showTransferConfirmation = true
                                }
                            }
                        )
                    }

                    LeaveGroupCard(
                        isLeaving = isLeaving,
                        errorMessage = leaveErrorMessage,
                        onLeaveClick = {
                            showLeaveConfirmation = true
                        }
                    )
                }
            }
        }
    }

    if (showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!isLeaving) {
                    showLeaveConfirmation = false
                }
            },
            title = {
                Text(
                    text = "Leave group?",
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "You will be removed from this group chat. Other members and the conversation will stay in place.",
                    color = Color(0xFFB8BFCC)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isLeaving,
                    onClick = {
                        if (isLeaving) {
                            return@TextButton
                        }

                        isLeaving = true
                        leaveErrorMessage = null

                        coroutineScope.launch {
                            runCatching {
                                repository.leaveGroupConversation(
                                    conversationId = conversationId
                                )
                            }.onSuccess {
                                isLeaving = false
                                showLeaveConfirmation = false
                                onLeaveSuccess()
                            }.onFailure { error ->
                                isLeaving = false
                                showLeaveConfirmation = false
                                leaveErrorMessage = error.message
                                    ?: "Unable to leave this group right now."
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (isLeaving) {
                            "Leaving..."
                        } else {
                            "Leave group"
                        },
                        color = Color(0xFFFCA5A5)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isLeaving,
                    onClick = {
                        showLeaveConfirmation = false
                    }
                ) {
                    Text(
                        text = "Cancel",
                        color = Color(0xFFB8BFCC)
                    )
                }
            },
            containerColor = Color(0xFF0B1220)
        )
    }

    if (showTransferConfirmation) {
        val targetMember = selectedTransferMember

        AlertDialog(
            onDismissRequest = {
                if (!isTransferringOwnership) {
                    showTransferConfirmation = false
                }
            },
            title = {
                Text(
                    text = "Transfer ownership?",
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Make ${targetMember?.displayName ?: "this member"} the group owner? You will become a normal member and can leave afterward.",
                    color = Color(0xFFB8BFCC)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isTransferringOwnership && targetMember != null,
                    onClick = {
                        val member = selectedTransferMember
                            ?: return@TextButton

                        if (isTransferringOwnership) {
                            return@TextButton
                        }

                        isTransferringOwnership = true
                        transferErrorMessage = null
                        transferSuccessMessage = null

                        coroutineScope.launch {
                            runCatching {
                                repository.transferGroupOwnership(
                                    conversationId = conversationId,
                                    newOwnerProfileId = member.profileId
                                )
                            }.onSuccess {
                                val refreshedMembers =
                                    repository.getGroupMembers(conversationId)

                                groupMembers = refreshedMembers
                                selectedTransferMember = null
                                isTransferringOwnership = false
                                showTransferConfirmation = false
                                transferSuccessMessage =
                                    "${member.displayName} is now the owner."
                            }.onFailure { error ->
                                isTransferringOwnership = false
                                showTransferConfirmation = false
                                transferErrorMessage = error.message
                                    ?: "Unable to transfer ownership right now."
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (isTransferringOwnership) {
                            "Transferring..."
                        } else {
                            "Transfer"
                        },
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isTransferringOwnership,
                    onClick = {
                        showTransferConfirmation = false
                    }
                ) {
                    Text(
                        text = "Cancel",
                        color = Color(0xFFB8BFCC)
                    )
                }
            },
            containerColor = Color(0xFF0B1220)
        )
    }
}

@Composable
private fun OwnerControlsCard(
    members: List<GroupMember>,
    currentUserId: String?,
    selectedMember: GroupMember?,
    isTransferring: Boolean,
    errorMessage: String?,
    onMemberSelected: (GroupMember) -> Unit,
    onTransferClick: () -> Unit
) {
    val eligibleMembers = members.filter { member ->
        member.profileId != currentUserId
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1220)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Owner controls",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Transfer ownership to another current member.",
                color = Color(0xFFB8BFCC),
                fontSize = 14.sp
            )

            if (eligibleMembers.isEmpty()) {
                Text(
                    text = "There are no other members to transfer ownership to yet.",
                    color = Color(0xFFB8BFCC),
                    fontSize = 14.sp
                )
            } else {
                eligibleMembers.forEach { member ->
                    OwnerTransferMemberRow(
                        member = member,
                        selected = selectedMember?.profileId == member.profileId,
                        enabled = !isTransferring,
                        onClick = {
                            onMemberSelected(member)
                        }
                    )
                }

                Button(
                    onClick = onTransferClick,
                    enabled = !isTransferring,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = Color(0xFF3F2A63)
                    )
                ) {
                    Text(
                        text = if (isTransferring) {
                            "Transferring..."
                        } else {
                            "Transfer ownership"
                        },
                        color = Color.White
                    )
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun OwnerTransferMemberRow(
    member: GroupMember,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                Color(0xFF25104B)
            } else {
                Color(0xFF111827)
            },
            disabledContainerColor = Color(0xFF111827)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemberAvatar(
                displayName = member.displayName,
                avatarUrl = member.avatarUrl,
                size = 40
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = member.displayName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (selected) {
                        "Selected"
                    } else {
                        "Member"
                    },
                    color = Color(0xFFB8BFCC),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun LeaveGroupCard(
    isLeaving: Boolean,
    errorMessage: String?,
    onLeaveClick: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Group actions",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Leaving removes you from this group chat only. It will not delete the group for other members.",
                color = Color(0xFFB8BFCC),
                fontSize = 14.sp
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }

            Button(
                onClick = onLeaveClick,
                enabled = !isLeaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7F1D1D),
                    disabledContainerColor = Color(0xFF3F1D1D)
                )
            ) {
                Text(
                    text = if (isLeaving) {
                        "Leaving..."
                    } else {
                        "Leave group"
                    },
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun GroupMembersCard(
    members: List<GroupMember>,
    onMemberClick: (GroupMember) -> Unit
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Members",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${members.size}",
                    color = Color(0xFFB8BFCC),
                    fontSize = 13.sp
                )
            }

            if (members.isEmpty()) {
                Text(
                    text = "No members found for this group yet.",
                    color = Color(0xFFB8BFCC)
                )
            } else {
                members.forEach { member ->
                    GroupMemberRow(
                        member = member,
                        onClick = {
                            onMemberClick(member)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupMemberRow(
    member: GroupMember,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MemberAvatar(
            displayName = member.displayName,
            avatarUrl = member.avatarUrl,
            size = 48
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = member.displayName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            roleLabel(member.role)?.let { label ->
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MemberAvatar(
    displayName: String,
    avatarUrl: String?,
    size: Int
) {
    Box(
        modifier = Modifier
            .size(size.dp)
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
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun roleLabel(
    role: String
): String? {
    return when (role.lowercase()) {
        "owner" -> "Owner"
        "admin" -> "Admin"
        else -> null
    }
}

@Composable
private fun GroupDetailsStatusCard(
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
private fun GroupMetadataRow(
    label: String,
    value: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF8D94A3),
            fontSize = 12.sp
        )

        Text(
            text = value,
            color = Color.White,
            fontSize = 15.sp
        )
    }
}

private fun formatGroupCreatedDate(
    timestamp: String
): String {
    return runCatching {
        Instant.parse(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a"))
    }.getOrDefault(timestamp)
}
