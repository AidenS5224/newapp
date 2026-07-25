package com.gamerconnect.testclient.data.lfg

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LfgJoinRequest(
    val id: String,

    @SerialName("lfg_post_id")
    val lfgPostId: String,

    @SerialName("requester_profile_id")
    val requesterProfileId: String,

    val status: String = "pending",

    @SerialName("created_at")
    val createdAt: String
)

