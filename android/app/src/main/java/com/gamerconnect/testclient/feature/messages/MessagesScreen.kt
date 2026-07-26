package com.gamerconnect.testclient.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MessagesScreen(
    onConversationClick: (
        conversationId: String,
        conversationTitle: String
    ) -> Unit,
    modifier: Modifier = Modifier,
    messagesViewModel: MessagesViewModel = viewModel()
) {
    val uiState = messagesViewModel.uiState.collectAsStateWithLifecycle().value
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Messages",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        MessageFilterRow()

        when {
            uiState.isLoading -> {
                Text(
                    text = "Loading conversations...",
                    color = Color.White
                )
            }

            uiState.errorMessage != null -> {
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }

            uiState.conversations.isEmpty() -> {
                Text(
                    text = "No conversations yet.",
                    color = Color(0xFF9CA3AF)
                )
            }

            else -> {
                uiState.conversations.forEach { conversation ->
                    ConversationCard(
                        name = conversation.title,
                        preview = conversation.latestMessage
                            ?: if (conversation.conversationType == "group") {
                                "LFG group chat"
                            } else {
                                "Direct conversation"
                            },
                        time = conversation.latestMessageAt
                            ?.let { timestamp ->
                                runCatching {
                                    Instant.parse(timestamp)
                                        .atZone(ZoneId.systemDefault())
                                        .format(
                                            DateTimeFormatter.ofPattern("h:mm a")
                                        )
                                }.getOrDefault("")
                            }
                            ?: "",
                        unreadCount = conversation.unreadCount,
                        onClick = {
                            messagesViewModel.clearUnreadCount(
                                conversation.id
                            )

                            onConversationClick(
                                conversation.id,
                                conversation.title
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageFilterRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MessageFilterChip("All", selected = true)
        MessageFilterChip("Unread", selected = false)
        MessageFilterChip("Groups", selected = false)
        MessageFilterChip("Requests", selected = false)
    }
}

@Composable
private fun MessageFilterChip(
    title: String,
    selected: Boolean
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color(0xFF111827)
            }
        )
    ) {
        Text(
            text = title,
            color = if (selected) Color.White else Color(0xFFB8BFCC),
            fontSize = 13.sp,
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 9.dp
            )
        )
    }
}

@Composable
private fun ConversationCard(
    name: String,
    preview: String,
    time: String,
    unreadCount: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1220)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF25104B)
                )
            ) {
                Text(
                    text = name.first().toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = preview,
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = time,
                    color = Color(0xFF8D94A3),
                    fontSize = 12.sp
                )

                if (unreadCount > 0) {
                    Card(
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = unreadCount.toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 4.dp
                            )
                        )
                    }
                }
            }
        }
    }
}
