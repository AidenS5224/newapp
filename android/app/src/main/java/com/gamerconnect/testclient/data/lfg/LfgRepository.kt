package com.gamerconnect.testclient.data.lfg

import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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


class LfgRepository {

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