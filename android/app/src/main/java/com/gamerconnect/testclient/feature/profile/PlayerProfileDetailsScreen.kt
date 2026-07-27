package com.gamerconnect.testclient.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.gamerconnect.testclient.data.profile.ProfileRepository
import com.gamerconnect.testclient.data.profile.UserProfile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PlayerProfileDetailsScreen(
    profileId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember {
        ProfileRepository()
    }

    var isLoading by remember(profileId) {
        mutableStateOf(true)
    }
    var profile by remember(profileId) {
        mutableStateOf<UserProfile?>(null)
    }
    var errorMessage by remember(profileId) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(profileId) {
        isLoading = true
        errorMessage = null

        runCatching {
            repository.getProfile(profileId)
        }.onSuccess { loadedProfile ->
            profile = loadedProfile
            isLoading = false
        }.onFailure { error ->
            errorMessage = error.message
                ?: "Unable to load this player profile."
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF111827)
                )
            ) {
                Text(
                    text = "Back",
                    color = Color.White
                )
            }

            Column {
                Text(
                    text = "Player Profile",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Public gamer details.",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp
                )
            }
        }

        when {
            isLoading -> {
                ProfileStatusCard {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Loading player profile...",
                        color = Color.White
                    )
                }
            }

            errorMessage != null -> {
                ProfileStatusCard {
                    Text(
                        text = "We couldn't load this player yet.",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = errorMessage ?: "Please try again shortly.",
                        color = Color(0xFFB8BFCC)
                    )
                }
            }

            profile == null -> {
                ProfileStatusCard {
                    Text(
                        text = "Player not found",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "This profile may no longer be available.",
                        color = Color(0xFFB8BFCC)
                    )
                }
            }

            else -> {
                val loadedProfile = profile
                if (loadedProfile != null) {
                    PlayerProfileContent(
                        profile = loadedProfile
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerProfileContent(
    profile: UserProfile
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                ProfileAvatar(
                    displayName = profile.displayName,
                    avatarUrl = profile.avatarUrl
                )

                Text(
                    text = profile.displayName.ifBlank {
                        "Unknown player"
                    },
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = profile.handle.takeIf { it.isNotBlank() }
                        ?.let { "@$it" }
                        ?: "Handle not set",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp
                )
            }
        }

        ProfileDetailsSection(
            title = "About",
            body = profile.bio.ifBlank {
                "No bio added yet."
            }
        )

        ProfileDetailsSection(
            title = "Region",
            body = profile.region.ifBlank {
                "Region not set."
            }
        )

        ProfileDetailsSection(
            title = "Platforms",
            body = profile.platforms
                .takeIf { it.isNotEmpty() }
                ?.joinToString("  |  ")
                ?: "No platforms added yet."
        )

        ProfileDetailsSection(
            title = "Games / Interests",
            body = profile.topGames
                .takeIf { it.isNotEmpty() }
                ?.joinToString("  |  ")
                ?: "No games added yet."
        )

        ProfileDetailsSection(
            title = "Play Style",
            body = profile.playStyle
                .takeIf { it.isNotEmpty() }
                ?.joinToString("  |  ")
                ?: "No play style added yet."
        )

        ProfileDetailsSection(
            title = "Joined",
            body = formatProfileCreatedDate(profile.createdAt)
        )
    }
}

@Composable
private fun ProfileAvatar(
    displayName: String,
    avatarUrl: String?
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(Color(0xFF25104B)),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "$displayName avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = displayName
                    .trim()
                    .firstOrNull()
                    ?.uppercase()
                    ?: "?",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ProfileStatusCard(
    content: @Composable ColumnScope.() -> Unit
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun ProfileDetailsSection(
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

private fun formatProfileCreatedDate(
    timestamp: String
): String {
    if (timestamp.isBlank()) {
        return "Join date not available."
    }

    return runCatching {
        Instant.parse(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }.getOrDefault(timestamp)
}
