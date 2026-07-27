package com.gamerconnect.testclient.data.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ChatMessage(
    val id: String,

    @SerialName("conversation_id")
    val conversationId: String,

    @SerialName("sender_profile_id")
    val senderProfileId: String,

    val body: String,

    @SerialName("created_at")
    val createdAt: String,

    @Transient
    val senderName: String? = null,

    @Transient
    val senderAvatarUrl: String? = null,

    @Transient
    val isSeen: Boolean = false

)