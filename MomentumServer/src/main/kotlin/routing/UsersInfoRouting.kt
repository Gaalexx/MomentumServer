package com.example.routing

import com.example.Models.FriendRequestActionDTO
import com.example.database.UserModel
import com.example.tokens.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.usersInfoRouting(jwtService: JwtService){
    route("/exists"){

        get("/email/{email}"){
            val email = call.parameters["email"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest
                )

            val id = UserModel.getIdByEmail(email)
            if(id == null){
                return@get call.respond(HttpStatusCode.OK, false)
            }
            else{
                return@get call.respond(HttpStatusCode.OK, true)
            }
        }

        get("/login/{login}"){
            val login = call.parameters["login"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest
                )

            val id = UserModel.getIdByUserName(login)
            if(id == null){
                return@get call.respond(HttpStatusCode.OK, false)
            }
            else{
                return@get call.respond(HttpStatusCode.OK, true)
            }
        }

        get("/phone/{phone}"){
            val phone = call.parameters["phone"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest
                )

            val id = UserModel.getIdByPhone(phone)
            if(id == null){
                return@get call.respond(HttpStatusCode.OK, false)
            }
            else{
                return@get call.respond(HttpStatusCode.OK, true)
            }
        }

    }
}