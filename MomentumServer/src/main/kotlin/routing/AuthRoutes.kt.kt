package com.example.routing

import com.example.Models.User
import com.example.database.UserModel
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    val username: String,
    val password: String
)

//@Serializable
//data class Answer(
//    val ans: String,
//)

fun Route.authRoutes() {
    post("/insert") {
        val body = call.receive<UserInfo>()

        UserModel.insert(User(body.username, body.password))
        call.respond(body)
    }
}