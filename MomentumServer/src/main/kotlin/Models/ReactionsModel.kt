package com.example.Models

import com.example.database.UploadingStatus
import java.util.UUID

data class ReactionsModel(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val postId: UUID,
    val reactionType: String,
    val createdAt: String? = null,
)
