package com.gamerconnect.testclient.data.feed

import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class FeedAuthorProfile(
    val id: String,

    @SerialName("display_name")
    val displayName: String,

    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

class FeedRepository {

    private val client = SupabaseProvider.client

    suspend fun getFeedPosts(): List<FeedPost> {
        val posts = client
            .from("feed_posts")
            .select {
                order(
                    column = "created_at",
                    order = Order.DESCENDING
                )

                limit(50)
            }
            .decodeList<FeedPost>()

        val authorProfiles = getAuthorProfiles(posts)

        return posts
            .map { post ->
                val authorProfile = authorProfiles[post.profileId]

                post.copy(
                    resolvedMediaUrl = resolveMediaUrl(post.mediaUrl),
                    authorDisplayName = authorProfile?.displayName,
                    authorAvatarUrl = resolveAvatarUrl(authorProfile?.avatarUrl)
                )
            }
    }

    private suspend fun getAuthorProfiles(
        posts: List<FeedPost>
    ): Map<String, FeedAuthorProfile> {
        val authorProfileIds = posts
            .map { post -> post.profileId }
            .distinct()

        if (authorProfileIds.isEmpty()) {
            return emptyMap()
        }

        return client
            .from("profiles")
            .select {
                filter {
                    isIn("id", authorProfileIds)
                }
            }
            .decodeList<FeedAuthorProfile>()
            .associateBy { profile -> profile.id }
    }

    private fun resolveMediaUrl(mediaUrl: String?): String? {
        val trimmedMediaUrl = mediaUrl?.trim()

        if (trimmedMediaUrl.isNullOrBlank()) {
            return null
        }

        if (
            trimmedMediaUrl.startsWith("http://", ignoreCase = true) ||
            trimmedMediaUrl.startsWith("https://", ignoreCase = true)
        ) {
            return trimmedMediaUrl
        }

        return client
            .storage
            .from(FEED_MEDIA_BUCKET)
            .publicUrl(trimmedMediaUrl)
    }

    private fun resolveAvatarUrl(avatarUrl: String?): String? {
        val trimmedAvatarUrl = avatarUrl?.trim()

        if (trimmedAvatarUrl.isNullOrBlank()) {
            return null
        }

        if (
            trimmedAvatarUrl.startsWith("http://", ignoreCase = true) ||
            trimmedAvatarUrl.startsWith("https://", ignoreCase = true)
        ) {
            return trimmedAvatarUrl
        }

        return client
            .storage
            .from(PROFILE_AVATARS_BUCKET)
            .publicUrl(trimmedAvatarUrl)
    }

    private companion object {
        const val FEED_MEDIA_BUCKET = "feed-media"
        const val PROFILE_AVATARS_BUCKET = "profile-avatars"
    }
}
