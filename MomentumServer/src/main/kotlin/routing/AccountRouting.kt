package com.example.routing

import com.example.Models.AccountInformationDTO
import com.example.Models.CheckResponseDTO
import com.example.Models.CheckUserInfoIsFreeRequestDTO
import com.example.Models.CheckUserInfoIsFreeResponseDTO
import com.example.Models.EditAccountDTO
import com.example.database.UserModel
import com.example.tokens.JwtService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

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
                call.respond(
                    HttpStatusCode.OK,
                    EditAccountDTO(
                        user.username,
                        user.email,
                        user.phoneNumber,
                        null
                    )
                )
            }
            else{
                call.respond(HttpStatusCode.BadRequest)
            }
        }

        post("/check-userinfo-is-free") {
            val userinfo = call.receive<CheckUserInfoIsFreeRequestDTO>()
            var isUsernameFree: Boolean? = null
            var isEmailFree: Boolean? = null
            var isPhoneFree: Boolean? = null

            if (userinfo.username != null) {
                val id = UserModel.getIdByUserName(userinfo.username)
                isUsernameFree = id == null
            }
            if (userinfo.email != null) {
                val id = UserModel.getIdByEmail(userinfo.email)
                isEmailFree = id == null
            }
            if (userinfo.phone != null) {
                val id = UserModel.getIdByPhone(userinfo.phone)
                isPhoneFree = id == null
            }
            call.respond(
                HttpStatusCode.OK,
                CheckUserInfoIsFreeResponseDTO(
                    isUsernameFree = isUsernameFree,
                    isEmailFree = isEmailFree,
                    isPhoneFree = isPhoneFree
                )
            )
        }
    }


}