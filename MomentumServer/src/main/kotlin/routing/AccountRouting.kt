package com.example.routing

import com.example.Models.AccountInformationDTO
import com.example.Models.EditAccountDTO
import com.example.database.UserModel
import com.example.tokens.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.util.UUID

fun Route.accountRoutes(jwtService: JwtService) {

    authenticate("jwt"){
        post("/me"){

            val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val usedId = UUID.fromString(principal.subject)

            val user = UserModel.getFullUser(usedId)
            if(user != null){
                if(user.username == null){
                    call.respond(HttpStatusCode.OK, AccountInformationDTO(user.email, null)) // TODO обавить логику с урлом аватарки
                }
                else{
                    call.respond(HttpStatusCode.OK, AccountInformationDTO(user.username, null)) // TODO обавить логику с урлом аватарки
                }
            }
            else{
                call.respond(HttpStatusCode.BadRequest)
            }
        }

        post("/update-user-info"){

            val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val usedId = UUID.fromString(principal.subject)

            val user = UserModel.getFullUser(usedId)
            if(user != null){
                val body = call.receive<EditAccountDTO>()
                UserModel.updateFullUser(usedId, body.login, body.email, body.phone)
            }
            else{
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }


}