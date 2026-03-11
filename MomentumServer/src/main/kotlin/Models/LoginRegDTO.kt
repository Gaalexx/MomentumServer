package com.example.Models

import kotlinx.serialization.Serializable

@Serializable
data class CheckEmailRequestDTO(
    val email: String,
)

@Serializable
data class CheckPhoneNumberRequestDTO(
    val phone: String,
)

@Serializable
data class CheckResponseDTO(
    val isSuccess: Boolean,
)

@Serializable
data class CheckCodeLoginResponseDTO(
    val isSuccess: Boolean,
    val token: String? = null,
)

@Serializable
data class RegisterUserRequestDTO(
    val email: String?,
    val phone: String?,
    val password: String,
    val deviceInfo: String
)

@Serializable
data class CheckCodeRequestDTO(
    val email: String?,
    val phone: String?,
    val code: String,
)

@Serializable
data class CheckCodeLoginRequestDTO(
    val email: String?,
    val phone: String?,
    val code: String,
    val deviceInfo: String
)

@Serializable
data class LoginUserRequestDTO(
    val phone: String?,
    val email: String?,
    val password: String,
    val deviceInfo: String
)

@Serializable
data class LoginResponseDTO(
    val jwt: String?,
)

@Serializable
data class GetJWTDTO(
    val token: String?,
)