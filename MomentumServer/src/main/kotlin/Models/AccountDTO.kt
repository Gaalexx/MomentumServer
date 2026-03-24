package com.example.Models

import kotlinx.serialization.Serializable

@Serializable
data class AccountInformationDTO(
    val name: String,
    val accountPhotoURL: String?
)

@Serializable
data class EditAccountDTO (
    val username: String?,
    val email: String?,
    val phone: String?,
    val profilePhotoURL: String?
)

@Serializable
data class CheckUserInfoIsFreeRequestDTO(
    val username: String?,
    val email: String?,
    val phone: String?,
)

@Serializable
data class CheckUserInfoIsFreeResponseDTO(
    val isUsernameFree: Boolean?,
    val isEmailFree: Boolean?,
    val isPhoneFree: Boolean?,
)