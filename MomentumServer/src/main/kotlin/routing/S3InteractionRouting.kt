package com.example.routing

import com.example.Models.S3UploadInfoDTO
import com.example.Models.UploadInfoDTO
import com.example.Respond
import com.example.database.MediaModel
import com.example.database.MediaTable
import com.example.database.MediaType
import com.example.database.PostModel
import com.example.database.PostsTable
import com.example.database.UploadingStatus
import com.example.s3Client.MinioStorage
import com.example.s3Client.S3Client
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.insert
import java.time.Duration
import java.util.UUID
import kotlin.time.Duration.Companion.days

fun Route.s3Routes(){ // TODO все это надо обернуть в authorize в конечном итоге

    post("/upload") {

        val body = call.receive<UploadInfoDTO>()
        val id = UUID.randomUUID() // TODO сохранить в КЭШ
        val userId = UUID.fromString("b23177c0-c722-47e9-9c05-f6700d639c14") // TODO из jwt
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

        call.respond(Respond(response))
    }

    post("/post") {

        val body = call.receive<S3UploadInfoDTO>()

        val userId = UUID.fromString("b23177c0-c722-47e9-9c05-f6700d639c14") // TODO из jwt
        val mediaId = UUID.randomUUID() // TODO взять из кэша
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

}