package com.example.Models

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class User (
    val username: String,
    val password: String
)