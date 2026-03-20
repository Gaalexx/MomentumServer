package com.example.Models

import kotlinx.serialization.Serializable

@Serializable
data class AccountInformationDTO(
    val name: String,
    val accountPhotoURL: String?
)

@Serializable
data class EditAccountDTO (
    val login: String?,
    val email: String?,
    val phone: String?,
    val profilePhotoURL: String?
)