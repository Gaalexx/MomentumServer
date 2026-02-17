package com.example.routing

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    val username: String,
    val password: String
)

@Serializable
data class Answer(
    val ans: String,
)

fun Route.authRoutes() {
    post("/mail") {
        val body = call.receive<UserInfo>()

        val ans: Answer = Answer("Ok")
        call.respond(ans)
    }
}