package com.gamerconnect.testclient.data.feed

import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage

class FeedRepository {

    private val client = SupabaseProvider.client

    suspend fun getFeedPosts(): List<FeedPost> {
        return client
            .from("feed_posts")
            .select {
                order(
                    column = "created_at",
                    order = Order.DESCENDING
                )

                limit(50)
            }
            .decodeList<FeedPost>()
            .map { post ->
                post.copy(
                    resolvedMediaUrl = resolveMediaUrl(post.mediaUrl)
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

    private companion object {
        const val FEED_MEDIA_BUCKET = "feed-media"
    }
}

