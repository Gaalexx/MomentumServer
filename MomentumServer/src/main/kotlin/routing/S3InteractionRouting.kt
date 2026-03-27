package com.example.routing

import com.example.Models.PostDTO
import com.example.Models.PresignedURLDTO
import com.example.Models.S3UpdateStatusDTO
import com.example.Models.UploadAvatarInfoDTO
import com.example.Models.UploadInfoDTO
import com.example.Respond
import com.example.database.AvatarsModel
import com.example.database.AvatarsTable
import com.example.database.Friendships
import com.example.database.MediaModel
import com.example.database.MediaTable
import com.example.database.PostModel
import com.example.database.PostsTable
import com.example.database.UploadingStatus
import com.example.database.User
import com.example.database.UserModel
import com.example.database.getAvatarURL
import com.example.s3Client.S3Client
import com.example.tokens.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Duration
import java.util.UUID

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

            val userId = UUID.fromString(principal.subject)
            val avatarId = UUID.fromString(body.mediaId)

            when(body.status){
                UploadingStatus.READY -> {
                    AvatarsTable.changeStatus(avatarId, body.status)
                    UserModel.updateAvatar(userId, avatarId)
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
            val postId = UUID.randomUUID()

            val post: PostModel? = when(body.status){
                UploadingStatus.READY -> PostModel(postId, userId, body.title ?: "", true, createdAt = null, mediaId)
                UploadingStatus.UPLOADING -> null // TODO продумать эти случаи
                UploadingStatus.FAILED -> null
            }

            if(post == null){
                // TODO удалить медиа из бд
            }
            else{
                PostsTable.insertNewPost(post)
            }

            call.respond(HttpStatusCode.OK)
        }

        post("/get-friends-media") {
            val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val userId = UUID.fromString(principal.subject)

            val listOfFriends = transaction {Friendships.getFriendsWithDetails(userId)}
            val listToSend: MutableList<PostDTO> = mutableListOf()

            listOfFriends.forEach { friend ->
                val posts = PostsTable.getPostsOfUser(UUID.fromString(friend.userId))
                posts.forEach { post ->
                    val media = MediaTable.getObjectKeyOfPost(post.mediaId)
                    if(media != null){
                        listToSend.add(PostDTO(post.id.toString(), post.userId.toString(), userName = friend.username,
                            title = post.title, inUse = post.inUse, presignedURL = media, avatarPresignedURL = friend.userAvatarUrl, createdAt = post.createdAt))
                    }
                }
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
                    val media = MediaTable.getObjectKeyOfPost(it.mediaId)
                    if(media != null){
                        val presignedURL = S3Client.getPresignedObjectUrl(media)
                        listToSend.add(PostDTO(it.id.toString(), it.userId.toString(), userName = user.username ?: user.email, it.title, it.inUse, presignedURL, presignedAvatarURL, it.createdAt))
                    }
                }
            }


            call.respond(listToSend)
        }
    }




}