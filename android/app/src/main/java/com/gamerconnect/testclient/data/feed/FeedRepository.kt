package com.gamerconnect.testclient.data.feed

import android.util.Log
import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import io.ktor.http.ContentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
private data class FeedAuthorProfile(
    val id: String,

    @SerialName("display_name")
    val displayName: String,

    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

@Serializable
private data class CreateFeedPostRequest(
    @SerialName("profile_id")
    val profileId: String,

    @SerialName("post_type")
    val postType: String = "post",

    val title: String,

    val body: String,

    @SerialName("game_id")
    val gameId: String? = null,

    @SerialName("media_url")
    val mediaUrl: String? = null,

    @SerialName("media_type")
    val mediaType: String? = null,

    val visibility: String = "public"
)

@Serializable
data class FeedGame(
    val id: String,
    val name: String
)

@Serializable
private data class CurrentProfileGamesRow(
    @SerialName("top_games")
    val topGames: List<String> = emptyList()
)

data class FeedImageUpload(
    val bytes: ByteArray,
    val mimeType: String,
    val extension: String
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

    suspend fun createPost(
        title: String,
        text: String,
        gameId: String?,
        image: FeedImageUpload?
    ) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("Sign in before creating a post.")
        val trimmedTitle = title.trim()
        val trimmedText = text.trim()

        require(trimmedTitle.isNotBlank()) {
            "Add a title before posting."
        }

        require(trimmedText.isNotBlank() || image != null) {
            "Add some text or an image before posting."
        }

        var uploadedObjectPath: String? = null

        try {
            val uploadedMediaUrl = image?.let { upload ->
                val objectPath = "$userId/${UUID.randomUUID()}.${upload.extension}"
                client
                    .storage
                    .from(FEED_MEDIA_BUCKET)
                    .upload(
                        path = objectPath,
                        data = upload.bytes
                    ) {
                        upsert = false
                        contentType = ContentType.parse(upload.mimeType)
                    }

                uploadedObjectPath = objectPath

                client
                    .storage
                    .from(FEED_MEDIA_BUCKET)
                    .publicUrl(objectPath)
            }

            client
                .from("feed_posts")
                .insert(
                    CreateFeedPostRequest(
                        profileId = userId,
                        title = trimmedTitle.take(MAX_TITLE_LENGTH),
                        body = trimmedText,
                        gameId = gameId,
                        mediaUrl = uploadedMediaUrl,
                        mediaType = image?.let { "image" }
                    )
                )
        } catch (error: Throwable) {
            uploadedObjectPath?.let { objectPath ->
                runCatching {
                    client
                        .storage
                        .from(FEED_MEDIA_BUCKET)
                        .delete(listOf(objectPath))
                }.onFailure { cleanupError ->
                    Log.w(
                        "FeedRepository",
                        "Unable to clean up failed feed media upload.",
                        cleanupError
                    )
                }
            }

            Log.w(
                "FeedRepository",
                "Unable to create feed post.",
                error
            )
            throw error
        }
    }

    suspend fun getCurrentProfileGames(): List<FeedGame> {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("Sign in before creating a post.")

        val profileGames = client
            .from("profiles")
            .select {
                filter {
                    eq("id", userId)
                }
            }
            .decodeSingle<CurrentProfileGamesRow>()
            .topGames
            .map { game -> game.trim() }
            .filter { game -> game.isNotBlank() }
            .distinctBy { game -> game.lowercase() }

        if (profileGames.isEmpty()) {
            return emptyList()
        }

        val gameRows = client
            .from("games")
            .select {
                order(
                    column = "name",
                    order = Order.ASCENDING
                )
            }
            .decodeList<FeedGame>()

        val gameByName = gameRows.associateBy { game ->
            game.name.lowercase()
        }

        return profileGames.mapNotNull { gameName ->
            gameByName[gameName.lowercase()]
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
        const val MAX_TITLE_LENGTH = 80
    }
}
