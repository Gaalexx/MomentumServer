package com.example.Models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranscriptRequestDTO(
    val postId: String? = null,
    @SerialName("post_id")
    val postIdSnake: String? = null,
    val id: String? = null,
    val uuid: String? = null
) {
    fun resolvedPostId(): String? = postId ?: postIdSnake ?: id ?: uuid
}

@Serializable
data class TranscriptResponseDTO(
    val postId: String,
    val mediaId: String,
    val text: String,
    val normalizedText: String? = null,
    val results: List<SaluteSberRecognitionResultDTO> = emptyList()
)

@Serializable
enum class TranscriptionStatus {
    DONE,
    TRANSCRIPTING,
    ERROR
}

@Serializable
data class GetTranscriptionResponseDTO(
    val status: TranscriptionStatus,
    val transcription: String
)

@Serializable
data class SaluteSberRecognitionResultDTO(
    val text: String? = null,
    @SerialName("normalized_text")
    val normalizedText: String? = null
)

@Serializable
data class TranscriptErrorDTO(
    val error: String,
    val details: String? = null
)
