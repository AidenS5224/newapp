package com.gamerconnect.testclient.data.messages

import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class ConversationParticipantRow(
    @SerialName("conversation_id")
    val conversationId: String
)

class MessagesRepository {

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