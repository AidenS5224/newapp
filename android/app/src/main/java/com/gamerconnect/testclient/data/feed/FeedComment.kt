package com.gamerconnect.testclient.data.feed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeedComment(
    val id: String,

    @SerialName("post_id")
    val postId: String,

    @SerialName("profile_id")
    val profileId: String,

    val body: String,

    @SerialName("created_at")
    val createdAt: String,

    @kotlinx.serialization.Transient
    val authorDisplayName: String? = null,

    @kotlinx.serialization.Transient
    val authorAvatarUrl: String? = null
)
