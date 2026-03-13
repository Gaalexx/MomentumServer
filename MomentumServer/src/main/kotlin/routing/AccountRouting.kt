package com.example.routing

import com.example.Models.AccountInformationDTO
import com.example.database.UserModel
import com.example.tokens.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
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
                    call.respond(HttpStatusCode.OK, AccountInformationDTO(user.username, "https://sun9-76.userapi.com/s/v1/ig2/T0jKQCt5-3MxyNEyM6x7KFiUPXQWMGizwTyRaKG_7stdeC0EWdqe0Wbw1ZyCFH1f119tma3KCcGorrpBQsciUT1Z.jpg?quality=95&as=32x43,48x64,72x96,108x144,160x213,240x320,360x480,480x640,540x720,640x853,720x960,1080x1440,1280x1707,1440x1920,1920x2560&from=bu&cs=1920x0")) // TODO обавить логику с урлом аватарки
                }
            }
            else{
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }


}