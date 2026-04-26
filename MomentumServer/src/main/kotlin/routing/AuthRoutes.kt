package com.example.routing


import com.example.Models.*
import com.example.data.codestorage.CodeStorage
import com.example.data.emailsender.EmailSender
import com.example.database.SessionTable
import com.example.database.SettingsTable
import com.example.database.UserModel
import com.example.firebase.PushSender
import com.example.tokens.JwtService
import com.example.tokens.RefreshTokenGenerator
import io.ktor.http.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.authRoutes(jwtService: JwtService) {
    val jwtConfig = environment.config.config("jwt")
    val jwtAudience = jwtConfig.property("audience").getString()
    val jwtDomain = jwtConfig.property("domain").getString()
    val jwtRealm = jwtConfig.property("realm").getString()
    val jwtSecret = jwtConfig.property("secret").getString()

    authenticate("jwt"){
        post("/sync-push-token"){
            val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val body = call.receive<GetJWTDTO>()

            val userId = UUID.fromString(principal.subject)
            if(body.token != null){
                UserModel.addPushToken(userId, body.token)
                PushSender.sendToToken(body.token, "Вход", "Ты вошел в приложение")
                call.respond(HttpStatusCode.OK, GetJWTDTO(body.token))
                return@post
            }
            call.respond(HttpStatusCode.BadRequest, GetJWTDTO(null))
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

        val id = UserModel.getIdByPhone(body.phone)
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

    post("/check-login-code"){
        val body = call.receive<CheckCodeLoginRequestDTO>()

        if(body.email != null){
            val id = UserModel.getIdByEmail(body.email)
            val token = RefreshTokenGenerator.generate()


            val isValid = CodeStorage.verifyCode(body.email, body.code)
            if(isValid && id != null){
                SessionTable.addNewSession(id, token, body.deviceInfo)
                call.respond(HttpStatusCode.OK, CheckCodeLoginResponseDTO(true, token))
            }
            else{
                call.respond(HttpStatusCode.OK, CheckCodeLoginResponseDTO(false))
            }
            return@post
        }
        else if(body.phone != null){ //TODO дописать логику для телефона
            val id = UserModel.getIdByPhone(body.phone)
            val token = RefreshTokenGenerator.generate()

            val isValid = CodeStorage.verifyCode(body.phone, body.code)
            if(isValid){
                call.respond(HttpStatusCode.OK, CheckCodeLoginResponseDTO(true, token))
            }
            else{
                call.respond(HttpStatusCode.OK, CheckCodeLoginResponseDTO(false))
            }
            return@post
        }
        call.respond(HttpStatusCode.OK, CheckCodeLoginResponseDTO(false))

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
        val uid: UUID
        if (body.email == null && body.phone == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        else if (body.phone == null && body.email != null){
            token = RefreshTokenGenerator.generate()

            uid = UserModel.registerNewUserWithEmail(body.email, body.password)
            SessionTable.addNewSession(uid, token, body.deviceInfo)
        }
        else if (body.email == null && body.phone != null) {
            token = RefreshTokenGenerator.generate()

            uid = UserModel.registerNewUserWithPhone(body.phone, body.password)
            SessionTable.addNewSession(uid, token, body.deviceInfo)
        }
        else{
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        SettingsTable.createDefaultSettings(uid)

        call.respond(HttpStatusCode.OK, LoginResponseDTO(token))
    }

    post("/logout") {
        val request = try {
            call.receive<LogoutRequestDTO>()
        } catch (e: Exception) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                LogoutResponseDTO(false, "Invalid request format")
            )
        }

        val deleted = SessionTable.deleteSession(request.refreshToken)

        if (deleted) {
            call.respond(
                HttpStatusCode.OK,
                LogoutResponseDTO(true, "Successfully logged out")
            )
        } else {
            call.respond(
                HttpStatusCode.NotFound,
                LogoutResponseDTO(false, "Session not found or already expired")
            )
        }
    }
}