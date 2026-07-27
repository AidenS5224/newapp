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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@Composable
fun MessagesScreen(
    onConversationClick: (
        conversationId: String,
        conversationTitle: String,
        conversationType: String
    ) -> Unit,
    modifier: Modifier = Modifier,
    messagesViewModel: MessagesViewModel = viewModel()
) {
    val uiState = messagesViewModel.uiState.collectAsStateWithLifecycle().value

    val filteredConversations = uiState.conversations.filter { conversation ->
        val matchesSearch =
            uiState.searchQuery.isBlank() ||
                    conversation.title.contains(
                        uiState.searchQuery,
                        ignoreCase = true
                    ) ||
                    conversation.latestMessage
                        ?.contains(
                            uiState.searchQuery,
                            ignoreCase = true
                        ) == true

        val matchesFilter = when (uiState.selectedFilter) {
            MessageFilter.ALL -> true
            MessageFilter.UNREAD -> conversation.unreadCount > 0
            MessageFilter.GROUPS ->
                conversation.conversationType == "group"
        }

        matchesSearch && matchesFilter
    }

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


        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = messagesViewModel::updateSearchQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text("Search conversations")
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

        MessageFilterRow(
            selectedFilter = uiState.selectedFilter,
            onFilterSelected = messagesViewModel::updateFilter
        )

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

            filteredConversations.isEmpty() -> {
                Text(
                    text = "No conversations yet.",
                    color = Color(0xFF9CA3AF)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = filteredConversations,
                        key = { conversation -> conversation.id }
                    ) { conversation ->
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
                                    conversation.title,
                                    conversation.conversationType
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageFilterRow(
    selectedFilter: MessageFilter,
    onFilterSelected: (MessageFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MessageFilterChip(
            title = "All",
            selected = selectedFilter == MessageFilter.ALL,
            onClick = {
                onFilterSelected(MessageFilter.ALL)
            }
        )

        MessageFilterChip(
            title = "Unread",
            selected = selectedFilter == MessageFilter.UNREAD,
            onClick = {
                onFilterSelected(MessageFilter.UNREAD)
            }
        )

        MessageFilterChip(
            title = "Groups",
            selected = selectedFilter == MessageFilter.GROUPS,
            onClick = {
                onFilterSelected(MessageFilter.GROUPS)
            }
        )
    }
}

@Composable
private fun MessageFilterChip(
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
