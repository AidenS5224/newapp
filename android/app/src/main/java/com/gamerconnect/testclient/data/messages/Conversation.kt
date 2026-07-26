package com.gamerconnect.testclient.data.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: String,
    val title: String,

    @SerialName("conversation_type")
    val conversationType: String,

    @SerialName("created_by_profile_id")
    val createdByProfileId: String,

    @SerialName("created_at")
    val createdAt: String,

    @kotlinx.serialization.Transient
    val unreadCount: Int = 0
)