package com.example.routing

import com.example.Models.AccountInformationDTO
import com.example.Models.FriendRequestActionDTO
import com.example.database.UserModel
import com.example.database.getAvatarURL
import com.example.tokens.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.usersInfoRouting(jwtService: JwtService){
    route("/exists"){

        get("/id/{id}"){
            val id = call.parameters["id"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest
                )

            if(UserModel.existsUserById(UUID.fromString(id))){
                return@get call.respond(HttpStatusCode.OK, true)
            }
            else{
                return@get call.respond(HttpStatusCode.OK, false)
            }
        }

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
    route("/get-info"){
        get("/id/{id}"){
            val principal = call.principal<JWTPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val id = call.parameters["id"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest
                )
            val usedId = UUID.fromString(id)

            val user = UserModel.getFullUser(usedId)
            if(user != null) {
                if(user.username == null){
                    call.respond(HttpStatusCode.OK,
                        AccountInformationDTO(
                            user.id.toString(),
                            user.email,
                            user.email,
                            user.phoneNumber,
                            getAvatarURL(user.id),
                            user.hasPremium
                        )
                    )
                }
                else{
                    call.respond(HttpStatusCode.OK,
                        AccountInformationDTO(
                            user.id.toString(),
                            user.email,
                            user.username,
                            user.phoneNumber,
                            getAvatarURL(user.id),
                            user.hasPremium
                        )
                    )
                }
            }
            else{
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}