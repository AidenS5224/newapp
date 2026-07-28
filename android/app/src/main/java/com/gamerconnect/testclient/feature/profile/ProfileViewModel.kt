package com.gamerconnect.testclient.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamerconnect.testclient.data.profile.ProfileRepository
import com.gamerconnect.testclient.data.profile.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UserProfile? = null,
    val availableGames: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val errorMessage: String? = null
)

class ProfileViewModel(
    private val repository: ProfileRepository = ProfileRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
        loadAvailableGames()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            runCatching {
                repository.getCurrentProfile()
            }.onSuccess { profile ->
                _uiState.update {
                    it.copy(
                        profile = profile,
                        isLoading = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Unable to load profile."
                    )
                }
            }
        }
    }

    fun saveProfile(
        displayName: String,
        region: String,
        platforms: List<String>,
        games: List<String>,
        bio: String,
        onSaved: () -> Unit
    ) {
        if (_uiState.value.isSaving) {
            return
        }

        val cleanDisplayName = displayName.trim()
        val cleanRegion = region.trim()
        val cleanBio = bio.trim()
        val cleanPlatforms = platforms
            .map {
                it.trim()
            }
            .filter {
                it.isNotBlank()
            }
            .distinct()
        val cleanGames = games
            .map {
                it.trim()
            }
            .filter {
                it.isNotBlank()
            }
            .distinct()

        val validationError = when {
            cleanDisplayName.isBlank() -> "Display name is required."
            cleanRegion.isBlank() -> "Region is required."
            else -> null
        }

        if (validationError != null) {
            _uiState.update {
                it.copy(errorMessage = validationError)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null
                )
            }

            runCatching {
                repository.updateCurrentProfile(
                    displayName = cleanDisplayName,
                    region = cleanRegion,
                    platforms = cleanPlatforms,
                    topGames = cleanGames,
                    bio = cleanBio
                )
            }.onSuccess { profile ->
                _uiState.update {
                    it.copy(
                        profile = profile,
                        isSaving = false,
                        errorMessage = null
                    )
                }
                onSaved()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "Unable to save profile. Please try again."
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(errorMessage = null)
        }
    }

    fun showError(
        message: String
    ) {
        _uiState.update {
            it.copy(errorMessage = message)
        }
    }

    fun uploadAvatar(
        bytes: ByteArray,
        mimeType: String
    ) {
        if (_uiState.value.isUploadingAvatar) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploadingAvatar = true,
                    errorMessage = null
                )
            }

            runCatching {
                repository.uploadCurrentAvatar(
                    bytes = bytes,
                    mimeType = mimeType
                )
            }.onSuccess { profile ->
                _uiState.update {
                    it.copy(
                        profile = profile,
                        isUploadingAvatar = false,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isUploadingAvatar = false,
                        errorMessage = error.message ?: "Unable to upload avatar. Please try again."
                    )
                }
            }
        }
    }

    fun removeAvatar() {
        if (_uiState.value.isUploadingAvatar) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploadingAvatar = true,
                    errorMessage = null
                )
            }

            runCatching {
                repository.removeCurrentAvatar()
            }.onSuccess { profile ->
                _uiState.update {
                    it.copy(
                        profile = profile,
                        isUploadingAvatar = false,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isUploadingAvatar = false,
                        errorMessage = error.message ?: "Unable to remove avatar. Please try again."
                    )
                }
            }
        }
    }

    private fun loadAvailableGames() {
        viewModelScope.launch {
            runCatching {
                repository.getAvailableGameNames()
            }.onSuccess { games ->
                _uiState.update {
                    it.copy(
                        availableGames = games
                    )
                }
            }
        }
    }
}


