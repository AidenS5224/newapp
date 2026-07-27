package com.gamerconnect.testclient.data.lfg

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LfgPost(
    val id: String,

    @SerialName("profile_id")
    val profileId: String,

    @SerialName("game_id")
    val gameId: String? = null,

    val gameTitle: String = "",

    val ownerDisplayName: String = "",

    val title: String,
    val mode: String,

    @SerialName("rank_range")
    val rankRange: String = "",

    @SerialName("party_size")
    val partySize: String = "",

    @SerialName("starts_at")
    val startsAt: String = "",

    val status: String = "open",

    @SerialName("created_at")
    val createdAt: String
)
