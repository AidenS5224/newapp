package com.gamerconnect.testclient.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamerconnect.testclient.data.profile.UserProfile

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onPlayerProfileClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val uiState = profileViewModel.uiState.collectAsStateWithLifecycle().value
    var isEditing by rememberSaveable {
        mutableStateOf(false)
    }

    when {
        uiState.isLoading -> {
            Text(
                text = "Loading profile...",
                color = Color.White,
                modifier = modifier.padding(24.dp)
            )
        }

        uiState.errorMessage != null && uiState.profile == null -> {
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = modifier.padding(24.dp)
            )
        }

        uiState.profile != null -> {
            val profile = uiState.profile

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Profile",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                ProfileHeader(
                    profile = profile,
                    isEditing = isEditing,
                    onEdit = {
                        profileViewModel.clearError()
                        isEditing = true
                    },
                    onSignOut = onSignOut
                )

                if (isEditing) {
                    ProfileEditForm(
                        profile = profile,
                        availableGames = uiState.availableGames,
                        isSaving = uiState.isSaving,
                        errorMessage = uiState.errorMessage,
                        onCancel = {
                            profileViewModel.clearError()
                            isEditing = false
                        },
                        onSave = { displayName, region, platforms, games, bio ->
                            profileViewModel.saveProfile(
                                displayName = displayName,
                                region = region,
                                platforms = platforms,
                                games = games,
                                bio = bio,
                                onSaved = {
                                    isEditing = false
                                }
                            )
                        }
                    )
                } else {
                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            onPlayerProfileClick(profile.id)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "View public profile",
                            color = Color.White
                        )
                    }

                    ProfileSection(
                        title = "About",
                        body = profile.bio.ifBlank {
                            "No biography added yet."
                        }
                    )

                    ProfileSection(
                        title = "Region",
                        body = profile.region.ifBlank {
                            "No region added yet."
                        }
                    )

                    ProfileSection(
                        title = "Platforms",
                        body = profile.platforms
                            .takeIf {
                                it.isNotEmpty()
                            }
                            ?.joinToString(", ")
                            ?: "No platforms added yet."
                    )

                    ProfileSection(
                        title = "Games",
                        body = profile.topGames
                            .takeIf {
                                it.isNotEmpty()
                            }
                            ?.joinToString(", ")
                            ?: "No games added yet."
                    )

                    ProfileSection(
                        title = "Availability",
                        body = profile.timezone.ifBlank {
                            "Availability not set."
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: UserProfile,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onSignOut: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1220)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF25104B)
                )
            ) {
                Text(
                    text = profile.displayName
                        .firstOrNull()
                        ?.uppercase()
                        ?: "G",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(24.dp)
                )
            }

            Text(
                text = profile.displayName.ifBlank {
                    "Gamer"
                },
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "@${profile.handle}",
                color = Color(0xFF9CA3AF),
                fontSize = 14.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProfileStat("12", "Connections")
                ProfileStat("4", "Groups")
                ProfileStat("18", "Posts")
            }

            Button(
                onClick = onEdit,
                enabled = !isEditing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Edit Profile",
                    color = Color.White
                )
            }

            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A0F1A)
                )
            ) {
                Text(
                    text = "Sign Out",
                    color = Color(0xFFFF8A8A)
                )
            }
        }
    }
}

@Composable
private fun ProfileEditForm(
    profile: UserProfile,
    availableGames: List<String>,
    isSaving: Boolean,
    errorMessage: String?,
    onCancel: () -> Unit,
    onSave: (
        displayName: String,
        region: String,
        platforms: List<String>,
        games: List<String>,
        bio: String
    ) -> Unit
) {
    var displayName by rememberSaveable {
        mutableStateOf(profile.displayName)
    }
    var region by rememberSaveable {
        mutableStateOf(profile.region)
    }
    var selectedPlatforms by rememberSaveable {
        mutableStateOf(profile.platforms)
    }
    var selectedGames by rememberSaveable {
        mutableStateOf(profile.topGames)
    }
    var gameSearch by rememberSaveable {
        mutableStateOf("")
    }
    var bio by rememberSaveable {
        mutableStateOf(profile.bio)
    }

    LaunchedEffect(profile.id) {
        displayName = profile.displayName
        region = profile.region
        selectedPlatforms = profile.platforms
        selectedGames = profile.topGames
        gameSearch = ""
        bio = profile.bio
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1220)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Edit Profile",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Update the public details other players see.",
                color = Color(0xFF9CA3AF),
                fontSize = 14.sp
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }

            ProfileTextField(
                value = displayName,
                onValueChange = {
                    displayName = it
                },
                label = "Display name",
                singleLine = true
            )

            ProfileTextField(
                value = region,
                onValueChange = {
                    region = it
                },
                label = "Region",
                singleLine = true
            )

            PlatformMultiSelect(
                selectedPlatforms = selectedPlatforms,
                onTogglePlatform = { platform ->
                    selectedPlatforms = if (selectedPlatforms.contains(platform)) {
                        selectedPlatforms.filterNot {
                            it == platform
                        }
                    } else {
                        selectedPlatforms + platform
                    }
                },
                onRemovePlatform = { platform ->
                    selectedPlatforms = selectedPlatforms.filterNot {
                        it == platform
                    }
                }
            )

            GameMultiSelect(
                availableGames = availableGames,
                selectedGames = selectedGames,
                searchText = gameSearch,
                onSearchTextChange = {
                    gameSearch = it
                },
                onToggleGame = { game ->
                    selectedGames = if (selectedGames.contains(game)) {
                        selectedGames.filterNot {
                            it == game
                        }
                    } else {
                        selectedGames + game
                    }
                },
                onRemoveGame = { game ->
                    selectedGames = selectedGames.filterNot {
                        it == game
                    }
                }
            )

            ProfileTextField(
                value = bio,
                onValueChange = {
                    bio = it
                },
                label = "Bio",
                minLines = 4
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Cancel",
                        color = Color.White
                    )
                }

                Button(
                    onClick = {
                        onSave(
                            displayName,
                            region,
                            selectedPlatforms,
                            selectedGames,
                            bio
                        )
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF159A52)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isSaving) {
                            "Saving..."
                        } else {
                            "Save"
                        },
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(label)
            },
            singleLine = singleLine,
            minLines = minLines,
            modifier = Modifier.fillMaxWidth()
        )

        if (supportingText != null) {
            Text(
                text = supportingText,
                color = Color(0xFF8D94A3),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PlatformMultiSelect(
    selectedPlatforms: List<String>,
    onTogglePlatform: (String) -> Unit,
    onRemovePlatform: (String) -> Unit
) {
    val platformOptions = listOf(
        "PC",
        "PlayStation",
        "Xbox",
        "Nintendo Switch",
        "Mobile"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Platforms",
            color = Color(0xFFB8BFCC),
            fontSize = 12.sp
        )

        if (selectedPlatforms.isEmpty()) {
            Text(
                text = "No platforms selected yet.",
                color = Color(0xFF8D94A3),
                fontSize = 12.sp
            )
        } else {
            selectedPlatforms.forEach { platform ->
                OutlinedButton(
                    onClick = {
                        onRemovePlatform(platform)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "$platform  Remove",
                        color = Color.White
                    )
                }
            }
        }

        platformOptions.forEach { platform ->
            val isSelected = selectedPlatforms.contains(platform)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = platform,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                Checkbox(
                    checked = isSelected,
                    onCheckedChange = {
                        onTogglePlatform(platform)
                    }
                )
            }
        }
    }
}

@Composable
private fun GameMultiSelect(
    availableGames: List<String>,
    selectedGames: List<String>,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onToggleGame: (String) -> Unit,
    onRemoveGame: (String) -> Unit
) {
    val fallbackGames = listOf(
        "Apex Legends",
        "Call of Duty: Warzone",
        "Counter-Strike 2",
        "Fortnite",
        "Minecraft",
        "Overwatch 2",
        "Rainbow Six Siege",
        "Rocket League",
        "Valorant"
    )
    val gameOptions = (availableGames + selectedGames + fallbackGames)
        .distinct()
        .sorted()
    val filteredGames = gameOptions
        .filter { game ->
            searchText.isBlank() || game.contains(
                other = searchText,
                ignoreCase = true
            )
        }
        .take(8)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Games / interests",
            color = Color(0xFFB8BFCC),
            fontSize = 12.sp
        )

        if (selectedGames.isEmpty()) {
            Text(
                text = "No games selected yet.",
                color = Color(0xFF8D94A3),
                fontSize = 12.sp
            )
        } else {
            selectedGames.forEach { game ->
                OutlinedButton(
                    onClick = {
                        onRemoveGame(game)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "$game  Remove",
                        color = Color.White
                    )
                }
            }
        }

        ProfileTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            label = "Search games",
            singleLine = true
        )

        if (filteredGames.isEmpty()) {
            Text(
                text = "No matching games found.",
                color = Color(0xFF8D94A3),
                fontSize = 12.sp
            )
        } else {
            filteredGames.forEach { game ->
                val isSelected = selectedGames.contains(game)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = game,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = {
                            onToggleGame(game)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = label,
            color = Color(0xFF8D94A3),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ProfileSection(
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1220)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = body,
                color = Color(0xFFB8BFCC)
            )
        }
    }
}
