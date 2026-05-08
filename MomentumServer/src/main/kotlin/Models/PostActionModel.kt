package com.example.Models

import java.util.UUID

data class PostActionModel(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val postId: UUID,
    val actionType: String,
    val createdAt: String? = null,
)
