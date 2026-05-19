package com.example.routing

import com.example.Models.GetTranscriptionResponseDTO
import com.example.Models.TranscriptionStatus
import com.example.Models.TranscriptErrorDTO
import com.example.Models.TranscriptRequestDTO
import com.example.Models.TranscriptResponseDTO
import com.example.database.Friendships
import com.example.database.MediaTable
import com.example.database.MediaType
import com.example.database.PostsTable
import com.example.database.UploadingStatus
import com.example.s3Client.S3Client
import com.example.salute.SaluteSberAudioLimitException
import com.example.salute.SaluteSberConfigurationException
import com.example.salute.SaluteSberRemoteException
import com.example.salute.SaluteSberSpeechService
import com.example.salute.SaluteSberUnsupportedAudioException
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerializationException
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

private val transcriptionStates = ConcurrentHashMap<UUID, TranscriptionState>()

fun Route.saluteSBERRoutes() {
    authenticate("jwt") {
        route("/transcript") {
            post {
                val body = call.receive<TranscriptRequestDTO>()
                call.respondTranscript(body.resolvedPostId())
            }

            get {
                call.respondTranscript(call.request.queryParameters.resolvePostId())
            }

            get("/{postId}/status") {
                call.respondTranscriptionStatus(call.parameters["postId"])
            }

            get("/{postId}") {
                call.respondTranscript(call.parameters["postId"])
            }
        }
    }
}

private fun Parameters.resolvePostId(): String? {
    return this["postId"] ?: this["post_id"] ?: this["id"] ?: this["uuid"]
}

private suspend fun ApplicationCall.respondTranscriptionStatus(postIdRaw: String?) {
    val principal = principal<JWTPrincipal>()
        ?: return respond(HttpStatusCode.Unauthorized, TranscriptErrorDTO("Unauthorized"))
    val requesterId = UUID.fromString(principal.subject)

    val postId = postIdRaw?.let {
        runCatching { UUID.fromString(it) }.getOrNull()
    } ?: return respond(
        HttpStatusCode.BadRequest,
        GetTranscriptionResponseDTO(TranscriptionStatus.ERROR, "")
    )

    val post = PostsTable.getPostById(postId)
        ?: return respond(HttpStatusCode.NotFound, GetTranscriptionResponseDTO(TranscriptionStatus.ERROR, ""))

    if (post.userId != requesterId && !Friendships.areFriends(requesterId, post.userId)) {
        return respond(HttpStatusCode.Forbidden, GetTranscriptionResponseDTO(TranscriptionStatus.ERROR, ""))
    }

    val state = transcriptionStates[postId] ?: TranscriptionState.Error
    val response = when (state) {
        is TranscriptionState.Done -> GetTranscriptionResponseDTO(TranscriptionStatus.DONE, state.text)
        TranscriptionState.Processing -> GetTranscriptionResponseDTO(TranscriptionStatus.TRANSCRIPTING, "")
        TranscriptionState.Error -> GetTranscriptionResponseDTO(TranscriptionStatus.ERROR, "")
    }

    respond(HttpStatusCode.OK, response)
}

private suspend fun ApplicationCall.respondTranscript(postIdRaw: String?) {
    val principal = principal<JWTPrincipal>()
        ?: return respond(HttpStatusCode.Unauthorized, TranscriptErrorDTO("Unauthorized"))
    val requesterId = UUID.fromString(principal.subject)

    val postId = postIdRaw?.let {
        runCatching { UUID.fromString(it) }.getOrNull()
    } ?: return respond(HttpStatusCode.BadRequest, TranscriptErrorDTO("Invalid postId"))

    val post = PostsTable.getPostById(postId)
        ?: return respond(HttpStatusCode.NotFound, TranscriptErrorDTO("Post not found"))

    if (post.userId != requesterId && !Friendships.areFriends(requesterId, post.userId)) {
        return respond(HttpStatusCode.Forbidden, TranscriptErrorDTO("Post is not available for this user"))
    }

    val media = MediaTable.getMediaById(post.mediaId)
        ?: return respond(HttpStatusCode.NotFound, TranscriptErrorDTO("Post media not found"))

    if (media.mediaType != MediaType.AUDIO) {
        return respond(HttpStatusCode.UnsupportedMediaType, TranscriptErrorDTO("Post media is not audio"))
    }

    if (media.status != UploadingStatus.READY) {
        return respond(HttpStatusCode.Conflict, TranscriptErrorDTO("Post media is not ready"))
    }

    transcriptionStates[postId] = TranscriptionState.Processing

    try {
        val presignedUrl = S3Client.getPresignedObjectUrl(media.objectKey)
        val recognition = SaluteSberSpeechService.recognizeByPresignedUrl(presignedUrl, media)
        transcriptionStates[postId] = TranscriptionState.Done(recognition.text)
        respond(
            HttpStatusCode.OK,
            TranscriptResponseDTO(
                postId = post.id.toString(),
                mediaId = media.id.toString(),
                text = recognition.text,
                normalizedText = recognition.normalizedText,
                results = recognition.results
            )
        )
    } catch (e: SaluteSberConfigurationException) {
        respond(
            HttpStatusCode.ServiceUnavailable,
            TranscriptErrorDTO("SaluteSpeech is not configured", e.message)
        )
        transcriptionStates[postId] = TranscriptionState.Error
    } catch (e: SaluteSberUnsupportedAudioException) {
        respond(
            HttpStatusCode.UnsupportedMediaType,
            TranscriptErrorDTO("Unsupported audio format", e.message)
        )
        transcriptionStates[postId] = TranscriptionState.Error
    } catch (e: SaluteSberAudioLimitException) {
        respond(
            HttpStatusCode(413, "Payload Too Large"),
            TranscriptErrorDTO("Audio exceeds SaluteSpeech sync recognition limits", e.message)
        )
        transcriptionStates[postId] = TranscriptionState.Error
    } catch (e: SaluteSberRemoteException) {
        respond(
            HttpStatusCode.BadGateway,
            TranscriptErrorDTO(
                "SaluteSpeech request failed",
                "upstreamStatus=${e.status.value}; ${e.details?.take(1000).orEmpty()}"
            )
        )
        transcriptionStates[postId] = TranscriptionState.Error
    } catch (e: SerializationException) {
        respond(
            HttpStatusCode.BadGateway,
            TranscriptErrorDTO("Invalid SaluteSpeech response", e.message)
        )
        transcriptionStates[postId] = TranscriptionState.Error
    } catch (e: Exception) {
        respond(
            HttpStatusCode.BadGateway,
            TranscriptErrorDTO("Transcription request failed", e.message)
        )
        transcriptionStates[postId] = TranscriptionState.Error
    }
}

private sealed class TranscriptionState {
    data object Processing : TranscriptionState()
    data class Done(val text: String) : TranscriptionState()
    data object Error : TranscriptionState()
}
