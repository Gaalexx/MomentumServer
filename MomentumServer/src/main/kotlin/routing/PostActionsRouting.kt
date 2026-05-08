package com.example.routing

import com.example.Models.PostActionModel
import com.example.database.PostActionsTable
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

fun Route.postActionsRoutes(jwtService: JwtService){

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
            val userId = UUID.fromString(principal.subject)

            try {
                PostActionsTable.insertNewAction(
                    PostActionModel(
                        userId = userId,
                        postId = UUID.fromString(postId),
                        actionType = reactionType,
                    )
                )
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid UUID format ${e.message ?: ""}")
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
                PostActionsTable.deleteAction(
                    userId = userId,
                    postId = UUID.fromString(postId),
                    actionType = reactionType,
                )
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid UUID format ${e.message ?: ""}")
            } catch (e : Exception) {
                call.respond(HttpStatusCode.BadRequest, "Error deleteAction (reaction) ${e.message ?: ""}")
            }

            call.respond(HttpStatusCode.OK)
        }

        post("/hide/{post_id}") {
            val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val postId = call.parameters["post_id"]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest
                )
            val userId = UUID.fromString(principal.subject)

            try {
                PostActionsTable.insertNewAction(
                    PostActionModel(
                        userId = userId,
                        postId = UUID.fromString(postId),
                        actionType = "HIDE",
                    )
                )
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid UUID format ${e.message ?: ""}")
            }

            call.respond(HttpStatusCode.OK)
        }

        delete("/show/{post_id}") {
            val principal = call.principal<JWTPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)

            val postId = call.parameters["post_id"]
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest
                )
            val userId = UUID.fromString(principal.subject)

            try {
                PostActionsTable.deleteAction(
                    userId = userId,
                    postId = UUID.fromString(postId),
                    actionType = "HIDE",
                )
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid UUID format ${e.message ?: ""}")
            } catch (e : Exception) {
                call.respond(HttpStatusCode.BadRequest, "Error deleteAction (hidden) ${e.message ?: ""}")
            }

            call.respond(HttpStatusCode.OK)
        }
    }
}