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
private data class FeedReactionRow(
    @SerialName("post_id")
    val postId: String,

    @SerialName("profile_id")
    val profileId: String,

    val reaction: String = "like"
)

@Serializable
private data class FeedConnectionRow(
    @SerialName("from_profile_id")
    val fromProfileId: String,

    @SerialName("to_profile_id")
    val toProfileId: String,

    val status: String
)

@Serializable
private data class CreateFeedReactionRequest(
    @SerialName("post_id")
    val postId: String,

    @SerialName("profile_id")
    val profileId: String,

    val reaction: String = "like"
)

@Serializable
private data class CreateFeedCommentRequest(
    val id: String,

    @SerialName("post_id")
    val postId: String,

    @SerialName("profile_id")
    val profileId: String,

    val body: String
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
private data class UpdateFeedPostRequest(
    val body: String
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

    fun getCurrentProfileId(): String? {
        return client.auth.currentUserOrNull()?.id
    }

    suspend fun getFeedPosts(
        filter: FeedFilter = FeedFilter.DISCOVER
    ): List<FeedPost> {
        val friendProfileIds = when (filter) {
            FeedFilter.DISCOVER -> emptyList()
            FeedFilter.FRIENDS -> getAcceptedFriendProfileIds()
        }

        val postsQuery = client
            .from("feed_posts")
            .select {
                filter {
                    when (filter) {
                        FeedFilter.DISCOVER -> Unit
                        FeedFilter.FRIENDS -> {
                            if (friendProfileIds.isEmpty()) {
                                isIn("profile_id", listOf(NO_MATCH_PROFILE_ID))
                            } else {
                                isIn("profile_id", friendProfileIds)
                            }
                        }
                    }
                }

                order(
                    column = "created_at",
                    order = Order.DESCENDING
                )

                limit(50)
            }

        return enrichPosts(postsQuery.decodeList())
    }

    suspend fun getFeedPost(
        postId: String
    ): FeedPost {
        require(postId.isNotBlank()) {
            "Post ID is required."
        }

        val post = client
            .from("feed_posts")
            .select {
                filter {
                    eq("id", postId)
                }
            }
            .decodeList<FeedPost>()
            .firstOrNull()
            ?: error("This post is no longer available.")

        return enrichPosts(listOf(post)).first()
    }

    suspend fun getOwnedFeedPost(
        postId: String
    ): FeedPost {
        val userId = requireCurrentProfileId()
        return getOwnedFeedPostRow(
            postId = postId,
            userId = userId
        ) ?: error("You can only manage your own posts.")
    }

    suspend fun updateFeedPost(
        postId: String,
        body: String
    ): Result<FeedPost> {
        return runCatching {
            require(postId.isNotBlank()) {
                "Post ID is required."
            }

            val userId = requireCurrentProfileId()
            val ownedPost = getOwnedFeedPostRow(
                postId = postId,
                userId = userId
            ) ?: error("You can only manage your own posts.")
            val trimmedBody = body.trim()

            require(trimmedBody.isNotBlank() || !ownedPost.mediaUrl.isNullOrBlank()) {
                "Add some text before saving."
            }

            client
                .from("feed_posts")
                .update(
                    UpdateFeedPostRequest(
                        body = trimmedBody
                    )
                ) {
                    filter {
                        eq("id", postId)
                        eq("profile_id", userId)
                    }
                }

            getFeedPost(postId)
        }
    }

    suspend fun deleteFeedPost(
        postId: String
    ): Result<Unit> {
        return runCatching {
            require(postId.isNotBlank()) {
                "Post ID is required."
            }

            val userId = requireCurrentProfileId()
            getOwnedFeedPostRow(
                postId = postId,
                userId = userId
            ) ?: error("You can only manage your own posts.")

            client
                .from("feed_posts")
                .delete {
                    filter {
                        eq("id", postId)
                        eq("profile_id", userId)
                    }
                }

            val stillExists = getOwnedFeedPostRow(
                postId = postId,
                userId = userId
            ) != null

            check(!stillExists) {
                "Couldn't delete post. Try again."
            }
        }
    }

    suspend fun addReaction(
        postId: String
    ) {
        require(postId.isNotBlank()) {
            "Post ID is required."
        }

        val userId = client.auth.currentUserOrNull()?.id
            ?: error("Sign in to react to posts.")

        runCatching {
            client
                .from("feed_reactions")
                .insert(
                    CreateFeedReactionRequest(
                        postId = postId,
                        profileId = userId
                    )
                )
        }.onFailure { error ->
            if (!isDuplicateReactionError(error)) {
                throw error
            }
        }
    }

    suspend fun removeReaction(
        postId: String
    ) {
        require(postId.isNotBlank()) {
            "Post ID is required."
        }

        val userId = client.auth.currentUserOrNull()?.id
            ?: error("Sign in to react to posts.")

        client
            .from("feed_reactions")
            .delete {
                filter {
                    eq("post_id", postId)
                    eq("profile_id", userId)
                    eq("reaction", FEED_REACTION_LIKE)
                }
            }
    }

    suspend fun getComments(
        postId: String
    ): List<FeedComment> {
        require(postId.isNotBlank()) {
            "Post ID is required."
        }

        val comments = client
            .from("feed_comments")
            .select {
                filter {
                    eq("post_id", postId)
                }

                order(
                    column = "created_at",
                    order = Order.ASCENDING
                )
            }
            .decodeList<FeedComment>()

        return enrichComments(comments)
    }

    suspend fun createComment(
        postId: String,
        body: String
    ): FeedComment {
        require(postId.isNotBlank()) {
            "Post ID is required."
        }

        val userId = client.auth.currentUserOrNull()?.id
            ?: error("Sign in to comment.")
        val trimmedBody = body.trim()

        require(trimmedBody.isNotBlank()) {
            "Add a comment before sending."
        }

        require(trimmedBody.length <= MAX_COMMENT_LENGTH) {
            "Comment is too long."
        }

        val comment = FeedComment(
            id = UUID.randomUUID().toString(),
            postId = postId,
            profileId = userId,
            body = trimmedBody,
            createdAt = ""
        )

        client
            .from("feed_comments")
            .insert(
                CreateFeedCommentRequest(
                    id = comment.id,
                    postId = postId,
                    profileId = userId,
                    body = trimmedBody
                )
            )

        return getComment(comment.id)
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

    private fun requireCurrentProfileId(): String {
        return client.auth.currentUserOrNull()?.id
            ?: error("Sign in to manage posts.")
    }

    private suspend fun getOwnedFeedPostRow(
        postId: String,
        userId: String
    ): FeedPost? {
        return client
            .from("feed_posts")
            .select {
                filter {
                    eq("id", postId)
                    eq("profile_id", userId)
                }
            }
            .decodeList<FeedPost>()
            .firstOrNull()
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

    private suspend fun getAcceptedFriendProfileIds(): List<String> {
        val currentUserId = client.auth.currentUserOrNull()?.id
            ?: error("Sign in to view posts from friends.")

        return client
            .from("connections")
            .select()
            .decodeList<FeedConnectionRow>()
            .filter { connection ->
                connection.status == CONNECTION_STATUS_ACCEPTED &&
                    (connection.fromProfileId == currentUserId ||
                        connection.toProfileId == currentUserId)
            }
            .map { connection ->
                if (connection.fromProfileId == currentUserId) {
                    connection.toProfileId
                } else {
                    connection.fromProfileId
                }
            }
            .distinct()
    }

    private suspend fun enrichPosts(
        posts: List<FeedPost>
    ): List<FeedPost> {
        val currentUserId = client.auth.currentUserOrNull()?.id
        val authorProfiles = getAuthorProfiles(posts)
        val reactionsByPostId = getReactionsByPostId(posts)
        val commentsByPostId = getCommentsByPostId(posts)

        return posts.map { post ->
            val authorProfile = authorProfiles[post.profileId]
            val reactions = reactionsByPostId[post.id].orEmpty()
            val comments = commentsByPostId[post.id].orEmpty()

            post.copy(
                resolvedMediaUrl = resolveMediaUrl(post.mediaUrl),
                authorDisplayName = authorProfile?.displayName,
                authorAvatarUrl = resolveAvatarUrl(authorProfile?.avatarUrl),
                reactionCount = reactions.size,
                isReactedByCurrentUser = currentUserId?.let { userId ->
                    reactions.any { reaction ->
                        reaction.profileId == userId && reaction.reaction == FEED_REACTION_LIKE
                    }
                } ?: false,
                isReactionPending = false,
                commentCount = comments.size
            )
        }
    }

    private suspend fun getReactionsByPostId(
        posts: List<FeedPost>
    ): Map<String, List<FeedReactionRow>> {
        val postIds = posts
            .map { post -> post.id }
            .distinct()

        if (postIds.isEmpty()) {
            return emptyMap()
        }

        return client
            .from("feed_reactions")
            .select {
                filter {
                    isIn("post_id", postIds)
                    eq("reaction", FEED_REACTION_LIKE)
                }
            }
            .decodeList<FeedReactionRow>()
            .groupBy { reaction -> reaction.postId }
    }

    private suspend fun getCommentsByPostId(
        posts: List<FeedPost>
    ): Map<String, List<FeedComment>> {
        val postIds = posts
            .map { post -> post.id }
            .distinct()

        if (postIds.isEmpty()) {
            return emptyMap()
        }

        return client
            .from("feed_comments")
            .select {
                filter {
                    isIn("post_id", postIds)
                }
            }
            .decodeList<FeedComment>()
            .groupBy { comment -> comment.postId }
    }

    private suspend fun getComment(
        commentId: String
    ): FeedComment {
        return enrichComments(
            client
                .from("feed_comments")
                .select {
                    filter {
                        eq("id", commentId)
                    }
                }
                .decodeList<FeedComment>()
        ).firstOrNull() ?: error("Comment was not found.")
    }

    private suspend fun enrichComments(
        comments: List<FeedComment>
    ): List<FeedComment> {
        val authorIds = comments
            .map { comment -> comment.profileId }
            .distinct()

        if (authorIds.isEmpty()) {
            return comments
        }

        val authorProfiles = client
            .from("profiles")
            .select {
                filter {
                    isIn("id", authorIds)
                }
            }
            .decodeList<FeedAuthorProfile>()
            .associateBy { profile -> profile.id }

        return comments.map { comment ->
            val authorProfile = authorProfiles[comment.profileId]

            comment.copy(
                authorDisplayName = authorProfile?.displayName,
                authorAvatarUrl = resolveAvatarUrl(authorProfile?.avatarUrl)
            )
        }
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
        const val MAX_COMMENT_LENGTH = 1000
        const val FEED_REACTION_LIKE = "like"
        const val CONNECTION_STATUS_ACCEPTED = "accepted"
        const val NO_MATCH_PROFILE_ID = "00000000-0000-0000-0000-000000000000"
    }
}

enum class FeedFilter {
    DISCOVER,
    FRIENDS
}

private fun isDuplicateReactionError(
    error: Throwable
): Boolean {
    val message = error.message.orEmpty().lowercase()

    return "duplicate" in message ||
        "23505" in message ||
        "feed_reactions_pkey" in message
}
