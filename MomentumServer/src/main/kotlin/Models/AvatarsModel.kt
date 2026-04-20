package com.example.Models

import com.example.database.UploadingStatus
import java.util.UUID

data class AvatarsModel(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val mimeType: String,
    val status: UploadingStatus? = null,
    val isActive: Boolean = false,
    val objectKey: String,
    val sizeBytes: Long,
)
