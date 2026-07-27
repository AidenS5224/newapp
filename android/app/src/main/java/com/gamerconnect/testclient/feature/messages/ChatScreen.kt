package com.gamerconnect.testclient.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale



@Composable
fun ChatScreen(
    conversationId: String,
    conversationTitle: String,
    conversationType: String,
    onBack: () -> Unit,
    onGroupDetailsClick: () -> Unit,
    modifier: Modifier = Modifier,
    chatViewModel: ChatViewModel = viewModel()
) {
    val uiState = chatViewModel.uiState
        .collectAsStateWithLifecycle()
        .value

    var messageText by remember {
        mutableStateOf("")
    }

    val listState = rememberLazyListState()

    LaunchedEffect(conversationId) {
        chatViewModel.loadMessages(conversationId)
        chatViewModel.observeMessages(conversationId)
    }

    LaunchedEffect(
        uiState.scrollToBottomSignal,
        uiState.isLoading
    ) {
        if (
            !uiState.isLoading &&
            uiState.messages.isNotEmpty()
        ) {
            listState.animateScrollToItem(
                uiState.messages.lastIndex
            )
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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

            if (conversationType == "group") {
                Button(
                    onClick = onGroupDetailsClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF25104B)
                    )
                ) {
                    Text(
                        text = "Group details",
                        color = Color.White
                    )
                }
            }
        }

        Text(
            text = conversationTitle,
            color = Color.White,
            fontSize = 24.sp
        )

        Text(
            text = "Conversation ID: $conversationId",
            color = Color(0xFF8D94A3),
            fontSize = 12.sp
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                uiState.isLoading -> {
                    item {
                        Text(
                            text = "Loading messages...",
                            color = Color.White
                        )
                    }
                }

                uiState.errorMessage != null -> {
                    item {
                        Text(
                            text = uiState.errorMessage,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                uiState.messages.isEmpty() -> {
                    item {
                        Text(
                            text = "No messages yet.",
                            color = Color(0xFFB8BFCC)
                        )
                    }
                }

                else -> {
                    item {
                        when {
                            uiState.isLoadingOlder -> {
                                Text(
                                    text = "Loading older messages...",
                                    color = Color(0xFFB8BFCC),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            uiState.hasMoreMessages -> {
                                Button(
                                    onClick = {
                                        chatViewModel.loadOlderMessages(conversationId)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF111827)
                                    )
                                ) {
                                    Text(
                                        text = "Load older messages",
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(
                        items = uiState.messages,
                        key = { _, message -> message.id }
                    ) { index, message ->
                        val currentDateLabel = formatMessageDateLabel(
                            message.createdAt
                        )

                        val previousDateLabel = uiState.messages
                            .getOrNull(index - 1)
                            ?.let { previousMessage ->
                                formatMessageDateLabel(
                                    previousMessage.createdAt
                                )
                            }

                        if (currentDateLabel != previousDateLabel) {
                            Text(
                                text = currentDateLabel,
                                color = Color(0xFF9CA3AF),
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }

                        MessageBubble(
                            body = message.body,
                            senderName = message.senderName,
                            senderAvatarUrl = message.senderAvatarUrl,
                            isCurrentUser =
                                message.senderProfileId == uiState.currentUserId,
                            isSeen = message.isSeen
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = {
                    Text("Message")
                },
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {
                    chatViewModel.sendMessage(
                        conversationId = conversationId,
                        body = messageText,
                        onSuccess = {
                            messageText = ""
                        }
                    )
                },
                enabled =
                    messageText.isNotBlank() &&
                            !uiState.isSending,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (uiState.isSending) {
                        "Sending..."
                    } else {
                        "Send"
                    },
                    color = Color.White
                )
            }
        }
    }
}

private fun formatMessageDateLabel(
    timestamp: String
): String {
    val messageDate = Instant.parse(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

    val today = LocalDate.now()

    return when (messageDate) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> messageDate.format(
            DateTimeFormatter.ofPattern("d MMM yyyy")
        )
    }
}

@Composable
private fun MessageBubble(
    body: String,
    senderName: String?,
    senderAvatarUrl: String?,
    isCurrentUser: Boolean,
    isSeen: Boolean
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isCurrentUser) {
            Alignment.CenterEnd
        } else {
            Alignment.CenterStart
        }
    ) {
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isCurrentUser) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    if (!senderAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = senderAvatarUrl,
                            contentDescription = "$senderName avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = senderName
                                ?.trim()
                                ?.firstOrNull()
                                ?.uppercase()
                                ?: "?",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(
                        color = if (isCurrentUser) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color(0xFF111827)
                        },
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(
                        horizontal = 14.dp,
                        vertical = 10.dp
                    )
            ) {
                if (!isCurrentUser && !senderName.isNullOrBlank()) {
                    Text(
                        text = senderName,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = body,
                    color = Color.White,
                    fontSize = 15.sp
                )

                if (isCurrentUser) {
                    Text(
                        text = if (isSeen) "Seen" else "Sent",
                        color = Color(0xFFDDD6FE),
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

