package com.example.Models

import com.example.database.MediaType
import com.example.database.UploadingStatus
import kotlinx.serialization.Serializable

@Serializable
data class UploadInfoDTO(
    val mimeType: String,
    val mediaType: MediaType,
    val size: Long,
    val durationMs: Long? = null,
)

@Serializable
data class UploadAvatarInfoDTO(
    val mimeType: String,
    val size: Long,
)

@Serializable
data class S3UpdateStatusDTO(
    val status: UploadingStatus,
    val mediaId: String,
    val title: String? = null,
)

@Serializable
data class PresignedURLDTO(
    val urlToLoad: String,
    val mediaId: String
)


@Serializable
data class PostDTO(
    val id: String,
    val userId: String,
    val userName: String,
    val title: String,
    val inUse: Boolean,
    val presignedURL: String,
    val avatarPresignedURL: String? = null,
    val createdAt: String? = null)

