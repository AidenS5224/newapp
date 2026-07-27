package com.gamerconnect.testclient.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val handle: String,
    @SerialName("display_name")
    val displayName: String,
    val age: Int? = null,
    val region: String = "",
    val timezone: String = "",
    val platforms: List<String> = emptyList(),
    @SerialName("top_games")
    val topGames: List<String> = emptyList(),
    val rank: String = "Unranked",
    @SerialName("play_style")
    val playStyle: List<String> = emptyList(),
    val bio: String = "",
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    val online: Boolean = false,
    @SerialName("created_at")
    val createdAt: String = ""
)
