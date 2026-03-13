package com.example.Models

import kotlinx.serialization.Serializable

@Serializable
data class AccountInformationDTO(
    val name: String,
    val accountPhotoURL: String?
)