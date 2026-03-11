package com.example.routing


import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.Models.CheckCodeRequestDTO
import com.example.Models.CheckEmailRequestDTO
import com.example.Models.CheckPhoneNumberRequestDTO
import com.example.Models.CheckResponseDTO
import com.example.Models.GetJWTDTO
import com.example.Models.LoginResponseDTO
import com.example.Models.LoginUserRequestDTO
import com.example.Models.RegisterUserRequestDTO
import com.example.data.codestorage.CodeStorage
import com.example.data.emailsender.EmailSender
import com.example.database.SessionTable
import com.example.database.UserModel
import com.example.tokens.JwtService
import com.example.tokens.RefreshTokenGenerator
import io.ktor.http.HttpStatusCode
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
data class SendCodeRequestDTO(
    val email: String
)

@Serializable
data class SendCodeResponseDTO(
    val success: Boolean,
    val message: String,
    val code: String? = null
)




fun Route.authRoutes(jwtService: JwtService) {
    val jwtConfig = environment.config.config("jwt")
    val jwtAudience = jwtConfig.property("audience").getString()
    val jwtDomain = jwtConfig.property("domain").getString()
    val jwtRealm = jwtConfig.property("realm").getString()
    val jwtSecret = jwtConfig.property("secret").getString()

    // new test endpoint: send code
    // example request: curl -X POST http://localhost/api/momentum/send-test-code -H "Content-Type: application/json" -d "{\"email\":\"test@example.com\"}"
    post("/send-test-code") {
        try {
            val request = call.receive<SendCodeRequestDTO>()

            val code = (100000..999999).random().toString()
            CodeStorage.saveCode(request.email, code)

            val sendResult = EmailSender.sendVerificationCode(
                recipientEmail = request.email,
                code = code
            )

            sendResult.onSuccess {
                // only for testing: return code
                call.respond(
                    HttpStatusCode.OK,
                    SendCodeResponseDTO(
                        success = true,
                        message = "Code sent successfully to ${request.email}",
                        code = code
                    )
                )
            }.onFailure { error ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    SendCodeResponseDTO(
                        success = false,
                        message = "Failed to send email: ${error.message}",
                        code = null
                    )
                )
            }

        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                SendCodeResponseDTO(
                    success = false,
                    message = "Invalid request: ${e.message}",
                    code = null
                )
            )
        }
    }

    post("/auth"){
        val body = call.receive<GetJWTDTO>()
        if(body.token != null){
            val tokenInfo = SessionTable.getSessionInfo(body.token)
            if(tokenInfo != null){
                val tokenToSend = jwtService.createAccessToken(tokenInfo.userId.toString(), tokenInfo.sessionId.toString())
                call.respond(HttpStatusCode.OK, GetJWTDTO(tokenToSend))
            }
            else{
                call.respond(HttpStatusCode.OK, GetJWTDTO(null))
            }
        }
        else{
            call.respond(HttpStatusCode.BadRequest, GetJWTDTO(null))
        }
    }

    post("/check-email") {
        val body = call.receive<CheckEmailRequestDTO>()

        val id = UserModel.getIdByEmail(body.email)
        if(id == null) {
            val code = (100000..999999).random().toString()
            CodeStorage.saveCode(body.email, code)
            val sendResult = EmailSender.sendVerificationCode(body.email, code)
            sendResult.onSuccess {
                call.respond(HttpStatusCode.OK, CheckResponseDTO(true))
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, CheckResponseDTO(false))
            }
        }
        else{
            call.respond(HttpStatusCode.OK, CheckResponseDTO(false))
        }
    }


    post("/send-code"){
        val body = call.receive<CheckEmailRequestDTO>()
        val id = UserModel.getIdByEmail(body.email)

        if(id != null) {
            val code = (100000..999999).random().toString()
            CodeStorage.saveCode(body.email, code)
            val sendResult = EmailSender.sendVerificationCode(body.email, code)
            sendResult.onSuccess {
                call.respond(HttpStatusCode.OK, CheckResponseDTO(true))
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, CheckResponseDTO(false))
            }
        }
        else{
            call.respond(HttpStatusCode.OK, CheckResponseDTO(false))
        }
    }

    post("/check-email-login"){
        val body = call.receive<CheckEmailRequestDTO>()
        val id = UserModel.getIdByEmail(body.email)

        if(id == null) {
            call.respond(HttpStatusCode.OK, CheckResponseDTO(false))
        }
        else{
            call.respond(HttpStatusCode.OK, CheckResponseDTO(true))
        }
    }

    post("/check-telephone") {
        val body = call.receive<CheckPhoneNumberRequestDTO>()

        val id = UserModel.getIdByEmail(body.phone)
        if(id == null) {
            // TODO отправка СМС на телефон
        }
        else{
            call.respond(HttpStatusCode.OK, CheckResponseDTO(false))
        }
        // то же самое и с телефоном

        call.respond(HttpStatusCode.OK, CheckResponseDTO(true))
    }

    post("/check-code") {
        val body = call.receive<CheckCodeRequestDTO>()

        if(body.email != null){
            val isValid = CodeStorage.verifyCode(body.email, body.code)
            if(isValid){
                call.respond(HttpStatusCode.OK, CheckResponseDTO(true))
            }
            else{
                call.respond(HttpStatusCode.OK, CheckResponseDTO(false))
            }
        }
        else if(body.phone != null){
            val isValid = CodeStorage.verifyCode(body.phone, body.code)
            if(isValid){
                call.respond(HttpStatusCode.OK, CheckResponseDTO(true))
            }
            else{
                call.respond(HttpStatusCode.OK, CheckResponseDTO(false))
            }
        }
        call.respond(HttpStatusCode.OK, CheckResponseDTO(true))
    }

    post("/login"){
        val body = call.receive<LoginUserRequestDTO>()
        val token: String?
        if (body.email == null && body.phone == null) {
            call.respond(HttpStatusCode.BadRequest, LoginResponseDTO(null))
            return@post
        }
        else if (body.phone == null && body.email != null) {
            val id = UserModel.getIdByEmail(body.email)
            if(id == null){
                call.respond(HttpStatusCode.BadRequest, LoginResponseDTO(null))
                return@post
            }
            else{
                if (UserModel.passwordIsValid(id, body.password)){
                    token = RefreshTokenGenerator.generate()
                    SessionTable.addNewSession(id, token, body.deviceInfo)
                }
                else{
                    token = null
                }

            }

        }
        else if(body.phone != null && body.email == null) {
            val id = UserModel.getIdByPhone(body.phone)
            if(id == null){
                call.respond(HttpStatusCode.BadRequest, LoginResponseDTO(null))
                return@post
            }
            else{
                if (UserModel.passwordIsValid(id, body.password)){
                    token = RefreshTokenGenerator.generate()
                    SessionTable.addNewSession(id, token, body.deviceInfo)
                }
                else{
                    token = null
                }
            }
        }
        else {
            call.respond(HttpStatusCode.BadRequest, LoginResponseDTO(null))
            return@post
        }

        call.respond(HttpStatusCode.OK, LoginResponseDTO(token))
    }

    post("/register") {
        val body = call.receive<RegisterUserRequestDTO>()
        val token: String
        if (body.email == null && body.phone == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        else if (body.phone == null && body.email != null){
            token = RefreshTokenGenerator.generate()

            val uid = UserModel.registerNewUserWithEmail(body.email, body.password)
            SessionTable.addNewSession(uid, token, body.deviceInfo)
        }
        else if (body.email == null && body.phone != null) {
            token = RefreshTokenGenerator.generate()

            val uid = UserModel.registerNewUserWithPhone(body.phone, body.password)
            SessionTable.addNewSession(uid, token, body.deviceInfo)
        }
        else{
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        call.respond(HttpStatusCode.OK, LoginResponseDTO(token))
    }
}