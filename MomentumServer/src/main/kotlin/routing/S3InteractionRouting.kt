package com.example.routing

import com.example.Models.PostDTO
import com.example.Models.PresignedURLDTO
import com.example.Models.S3UpdateStatusDTO
import com.example.Models.UploadInfoDTO
import com.example.Respond
import com.example.database.MediaModel
import com.example.database.MediaTable
import com.example.database.PostModel
import com.example.database.PostsTable
import com.example.database.UploadingStatus
import com.example.s3Client.S3Client
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.selectAll
import java.time.Duration
import java.util.UUID

fun Route.s3Routes(){ // TODO все это надо обернуть в authorize в конечном итоге + доделать удаление FAILED ссылок из медиа

    post("/upload") {

        val body = call.receive<UploadInfoDTO>()
        val id = UUID.randomUUID()
        val userId = UUID.fromString("4a3416a6-597a-431c-bf95-43ee749f82c6") // TODO из jwt
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

        val response = S3Client.presignPutUrl(objectKey, Duration.ofHours(1))

        MediaTable.insertNewMedia(media)

        call.respond(PresignedURLDTO(response, id.toString()))
    }

    post("/status-upload") {

        val body = call.receive<S3UpdateStatusDTO>()

        val userId = UUID.fromString("4a3416a6-597a-431c-bf95-43ee749f82c6") // TODO из jwt
        val mediaId = UUID.fromString(body.mediaId)
        val postId = UUID.randomUUID()

        val post: PostModel? = when(body.status){
            UploadingStatus.READY -> PostModel(postId, userId, body.title ?: "", true)
            UploadingStatus.UPLOADING -> null // TODO продумать эти случаи
            UploadingStatus.FAILED -> null
        }

        if(post == null){
            // TODO удалить медиа из бд
        }
        else{
            PostsTable.insertNewPost(post)
            MediaTable.addPostId(mediaId, postId)
        }

        call.respond(HttpStatusCode.OK)
    }

    post("/get-my-media") {
        val userId = UUID.fromString("4a3416a6-597a-431c-bf95-43ee749f82c6") // TODO из jwt

        val listOfPosts = PostsTable.getPostsOfUser(userId)
        listOfPosts.forEach {
            println(it.title)
        }
        val listToSend: MutableList<PostDTO> = mutableListOf()
        listOfPosts.forEach { it ->  // TODO не отказоустойчивый код. надо проверять, нашлось ли media
            val media = MediaTable.getObjectKeyOfPost(it.id)
            val presignedURL = S3Client.getPresignedObjectUrl(media)
            println(presignedURL)
            listToSend.add(PostDTO(it.id.toString(), it.userId.toString(), it.title, it.inUse, presignedURL, it.createdAt))
        }

        call.respond(listToSend)
    }

}