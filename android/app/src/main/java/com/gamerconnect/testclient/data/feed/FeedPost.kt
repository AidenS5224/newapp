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
    val createdAt: String,

    @kotlinx.serialization.Transient
    val resolvedMediaUrl: String? = null,

    @kotlinx.serialization.Transient
    val authorDisplayName: String? = null,

    @kotlinx.serialization.Transient
    val authorAvatarUrl: String? = null,

    @kotlinx.serialization.Transient
    val reactionCount: Int = 0,

    @kotlinx.serialization.Transient
    val isReactedByCurrentUser: Boolean = false,

    @kotlinx.serialization.Transient
    val isReactionPending: Boolean = false
)
