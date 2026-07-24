package com.gamerconnect.testclient.data.feed

import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

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
    }
}

