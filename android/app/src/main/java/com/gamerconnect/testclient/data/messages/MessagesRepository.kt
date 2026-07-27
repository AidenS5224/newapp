package com.gamerconnect.testclient.data.messages

import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID


@Serializable
private data class DirectConversationParticipantRow(
    @SerialName("conversation_id")
    val conversationId: String,

    @SerialName("profile_id")
    val profileId: String
)

@Serializable
private data class ParticipantReadStateRow(
    @SerialName("profile_id")
    val profileId: String,

    @SerialName("last_read_at")
    val lastReadAt: String? = null
)

@Serializable
private data class MessageSenderProfile(
    val id: String,

    @SerialName("display_name")
    val displayName: String,

    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

@Serializable
private data class ConversationParticipantRow(
    @SerialName("conversation_id")
    val conversationId: String,

    @SerialName("profile_id")
    val profileId: String,

    @SerialName("last_read_at")
    val lastReadAt: String? = null
)

@Serializable
private data class UnreadMessageRow(
    @SerialName("conversation_id")
    val conversationId: String,

    @SerialName("sender_profile_id")
    val senderProfileId: String,

    val body: String,

    @SerialName("created_at")
    val createdAt: String
)

@Serializable
private data class CreateMessageRequest(
    @SerialName("conversation_id")
    val conversationId: String,

    @SerialName("sender_profile_id")
    val senderProfileId: String,

    val body: String
)

@Serializable
private data class UpdateReadTimestampRequest(
    @SerialName("last_read_at")
    val lastReadAt: String
)



class MessagesRepository {


    fun observeConversationMessageChanges(): Flow<Unit> = callbackFlow {
        client.realtime.connect()

        val channel = client.realtime.channel(
            channelId = "conversation-list-${UUID.randomUUID()}"
        )

        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(
            schema = "public"
        ) {
            table = "messages"
        }

        val collectionJob = launch {
            changes.collect {
                trySend(Unit)
            }
        }

        channel.subscribe(blockUntilSubscribed = true)

        awaitClose {
            collectionJob.cancel()

            launch {
                client.realtime.removeChannel(channel)
            }
        }
    }

    suspend fun markConversationAsRead(
        conversationId: String
    ) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("No signed-in user.")

        require(conversationId.isNotBlank()) {
            "Conversation ID is required."
        }

        client
            .from("conversation_participants")
            .update(
                UpdateReadTimestampRequest(
                    lastReadAt = Instant.now().toString()
                )
            ) {
                filter {
                    eq("conversation_id", conversationId)
                    eq("profile_id", userId)
                }
            }
    }

    fun observeInsertedMessages(
        conversationId: String
    ): Flow<Unit> = callbackFlow {
        require(conversationId.isNotBlank()) {
            "Conversation ID is required."
        }

        client.realtime.connect()

        val channel = client.realtime.channel(
            channelId = "messages-$conversationId-${UUID.randomUUID()}"
        )

        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(
            schema = "public"
        ) {
            table = "messages"
        }

        val readReceiptChanges =
            channel.postgresChangeFlow<PostgresAction.Update>(
                schema = "public"
            ) {
                table = "conversation_participants"
            }

        val messageJob = launch {
            changes.collect {
                trySend(Unit)
            }
        }

        val readReceiptJob = launch {
            readReceiptChanges.collect {
                trySend(Unit)
            }
        }

        channel.subscribe(blockUntilSubscribed = true)

        awaitClose {
            messageJob.cancel()
            readReceiptJob.cancel()

            launch {
                client.realtime.removeChannel(channel)
            }
        }
    }

    suspend fun sendMessage(
        conversationId: String,
        body: String
    ) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("No signed-in user.")

        require(conversationId.isNotBlank()) {
            "Conversation ID is required."
        }

        require(body.isNotBlank()) {
            "Message cannot be empty."
        }

        client
            .from("messages")
            .insert(
                CreateMessageRequest(
                    conversationId = conversationId,
                    senderProfileId = userId,
                    body = body.trim()
                )
            )
    }

    suspend fun getMessages(
        conversationId: String,
        page: Int = 0,
        pageSize: Int = 50
    ): List<ChatMessage> {
        require(conversationId.isNotBlank()) {
            "Conversation ID is required."
        }

        val currentUserId = client.auth.currentUserOrNull()?.id
            ?: error("No signed-in user.")

        val messages = client
            .from("messages")
            .select {
                filter {
                    eq("conversation_id", conversationId)
                }

                order(
                    column = "created_at",
                    order = Order.DESCENDING
                )

                val from = page.toLong() * pageSize
                val to = from + pageSize - 1

                range(from..to)
            }
            .decodeList<ChatMessage>()

        if (messages.isEmpty()) {
            return emptyList()
        }

        val senderIds = messages
            .map { it.senderProfileId }
            .distinct()

        val senderProfiles = client
            .from("profiles")
            .select {
                filter {
                    isIn("id", senderIds)
                }
            }
            .decodeList<MessageSenderProfile>()

        val senderProfileById = senderProfiles.associateBy {
            it.id
        }

        val participantReadStates = client
            .from("conversation_participants")
            .select {
                filter {
                    eq("conversation_id", conversationId)
                    neq("profile_id", currentUserId)
                }
            }
            .decodeList<ParticipantReadStateRow>()

        return messages.asReversed().map { message ->
            val messageCreatedAt = runCatching {
                Instant.parse(message.createdAt)
            }.getOrNull()

            val isSeen = message.senderProfileId == currentUserId &&
                    messageCreatedAt != null &&
                    participantReadStates.any { participant ->
                        participant.lastReadAt
                            ?.let { timestamp ->
                                runCatching {
                                    Instant.parse(timestamp)
                                        .isAfter(messageCreatedAt)
                                }.getOrDefault(false)
                            }
                            ?: false
                    }

            val senderProfile = senderProfileById[message.senderProfileId]

            message.copy(
                senderName = senderProfile?.displayName
                    ?: "Unknown player",
                senderAvatarUrl = senderProfile?.avatarUrl,
                isSeen = isSeen
            )
        }
    }

    private val client = SupabaseProvider.client

    suspend fun getMyConversations(): List<Conversation> {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("No signed-in user.")

        val participantRows = client
            .from("conversation_participants")
            .select {
                filter {
                    eq("profile_id", userId)
                }
            }
            .decodeList<ConversationParticipantRow>()

        if (participantRows.isEmpty()) {
            return emptyList()
        }

        val conversationIds = participantRows
            .map { it.conversationId }
            .distinct()

        val conversations = client
            .from("conversations")
            .select {
                filter {
                    isIn("id", conversationIds)
                }
            }
            .decodeList<Conversation>()

        val allParticipantRows = client
            .from("conversation_participants")
            .select {
                filter {
                    isIn("conversation_id", conversationIds)
                }
            }
            .decodeList<DirectConversationParticipantRow>()

        val otherProfileIds = allParticipantRows
            .filter { participant ->
                participant.profileId != userId
            }
            .map { participant ->
                participant.profileId
            }
            .distinct()

        val otherProfiles = if (otherProfileIds.isEmpty()) {
            emptyList()
        } else {
            client
                .from("profiles")
                .select {
                    filter {
                        isIn("id", otherProfileIds)
                    }
                }
                .decodeList<MessageSenderProfile>()
        }

        val profileNameById = otherProfiles.associate {
            it.id to it.displayName
        }

        val otherParticipantByConversation = allParticipantRows
            .filter { participant ->
                participant.profileId != userId
            }
            .associateBy { participant ->
                participant.conversationId
            }

        val messages = client
            .from("messages")
            .select {
                filter {
                    isIn("conversation_id", conversationIds)

                }
            }
            .decodeList<UnreadMessageRow>()

        val participantByConversation = participantRows
            .associateBy { it.conversationId }

        return conversations
            .map { conversation ->
            val lastReadAt = participantByConversation[
                conversation.id
            ]?.lastReadAt?.let { timestamp ->
                runCatching {
                    Instant.parse(timestamp)
                }.getOrNull()
            }

            val unreadCount = messages.count { message ->
                when {
                    message.conversationId != conversation.id -> false

                    message.senderProfileId == userId -> false

                    lastReadAt == null -> true

                    else -> runCatching {
                        Instant.parse(message.createdAt)
                            .isAfter(lastReadAt)
                    }.getOrDefault(false)
                }
            }

            val latestMessage = messages
                .filter { message ->
                    message.conversationId == conversation.id
                }
                .maxByOrNull { message ->
                    Instant.parse(message.createdAt)
                }

                val displayTitle =
                    if (conversation.conversationType == "direct") {
                        val otherParticipant =
                            otherParticipantByConversation[conversation.id]

                        otherParticipant
                            ?.let { participant ->
                                profileNameById[participant.profileId]
                            }
                            ?: conversation.title
                    } else {
                        conversation.title
                    }

                conversation.copy(
                    title = displayTitle,
                    unreadCount = unreadCount,
                    latestMessage = latestMessage?.body,
                    latestMessageAt = latestMessage?.createdAt
                )
        }
            .sortedByDescending { conversation ->
                conversation.latestMessageAt
                    ?.let { timestamp ->
                        runCatching {
                            Instant.parse(timestamp)
                        }.getOrNull()
                    }
                    ?: runCatching {
                        Instant.parse(conversation.createdAt)
                    }.getOrNull()
            }
    }
}