package com.example.Models

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class User (
    val id: Int,
    val usrname: String,
    val phoneNumber: String,
    val email: String,
    val displayName: String,
    val avatarMediaId: Int,
    val bio: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isDeleted: Boolean
)