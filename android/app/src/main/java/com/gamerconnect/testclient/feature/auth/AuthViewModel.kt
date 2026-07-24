package com.gamerconnect.testclient.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamerconnect.testclient.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isSignedIn: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isSignedIn = repository.isSignedIn()
        )
    )

    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

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
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSignedIn = true,
                        isLoading = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Unable to sign in."
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
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSignedIn = repository.isSignedIn(),
                        isLoading = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Unable to create account."
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching {
                repository.signOut()
            }

            _uiState.value = AuthUiState(
                isSignedIn = false
            )
        }
    }
}

