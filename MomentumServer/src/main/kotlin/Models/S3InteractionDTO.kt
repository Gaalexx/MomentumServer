package com.example.Models

import ch.qos.logback.core.util.FileSize
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
data class S3UploadInfoDTO(
    val status: UploadingStatus,
    val title: String? = null,
)

@Serializable
enum class MediaType {
    IMAGE, VIDEO, AUDIO
}