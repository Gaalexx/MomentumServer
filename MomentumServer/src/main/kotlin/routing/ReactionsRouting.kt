package com.example.routing

import com.example.Models.PresignedURLDTO
import com.example.Models.ReactionsModel
import com.example.Models.UploadInfoDTO
import com.example.database.MediaModel
import com.example.database.MediaTable
import com.example.database.ReactionsTable
import com.example.s3Client.S3Client
import com.example.tokens.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.Duration
import java.util.UUID

fun Route.ReactionsRootes(jwtService: JwtService){

    authenticate("jwt"){
        post("/react/{post_id}/{reaction_type}") {
            val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val postId = call.parameters["post_id"]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest
                )
            val reactionType = call.parameters["reaction_type"]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest
                )
            val id = UUID.randomUUID()
            val userId = UUID.fromString(principal.subject)
            println(userId.toString())

            try {
                ReactionsTable.insertNewReaction(
                    ReactionsModel(
                        id = id,
                        userId = userId,
                        postId = UUID.fromString(postId),
                        reactionType = reactionType,
                    )
                )
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid UUID format ${e.message ?: ""}")
            }

            call.respond(HttpStatusCode.OK)
        }
    }
}