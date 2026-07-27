package com.gamerconnect.testclient.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamerconnect.testclient.data.messages.Conversation
import com.gamerconnect.testclient.data.messages.MessagesRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun GroupDetailsScreen(
    conversationId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember {
        MessagesRepository()
    }

    var isLoading by remember(conversationId) {
        mutableStateOf(true)
    }

    var groupConversation by remember(conversationId) {
        mutableStateOf<Conversation?>(null)
    }

    var errorMessage by remember(conversationId) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(conversationId) {
        isLoading = true
        errorMessage = null

        runCatching {
            repository
                .getMyConversations()
                .firstOrNull { conversation ->
                    conversation.id == conversationId &&
                            conversation.conversationType == "group"
                }
        }.onSuccess { conversation ->
            groupConversation = conversation
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
            }
        }
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
