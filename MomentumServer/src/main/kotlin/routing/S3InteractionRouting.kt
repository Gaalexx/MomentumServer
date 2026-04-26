package com.example.routing

import com.example.Models.*
import com.example.Models.*
import com.example.Respond
import com.example.database.*
import com.example.firebase.PushSender
import com.example.s3Client.S3Client
import com.example.s3Client.StorageException
import com.example.tokens.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID

private val logger = LoggerFactory.getLogger("com.example.routing.S3InteractionRouting")

fun Route.s3Routes(jwtService: JwtService){ // TODO доделать удаление FAILED ссылок из медиа

    authenticate("jwt"){
        post("/upload") {

            val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val body = call.receive<UploadInfoDTO>()
            val id = UUID.randomUUID()
            val userId = UUID.fromString(principal.subject)
            println(userId.toString())
            val objectKey = "posts/${userId}/${id}"

            val media = MediaModel(
                id = id,
                userId = userId,
                mediaType = body.mediaType,
                mimeType = body.mimeType,
                objectKey = objectKey,
                sizeBytes = body.size,
                duration = body.durationMs
            )

            val response = S3Client.presignPutUrl(objectKey, Duration.ofMinutes(2))

            MediaTable.insertNewMedia(media)

            call.respond(PresignedURLDTO(response, id.toString()))
        }

        post("/upload-avatar") {

            val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val body = call.receive<UploadAvatarInfoDTO>()
            val id = UUID.randomUUID()
            val userId = UUID.fromString(principal.subject)
            println(userId.toString())
            val objectKey = "avatars/${userId}/${id}"

            val avatar = AvatarsModel (
                id = id,
                userId = userId,
                mimeType = body.mimeType,
                isActive = true, //TODO: возможность загружать неактивную аватарку?
                objectKey = objectKey,
                sizeBytes = body.size,
            )

            val response = S3Client.presignPutUrl(objectKey, Duration.ofMinutes(2))
            AvatarsTable.insertNewAvatars(avatar)
            call.respond(PresignedURLDTO(response, id.toString()))
        }

        post("/status-upload-avatar") {
            val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val body = call.receive<S3UpdateStatusDTO>()

            val avatarId = UUID.fromString(body.mediaId)

            when(body.status){
                UploadingStatus.READY -> {
                    AvatarsTable.changeStatus(avatarId, body.status)
                }
                UploadingStatus.UPLOADING -> null // TODO продумать эти случаи
                UploadingStatus.FAILED -> AvatarsTable.deleteAvatar(avatarId)
            }

            call.respond(HttpStatusCode.OK)
        }

        post("/status-upload") {
            val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val body = call.receive<S3UpdateStatusDTO>()

            val userId = UUID.fromString(principal.subject)
            val mediaId = UUID.fromString(body.mediaId)

            when (body.status) {
                UploadingStatus.READY -> {
                    MediaTable.changeStatus(mediaId, body.status)

                    val existingPost = PostsTable.getPostByMediaId(mediaId)
                    if (existingPost == null) {
                        val postId = UUID.randomUUID()
                        val post = PostModel(postId, userId, body.title ?: "", true, createdAt = null, mediaId)
                        PostsTable.insertNewPost(post)

                        val user = UserModel.getFullUser(userId)
                        val pushToken = user?.pushToken?.takeIf { it.isNotBlank() }
                        if (pushToken != null) {
                            val pushResult = PushSender.sendToToken(
                                token = pushToken,
                                title = "Новая запись",
                                body = "${user.username ?: user.email} выкладывает новый момент"
                            )

                            when {
                                pushResult.isSuccess -> {
                                    logger.info(
                                        "Push sent for media {} to user {} with messageId {}",
                                        mediaId,
                                        userId,
                                        pushResult.messageId
                                    )
                                }
                                pushResult.shouldInvalidateToken -> {
                                    UserModel.clearPushToken(userId, pushToken)
                                    logger.warn(
                                        "Invalid push token was cleared for user {} after failed push for media {}: {}",
                                        userId,
                                        mediaId,
                                        pushResult.errorCode ?: "UNKNOWN"
                                    )
                                }
                                else -> {
                                    logger.warn(
                                        "Push was not sent for media {} to user {}: {} {}",
                                        mediaId,
                                        userId,
                                        pushResult.errorCode ?: "UNKNOWN",
                                        pushResult.errorMessage ?: ""
                                    )
                                }
                            }
                        } else {
                            logger.info("Skipping push for media {}: user {} has no push token", mediaId, userId)
                        }
                    } else {
                        logger.info("Skipping duplicate READY status for media {}", mediaId)
                    }
                }
                UploadingStatus.UPLOADING -> {
                    MediaTable.changeStatus(mediaId, body.status)
                }
                UploadingStatus.FAILED -> {
                    MediaTable.changeStatus(mediaId, body.status)
                    MediaTable.deleteMedia(mediaId)
                }
            }

            call.respond(HttpStatusCode.OK)
        }

        post("/get-friends-media") {
            val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val userId = UUID.fromString(principal.subject)

            val listToSend = transaction {
                val listOfFriends = Friendships.getFriendsWithDetails(userId).toList()
                val result = mutableListOf<PostDTO>()


                listOfFriends.forEach { friend ->
                    val posts = PostsTable.getPostsOfUser(UUID.fromString(friend.userId))
                    posts.forEach { post ->
                        val media = MediaTable.getMediaById(post.mediaId)
                        val reactions = ReactionsTable.getAllPostReactions(userId, post.id)

                        if(media != null){
                            val presignedURL = S3Client.getPresignedObjectUrl(media.objectKey)
                            result.add(
                                PostDTO(
                                    post.id.toString(),
                                    post.userId.toString(),
                                    userName = friend.username,
                                    title = post.title,
                                    inUse = post.inUse,
                                    presignedURL = presignedURL,
                                    avatarPresignedURL = friend.userAvatarUrl,
                                    createdAt = post.createdAt,
                                    mediaType = media.mediaType,
                                    reactions = reactions.groupBy(
                                        keySelector = { it.reactionType },
                                        valueTransform = { it.userId.toString() }
                                    ).map { (key, value) ->
                                        ReactionsDTO(
                                            emoji = key,
                                            users = value
                                        )
                                    }
                                )
                            )
                        }
                    }
                }
                result
            }

            call.respond(HttpStatusCode.OK, listToSend)
        }

        post("/get-my-media") {

            val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val userId = UUID.fromString(principal.subject)
            val user = UserModel.getFullUser(userId)
            val presignedAvatarURL = getAvatarURL(userId)

            val listOfPosts = PostsTable.getPostsOfUser(userId)

            val listToSend: MutableList<PostDTO> = mutableListOf()
            if(user != null){
                listOfPosts.forEach { it ->
                    val media = MediaTable.getMediaById(it.mediaId)
                    val reactions = ReactionsTable.getMyPostReactions(it.id)

                    if(media != null){
                        val presignedURL = S3Client.getPresignedObjectUrl(media.objectKey)
                        listToSend.add(
                            PostDTO(
                                id = it.id.toString(),
                                userId = it.userId.toString(),
                                userName = user.username ?: user.email,
                                title = it.title,
                                inUse = it.inUse,
                                presignedURL = presignedURL,
                                avatarPresignedURL = presignedAvatarURL,
                                createdAt = it.createdAt,
                                mediaType = media.mediaType,
                                reactions = reactions.groupBy(
                                        keySelector = { it.reactionType },
                                        valueTransform = { it.userId.toString() }
                                    ).map { (key, value) ->
                                        ReactionsDTO(
                                            emoji = key,
                                            users = value
                                        )
                                    }
                            )
                        )
                    }
                }
            }


            call.respond(listToSend)
        }

        delete("/post/{postId}") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized)
            val userId = UUID.fromString(principal.subject)
            val postIdStr = call.parameters["postId"]
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    DeletePostResponseDTO(
                        success = false,
                        message = "postId is required",
                        s3Deleted = false
                    )
                )
            val postId = try {
                UUID.fromString(postIdStr)
            } catch (e: IllegalArgumentException) {
                return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    DeletePostResponseDTO(
                        success = false,
                        message = "Invalid postId format",
                        s3Deleted = false,
                        postId = postIdStr
                    )
                )
            }

            val post = PostsTable.getPostById(postId)

            if (post == null) {
                return@delete call.respond(
                    HttpStatusCode.NotFound,
                    DeletePostResponseDTO(
                        success = false,
                        message = "Post not found",
                        s3Deleted = false,
                        postId = postIdStr
                    )
                )
            }

            if (post.userId != userId) {
                return@delete call.respond(
                    HttpStatusCode.Forbidden,
                    DeletePostResponseDTO(
                        success = false,
                        message = "You can only delete your own posts",
                        s3Deleted = false,
                        postId = postIdStr
                    )
                )
            }

            val media = MediaTable.getMediaById(post.mediaId)
            var s3DeleteSuccess = false
            var s3ErrorMessage: String? = null

            if (media != null) {
                try {
                    S3Client.deleteObject(media.objectKey)
                    s3DeleteSuccess = true
                } catch (e: StorageException) {
                    println("Failed to delete file from S3: ${e.message}")
                    s3ErrorMessage = e.message
                } catch (e: Exception) {
                    println("Unexpected error deleting from S3: ${e.message}")
                    s3ErrorMessage = e.message
                }
            } else {
                s3ErrorMessage = "Media not found in database"
            }

            val postDeleted = PostsTable.deletePost(postId)
            val mediaDeleted = if (media != null) MediaTable.deleteMedia(post.mediaId) else true

            if (!postDeleted) {
                return@delete call.respond(
                    HttpStatusCode.InternalServerError,
                    DeletePostResponseDTO(
                        success = false,
                        message = "Failed to delete post from database",
                        s3Deleted = s3DeleteSuccess,
                        postId = postIdStr
                    )
                )
            }

            val responseMessage = buildString {
                append("Post deleted")
                if (!s3DeleteSuccess) {
                    append(" from database, but failed to delete file from storage")
                    if (s3ErrorMessage != null) {
                        append(": $s3ErrorMessage")
                    }
                }
            }

            call.respond(
                HttpStatusCode.OK,
                DeletePostResponseDTO(
                    success = true,
                    message = responseMessage,
                    s3Deleted = s3DeleteSuccess,
                    postId = postIdStr
                )
            )
        }

    }

}
