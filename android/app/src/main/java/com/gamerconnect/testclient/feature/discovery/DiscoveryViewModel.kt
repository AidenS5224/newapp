package com.gamerconnect.testclient.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamerconnect.testclient.data.profile.ProfileRepository
import com.gamerconnect.testclient.data.profile.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoveryUiState(
    val profiles: List<UserProfile> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class DiscoveryViewModel(
    private val repository: ProfileRepository = ProfileRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
    }

    fun loadProfiles() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            runCatching {
                repository.getDiscoveryProfiles()
            }.onSuccess { profiles ->
                _uiState.update {
                    it.copy(
                        profiles = profiles,
                        currentIndex = 0,
                        isLoading = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message
                            ?: "Unable to load discovery profiles."
                    )
                }
            }
        }
    }

    fun nextProfile() {
        _uiState.update { state ->
            if (state.profiles.isEmpty()) {
                state
            } else {
                state.copy(
                    currentIndex = (state.currentIndex + 1) % state.profiles.size
                )
            }
        }
    }
}