package com.gamerconnect.testclient.feature.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun DiscoveryScreen(
    modifier: Modifier = Modifier,
    discoveryViewModel: DiscoveryViewModel = viewModel()
) {
    val uiState = discoveryViewModel.uiState.collectAsStateWithLifecycle().value
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Discovery / LFG",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DiscoveryTab(
                title = "Looking For Group",
                selected = true
            )

            DiscoveryTab(
                title = "Matches",
                selected = false
            )

            DiscoveryTab(
                title = "My Posts",
                selected = false
            )
        }

        when {
            uiState.isLoading -> {
                Text(
                    text = "Loading players...",
                    color = Color.White
                )
            }

            uiState.errorMessage != null -> {
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }

            uiState.profiles.isEmpty() -> {
                Text(
                    text = "No players found.",
                    color = Color(0xFF9CA3AF)
                )
            }

            else -> {
                val profile = uiState.profiles[uiState.currentIndex]

                PlayerCard(
                    profile = profile,
                    onPass = discoveryViewModel::nextProfile,
                    onPlay = discoveryViewModel::nextProfile
                )
            }
        }
    }
}

@Composable
private fun DiscoveryTab(
    title: String,
    selected: Boolean
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            }
        )
    ) {
        Text(
            text = title,
            color = if (selected) Color.White else Color(0xFFB8BFCC),
            fontSize = 12.sp,
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp
            )
        )
    }
}

@Composable
private fun PlayerCard(
    profile: UserProfile,
    onPass: () -> Unit,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1220)
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(Color(0xFF111A2B)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.displayName
                        .firstOrNull()
                        ?.uppercase()
                        ?: "?",
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = profile.displayName,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = buildString {
                        profile.age?.let {
                            append(it)
                            append(" · ")
                        }

                        append(
                            profile.region.ifBlank {
                                "Region not set"
                            }
                        )
                    },
                    color = Color(0xFF8D94A3),
                    fontSize = 14.sp
                )

                Text(
                    text = "Looking for",
                    color = Color(0xFF8D94A3),
                    fontSize = 12.sp
                )

                Text(
                    text = profile.topGames
                        .firstOrNull()
                        ?: "No game selected",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = listOf(
                        profile.rank,
                        profile.playStyle.joinToString(", ")
                    )
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )

                if (profile.bio.isNotBlank()) {
                    Text(
                        text = profile.bio,
                        color = Color(0xFFB8BFCC),
                        fontSize = 14.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onPass,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF151B26)
                    )
                ) {
                    Text(
                        text = "Pass",
                        color = Color.White
                    )
                }

                Button(
                    onClick = onPlay,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Play",
                        color = Color.White
                    )
                }
            }
        }
    }
}
