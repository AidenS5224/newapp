package com.gamerconnect.testclient.data.lfg

import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class LfgRepository {

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