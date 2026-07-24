package com.gamerconnect.testclient.data.feed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeedPost(
    val id: String,

    @SerialName("profile_id")
    val profileId: String,

    @SerialName("post_type")
    val postType: String = "post",

    @SerialName("game_id")
    val gameId: String? = null,

    val title: String,
    val body: String,

    @SerialName("media_url")
    val mediaUrl: String? = null,

    @SerialName("media_type")
    val mediaType: String? = null,

    val visibility: String = "public",

    @SerialName("created_at")
    val createdAt: String
)
