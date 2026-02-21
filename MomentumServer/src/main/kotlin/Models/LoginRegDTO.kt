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
data class RegisterUserRequestDTO(
    val email: String?,
    val phone: String?,
    val password: String,
)

@Serializable
data class CheckCodeRequestDTO(
    val code: String,
)

@Serializable
data class LoginUserRequestDTO(
    val phone: String?,
    val email: String?,
    val password: String,
)

@Serializable
data class LoginResponseDTO(
    val jwt: String,
)