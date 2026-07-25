package com.gamerconnect.testclient.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamerconnect.testclient.data.auth.AuthRepository
import com.gamerconnect.testclient.data.supabase.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isInitialized: Boolean = false,
    val isSignedIn: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())

    val uiState: StateFlow<AuthUiState> =
        _uiState.asStateFlow()

    init {
        observeSession()
    }

    private fun observeSession() {
        viewModelScope.launch {
            SupabaseProvider.client.auth.sessionStatus.collect { status ->
                when (status) {
                    SessionStatus.Initializing -> {
                        _uiState.update {
                            it.copy(isInitialized = false)
                        }
                    }

                    is SessionStatus.Authenticated -> {
                        _uiState.update {
                            it.copy(
                                isInitialized = true,
                                isSignedIn = true,
                                isLoading = false,
                                errorMessage = null
                            )
                        }
                    }

                    is SessionStatus.NotAuthenticated -> {
                        _uiState.update {
                            it.copy(
                                isInitialized = true,
                                isSignedIn = false,
                                isLoading = false
                            )
                        }
                    }

                    is SessionStatus.RefreshFailure -> {
                        _uiState.update {
                            it.copy(
                                isInitialized = true,
                                isSignedIn = false,
                                isLoading = false,
                                errorMessage =
                                    "Your session expired. Please sign in again."
                            )
                        }
                    }
                }
            }
        }
    }

    fun signIn(
        email: String,
        password: String
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            runCatching {
                repository.signIn(email, password)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage =
                            error.message ?: "Unable to sign in."
                    )
                }
            }
        }
    }

    fun signUp(
        email: String,
        password: String
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            runCatching {
                repository.signUp(email, password)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage =
                            error.message ?: "Unable to create account."
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching {
                repository.signOut()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        errorMessage =
                            error.message ?: "Unable to sign out."
                    )
                }
            }
        }
    }
}