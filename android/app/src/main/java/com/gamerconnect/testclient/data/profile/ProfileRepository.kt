package com.gamerconnect.testclient.data.profile

import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from

class ProfileRepository {

    private val client = SupabaseProvider.client

    suspend fun getCurrentProfile(): UserProfile {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("No signed-in user.")

        return client
            .from("profiles")
            .select {
                filter {
                    eq("id", userId)
                }
            }
            .decodeSingle<UserProfile>()
    }
}

