package com.example.routing

import com.example.Models.GetTranscriptionResponseDTO
import com.example.Models.TranscriptionStatus
import com.example.Models.TranscriptErrorDTO
import com.example.Models.TranscriptRequestDTO
import com.example.database.Friendships
import com.example.database.MediaModel
import com.example.database.MediaTable
import com.example.database.MediaType
import com.example.database.PostModel
import com.example.database.PostsTable
import com.example.database.UploadingStatus
import com.example.s3Client.S3Client
import com.example.salute.SaluteSberAudioLimitException
import com.example.salute.SaluteSberAudioConversionException
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

private val transcriptionStates = ConcurrentHashMap<UUID, TranscriptionState>()
private val transcriptionJobs = ConcurrentHashMap<UUID, kotlinx.coroutines.Job>()
private val transcriptionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val transcriptLogger = LoggerFactory.getLogger("Transcript")

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

    if (!media.canBeTranscribed()) {
        return respond(HttpStatusCode.UnsupportedMediaType, TranscriptErrorDTO("Post media is not audio or video"))
    }

    if (media.status != UploadingStatus.READY) {
        return respond(HttpStatusCode.Conflict, TranscriptErrorDTO("Post media is not ready"))
    }

    if (transcriptionJobs[postId]?.isActive == true) {
        return respond(
            HttpStatusCode.Accepted,
            GetTranscriptionResponseDTO(TranscriptionStatus.TRANSCRIPTING, "")
        )
    }

    val existingState = transcriptionStates[postId]
    if (existingState is TranscriptionState.Done) {
        return respond(HttpStatusCode.OK, GetTranscriptionResponseDTO(TranscriptionStatus.DONE, existingState.text))
    }

    transcriptionStates[postId] = TranscriptionState.Processing
    val job = transcriptionScope.launch(start = CoroutineStart.LAZY) {
        try {
            runTranscription(postId, requesterId, post, media)
        } finally {
            transcriptionJobs.remove(postId)
        }
    }
    transcriptionJobs[postId] = job
    job.start()

    respond(HttpStatusCode.Accepted, GetTranscriptionResponseDTO(TranscriptionStatus.TRANSCRIPTING, ""))
}

private suspend fun runTranscription(postId: UUID, requesterId: UUID, post: PostModel, media: MediaModel) {
    try {
        transcriptLogger.info(
            "Transcription started: postId={}, mediaId={}, requesterId={}, objectKey={}, mimeType={}, sizeBytes={}, durationMs={}",
            post.id,
            media.id,
            requesterId,
            media.objectKey,
            media.mimeType,
            media.sizeBytes,
            media.duration
        )

        val presignedUrl = S3Client.getPresignedObjectUrl(media.objectKey)
        val recognition = SaluteSberSpeechService.recognizeByPresignedUrl(presignedUrl, media)
        if (recognition.text.isBlank()) {
            transcriptLogger.warn(
                "Transcription completed with empty text: postId={}, mediaId={}, results={}",
                post.id,
                media.id,
                recognition.results.size
            )
            transcriptionStates[postId] = TranscriptionState.Error
            return
        }

        transcriptionStates[postId] = TranscriptionState.Done(recognition.text)
        transcriptLogger.info(
            "Transcription completed: postId={}, mediaId={}, textChars={}, results={}",
            post.id,
            media.id,
            recognition.text.length,
            recognition.results.size
        )
    } catch (e: SaluteSberConfigurationException) {
        transcriptLogger.error(
            "Transcription configuration error: postId={}, mediaId={}, message={}",
            post.id,
            media.id,
            e.message
        )
        transcriptionStates[postId] = TranscriptionState.Error
    } catch (e: SaluteSberUnsupportedAudioException) {
        transcriptLogger.warn(
            "Transcription unsupported audio: postId={}, mediaId={}, mimeType={}, message={}",
            post.id,
            media.id,
            media.mimeType,
            e.message
        )
        transcriptionStates[postId] = TranscriptionState.Error
    } catch (e: SaluteSberAudioLimitException) {
        transcriptLogger.warn(
            "Transcription audio limit exceeded: postId={}, mediaId={}, sizeBytes={}, durationMs={}, message={}",
            post.id,
            media.id,
            media.sizeBytes,
            media.duration,
            e.message
        )
        transcriptionStates[postId] = TranscriptionState.Error
    } catch (e: SaluteSberAudioConversionException) {
        transcriptLogger.warn(
            "Transcription media conversion failed: postId={}, mediaId={}, mimeType={}, message={}",
            post.id,
            media.id,
            media.mimeType,
            e.message
        )
        transcriptionStates[postId] = TranscriptionState.Error
    } catch (e: SaluteSberRemoteException) {
        transcriptLogger.warn(
            "Transcription upstream error: postId={}, mediaId={}, upstreamStatus={}, message={}, details={}",
            post.id,
            media.id,
            e.status.value,
            e.message,
            e.details?.take(1000).orEmpty()
        )
        transcriptionStates[postId] = TranscriptionState.Error
    } catch (e: SerializationException) {
        transcriptLogger.warn(
            "Transcription response parse error: postId={}, mediaId={}, message={}",
            post.id,
            media.id,
            e.message,
            e
        )
        transcriptionStates[postId] = TranscriptionState.Error
    } catch (e: Exception) {
        transcriptLogger.error(
            "Transcription unexpected error: postId={}, mediaId={}",
            post.id,
            media.id,
            e
        )
        transcriptionStates[postId] = TranscriptionState.Error
    }
}

private sealed class TranscriptionState {
    data object Processing : TranscriptionState()
    data class Done(val text: String) : TranscriptionState()
    data object Error : TranscriptionState()
}

private fun MediaModel.canBeTranscribed(): Boolean {
    if (mediaType == MediaType.AUDIO || mediaType == MediaType.VIDEO) {
        return true
    }

    val normalizedMime = mimeType.lowercase().trim().substringBefore(";").trim()
    return normalizedMime.startsWith("audio/") || normalizedMime.startsWith("video/")
}
