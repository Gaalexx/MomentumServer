package com.example.Models

import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    val username: String,
    val password: String
)

@Serializable
data class SendCodeRequestDTO(
    val email: String
)

@Serializable
data class SendCodeResponseDTO(
    val success: Boolean,
    val message: String,
    val code: String? = null
)

@Serializable
data class LogoutRequestDTO(
    val refreshToken: String
)

@Serializable
data class LogoutResponseDTO(
    val success: Boolean,
    val message: String
)