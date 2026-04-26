package com.example.firebase

import kotlinx.serialization.Serializable

@Serializable
data class SendPushRequestDTO(
    val token: String,
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap()
)

@Serializable
data class SendPushResponseDTO(
    val success: Boolean,
    val messageId: String? = null,
    val error: String? = null
)