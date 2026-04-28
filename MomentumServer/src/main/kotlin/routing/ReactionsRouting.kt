package com.example.routing

import com.example.Models.ReactionsModel
import com.example.database.PostsTable
import com.example.database.ReactionsTable
import com.example.database.SettingsTable
import com.example.database.UserModel
import com.example.firebase.PushSender
import com.example.tokens.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import java.util.UUID

fun Route.reactionsRoutes(jwtService: JwtService){

    authenticate("jwt"){
        post("/react/{post_id}/{reaction_type}") {
            val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val postId = UUID.fromString(call.parameters["post_id"]?: return@post call.respond(
                HttpStatusCode.BadRequest
            ))

            val reactionType = call.parameters["reaction_type"]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest
                )
            val id = UUID.randomUUID()
            val userId = UUID.fromString(principal.subject)

            try {
                ReactionsTable.insertNewReaction(
                    ReactionsModel(
                        id = id,
                        userId = userId,
                        postId = postId,
                        reactionType = reactionType,
                    )
                )
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid UUID format ${e.message ?: ""}")
            }

            val post = PostsTable.getPostById(postId)
            if(post != null) {
                val user = UserModel.getFullUser(post.userId)
                val userWhoLiled = UserModel.getFullUser(userId)
                val settings = SettingsTable.getServerSettingsInfo(userId)
                if(user != null && userWhoLiled != null && settings != null && settings.reactionsEnabled && user.pushToken != null) {
                    PushSender.sendToToken(user.pushToken, "Новая реакция", "${userWhoLiled.username ?: userWhoLiled.email} поставил реакцию ${reactionType}!")
                }
            }


            call.respond(HttpStatusCode.OK)
        }

        delete("/unreact/{post_id}/{reaction_type}") {
            val principal = call.principal<JWTPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)

            val postId = call.parameters["post_id"]
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest
                )
            val reactionType = call.parameters["reaction_type"]
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest
                )
            val userId = UUID.fromString(principal.subject)

            try {
                ReactionsTable.deleteReaction(
                    userId = userId,
                    postId = UUID.fromString(postId),
                    reactionType = reactionType,
                )
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid UUID format ${e.message ?: ""}")
            } catch (e : Exception) {
                call.respond(HttpStatusCode.BadRequest, "Error deleteReaction ${e.message ?: ""}")
            }

            call.respond(HttpStatusCode.OK)
        }
    }
}