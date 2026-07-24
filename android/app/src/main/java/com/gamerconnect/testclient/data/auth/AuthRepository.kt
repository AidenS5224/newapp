package com.gamerconnect.testclient.data.auth

import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

class AuthRepository {

    private val auth = SupabaseProvider.client.auth

    fun isSignedIn(): Boolean {
        return auth.currentUserOrNull() != null
    }

    suspend fun signIn(
        email: String,
        password: String
    ) {
        require(email.isNotBlank()) {
            "Email is required."
        }

        require(password.isNotBlank()) {
            "Password is required."
        }

        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signUp(
        email: String,
        password: String
    ) {
        require(email.isNotBlank()) {
            "Email is required."
        }

        require(password.length >= 8) {
            "Password must contain at least 8 characters."
        }

        auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }
}
