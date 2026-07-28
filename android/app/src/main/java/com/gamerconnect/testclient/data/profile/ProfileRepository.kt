package com.gamerconnect.testclient.data.profile

import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class FriendshipStatus {
    NONE,
    OUTGOING_PENDING,
    INCOMING_PENDING,
    ACCEPTED,
    REJECTED
}

data class FriendshipState(
    val connectionId: String? = null,
    val status: FriendshipStatus = FriendshipStatus.NONE
)

data class PlayerSafetyState(
    val isBlockedByCurrentUser: Boolean = false
)

@Serializable
private data class ConnectionRow(
    val id: String,

    @SerialName("from_profile_id")
    val fromProfileId: String,

    @SerialName("to_profile_id")
    val toProfileId: String,

    val status: String
)

class ProfileRepository {

    private val client = SupabaseProvider.client

    suspend fun getCurrentProfile(): UserProfile {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("No signed-in user.")

        return getProfile(userId)
    }

    suspend fun getProfile(
        profileId: String
    ): UserProfile {
        require(profileId.isNotBlank()) {
            "Profile ID is required."
        }

        return client
            .from("profiles")
            .select {
                filter {
                    eq("id", profileId)
                }
            }
            .decodeSingle<UserProfile>()
    }
    suspend fun getDiscoveryProfiles(): List<UserProfile> {
        val currentUserId = client.auth.currentUserOrNull()?.id
            ?: error("No signed-in user.")

        return client
            .from("profiles")
            .select {
                filter {
                    neq("id", currentUserId)
                }

                limit(50)
            }
            .decodeList<UserProfile>()
    }

    suspend fun searchProfilesByDisplayName(
        query: String
    ): List<UserProfile> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return emptyList()
        }

        return client.postgrest.rpc(
            function = "search_player_profiles",
            parameters = buildJsonObject {
                put("search_text", cleanQuery)
            }
        ).decodeList()
    }

    suspend fun getFriendshipWith(
        profileId: String
    ): FriendshipState {
        val currentUserId = client.auth.currentUserOrNull()?.id
            ?: error("No signed-in user.")

        if (profileId.isBlank() || profileId == currentUserId) {
            return FriendshipState()
        }

        val connections = client
            .from("connections")
            .select()
            .decodeList<ConnectionRow>()

        val connection = connections.firstOrNull { row ->
            (row.fromProfileId == currentUserId && row.toProfileId == profileId) ||
                (row.fromProfileId == profileId && row.toProfileId == currentUserId)
        } ?: return FriendshipState()

        val status = when (connection.status) {
            "accepted" -> FriendshipStatus.ACCEPTED
            "pending" -> if (connection.fromProfileId == currentUserId) {
                FriendshipStatus.OUTGOING_PENDING
            } else {
                FriendshipStatus.INCOMING_PENDING
            }
            "rejected" -> FriendshipStatus.REJECTED
            else -> FriendshipStatus.NONE
        }

        return FriendshipState(
            connectionId = connection.id,
            status = status
        )
    }

    suspend fun sendFriendRequest(
        profileId: String
    ) {
        require(profileId.isNotBlank()) {
            "Player profile ID is required."
        }

        client.postgrest.rpc(
            function = "send_friend_request",
            parameters = buildJsonObject {
                put("target_profile_id", profileId)
            }
        )
    }

    suspend fun acceptFriendRequest(
        connectionId: String
    ) {
        respondFriendRequest(
            connectionId = connectionId,
            responseStatus = "accepted"
        )
    }

    suspend fun declineFriendRequest(
        connectionId: String
    ) {
        respondFriendRequest(
            connectionId = connectionId,
            responseStatus = "rejected"
        )
    }

    suspend fun removeFriend(
        profileId: String
    ) {
        require(profileId.isNotBlank()) {
            "Player profile ID is required."
        }

        client.postgrest.rpc(
            function = "remove_friendship",
            parameters = buildJsonObject {
                put("target_profile_id", profileId)
            }
        )
    }

    suspend fun getSafetyState(
        profileId: String
    ): PlayerSafetyState {
        val currentUserId = client.auth.currentUserOrNull()?.id
            ?: error("No signed-in user.")

        if (profileId.isBlank() || profileId == currentUserId) {
            return PlayerSafetyState()
        }

        val isBlockedByCurrentUser = client.postgrest.rpc(
            function = "has_blocked_profile",
            parameters = buildJsonObject {
                put("target_profile_id", profileId)
            }
        ).decodeAs<Boolean>()

        return PlayerSafetyState(
            isBlockedByCurrentUser = isBlockedByCurrentUser
        )
    }

    suspend fun blockPlayer(
        profileId: String
    ) {
        require(profileId.isNotBlank()) {
            "Player profile ID is required."
        }

        client.postgrest.rpc(
            function = "block_profile",
            parameters = buildJsonObject {
                put("target_profile_id", profileId)
            }
        )
    }

    suspend fun unblockPlayer(
        profileId: String
    ) {
        require(profileId.isNotBlank()) {
            "Player profile ID is required."
        }

        client.postgrest.rpc(
            function = "unblock_profile",
            parameters = buildJsonObject {
                put("target_profile_id", profileId)
            }
        )
    }

    suspend fun reportPlayer(
        profileId: String,
        reason: String,
        description: String
    ) {
        require(profileId.isNotBlank()) {
            "Player profile ID is required."
        }

        require(reason.isNotBlank()) {
            "Choose a report reason."
        }

        client.postgrest.rpc(
            function = "report_profile",
            parameters = buildJsonObject {
                put("target_profile_id", profileId)
                put("report_reason", reason)
                put("report_description", description)
            }
        )
    }

    private suspend fun respondFriendRequest(
        connectionId: String,
        responseStatus: String
    ) {
        require(connectionId.isNotBlank()) {
            "Friend request ID is required."
        }

        client.postgrest.rpc(
            function = "respond_friend_request",
            parameters = buildJsonObject {
                put("request_id", connectionId)
                put("response_status", responseStatus)
            }
        )
    }
}

