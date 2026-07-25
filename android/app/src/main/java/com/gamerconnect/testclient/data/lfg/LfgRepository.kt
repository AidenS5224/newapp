package com.gamerconnect.testclient.data.lfg

import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class CreateLfgPostRequest(
    @SerialName("profile_id")
    val profileId: String,

    val title: String,
    val mode: String,

    @SerialName("rank_range")
    val rankRange: String,

    @SerialName("party_size")
    val partySize: String,

    @SerialName("starts_at")
    val startsAt: String,

    val status: String = "open"
)

@Serializable
private data class CreateLfgJoinRequest(
    @SerialName("lfg_post_id")
    val lfgPostId: String,

    @SerialName("requester_profile_id")
    val requesterProfileId: String,

    val status: String = "pending"
)

class LfgRepository {

    suspend fun acceptJoinRequest(
        requestId: String
    ) {
        require(requestId.isNotBlank()) {
            "Request ID is required."
        }

        client
            .from("lfg_join_requests")
            .update(
                buildJsonObject {
                    put("status", "accepted")
                }
            ) {
                filter {
                    eq("id", requestId)
                }
            }
    }

    suspend fun rejectJoinRequest(
        requestId: String
    ) {
        require(requestId.isNotBlank()) {
            "Request ID is required."
        }

        client
            .from("lfg_join_requests")
            .update(
                buildJsonObject {
                    put("status", "rejected")
                }
            ) {
                filter {
                    eq("id", requestId)
                }
            }
    }

    suspend fun getPendingRequestsForOwnedPosts(): List<LfgJoinRequest> {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("No signed-in user.")

        val ownedPosts = client
            .from("lfg_posts")
            .select {
                filter {
                    eq("profile_id", userId)
                }
            }
            .decodeList<LfgPost>()

        if (ownedPosts.isEmpty()) {
            return emptyList()
        }

        val ownedPostIds = ownedPosts.map { it.id }

        return client
            .from("lfg_join_requests")
            .select {
                filter {
                    isIn("lfg_post_id", ownedPostIds)
                    eq("status", "pending")
                }

                order(
                    column = "created_at",
                    order = Order.ASCENDING
                )
            }
            .decodeList<LfgJoinRequest>()
    }

    suspend fun requestToJoin(
        lfgPostId: String
    ) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("No signed-in user.")

        require(lfgPostId.isNotBlank()) {
            "LFG post ID is required."
        }

        val existingRequests = client
            .from("lfg_join_requests")
            .select {
                filter {
                    eq("lfg_post_id", lfgPostId)
                    eq("requester_profile_id", userId)
                }

                limit(1)
            }
            .decodeList<LfgJoinRequest>()

        if (existingRequests.isNotEmpty()) {
            return
        }

        client
            .from("lfg_join_requests")
            .insert(
                CreateLfgJoinRequest(
                    lfgPostId = lfgPostId,
                    requesterProfileId = userId
                )
            )
    }

    suspend fun createLfgPost(
        title: String,
        mode: String,
        rankRange: String,
        partySize: String,
        startsAt: String
    ) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("No signed-in user.")

        require(title.isNotBlank()) {
            "Title is required."
        }

        require(mode.isNotBlank()) {
            "Mode is required."
        }

        client
            .from("lfg_posts")
            .insert(
                CreateLfgPostRequest(
                    profileId = userId,
                    title = title.trim(),
                    mode = mode.trim(),
                    rankRange = rankRange.trim(),
                    partySize = partySize.trim(),
                    startsAt = startsAt.trim()
                )
            )
    }

    private val client = SupabaseProvider.client

    suspend fun getOpenLfgPosts(): List<LfgPost> {
        return client
            .from("lfg_posts")
            .select {
                filter {
                    eq("status", "open")
                }

                order(
                    column = "created_at",
                    order = Order.DESCENDING
                )

                limit(50)
            }
            .decodeList<LfgPost>()
    }
}