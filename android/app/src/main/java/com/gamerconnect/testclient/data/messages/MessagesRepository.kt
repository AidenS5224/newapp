package com.gamerconnect.testclient.data.messages

import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.jan.supabase.postgrest.query.Order


@Serializable
private data class ConversationParticipantRow(
    @SerialName("conversation_id")
    val conversationId: String
)

@Serializable
private data class CreateMessageRequest(
    @SerialName("conversation_id")
    val conversationId: String,

    @SerialName("sender_profile_id")
    val senderProfileId: String,

    val body: String
)

class MessagesRepository {

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
        conversationId: String
    ): List<ChatMessage> {
        require(conversationId.isNotBlank()) {
            "Conversation ID is required."
        }

        return client
            .from("messages")
            .select {
                filter {
                    eq("conversation_id", conversationId)
                }

                order(
                    column = "created_at",
                    order = Order.ASCENDING
                )

                limit(100)
            }
            .decodeList<ChatMessage>()
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

        return client
            .from("conversations")
            .select {
                filter {
                    isIn("id", conversationIds)
                }
            }
            .decodeList<Conversation>()
    }
}