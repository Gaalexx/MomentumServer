package com.example.Models

import kotlinx.serialization.Serializable

// Request DTO
@Serializable
data class CheckEmailRequestDTO(
    val email: String,
)

@Serializable
data class CheckPhoneNumberRequestDTO(
    val phone: String,
)

@Serializable
data class CheckUserInfoIsFreeRequestDTO(
    val username: String?,
    val email: String?,
    val phone: String?,
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
data class GetJWTDTO(
    val token: String?,
)
// Response DTO
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
data class CheckUserInfoIsFreeResponseDTO(
    val isUsernameFree: Boolean?,
    val isEmailFree: Boolean?,
    val isPhoneFree: Boolean?,
)

@Serializable
data class LoginResponseDTO(
    val jwt: String?,
)