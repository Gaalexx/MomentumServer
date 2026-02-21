package com.example.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.Models.CheckCodeRequestDTO
import com.example.Models.CheckEmailRequestDTO
import com.example.Models.CheckPhoneNumberRequestDTO
import com.example.Models.CheckResponseDTO
import com.example.Models.LoginResponseDTO
import com.example.Models.LoginUserRequestDTO
import com.example.Models.RegisterUserRequestDTO
import com.example.Models.User
import com.example.database.UserModel
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


fun Route.authRoutes() {
    val jwtConfig = environment.config.config("jwt")
    val jwtAudience = jwtConfig.property("jwt.audience").getString()
    val jwtDomain = jwtConfig.property("jwt.domain").getString()
    val jwtRealm = jwtConfig.property("jwt.realm").getString()
    val jwtSecret = jwtConfig.property("jwt.secret").getString()

    post("/check-email") {
        val body = call.receive<CheckEmailRequestDTO>()

        // проверка на присутствие почты в базе данных
        // пока что почта свободна

        call.respond(HttpStatusCode.OK, CheckResponseDTO(true))
    }

    post("/check-telephone") {
        val body = call.receive<CheckPhoneNumberRequestDTO>()

        // то же самое и с телефоном

        call.respond(HttpStatusCode.OK, CheckResponseDTO(true))
    }

    post("/check-code") {
        val body = call.receive<CheckCodeRequestDTO>()

        // проверка кода

        call.respond(HttpStatusCode.OK, CheckResponseDTO(true))
    }

    post("/login"){
        val body = call.receive<LoginUserRequestDTO>()
        val token: String
        // сравнить пароли и войти
        // может быть позже положу uuid и hasPremium под jwt
        if (body.email == null && body.phone == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        else if (body.phone == null){
            token = JWT.create()
                .withIssuer(jwtDomain)
                .withAudience(jwtAudience)
                .withClaim("email", body.email)
                .sign(Algorithm.HMAC256(jwtSecret))
        }
        else {
            token = JWT.create()
                .withIssuer(jwtDomain)
                .withAudience(jwtAudience)
                .withClaim("phone", body.phone)
                .sign(Algorithm.HMAC256(jwtSecret))
        }

        call.respond(HttpStatusCode.OK, LoginResponseDTO(token))
    }

    post("/register") {
        val body = call.receive<RegisterUserRequestDTO>()
        val token: String
        // добавить пользователя в бд
        if (body.email == null && body.phone == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        else if (body.phone == null){
            token = JWT.create()
                .withIssuer(jwtDomain)
                .withAudience(jwtAudience)
                .withClaim("email", body.email)
                .sign(Algorithm.HMAC256(jwtSecret))
        }
        else {
            token = JWT.create()
                .withIssuer(jwtDomain)
                .withAudience(jwtAudience)
                .withClaim("phone", body.phone)
                .sign(Algorithm.HMAC256(jwtSecret))
        }

        call.respond(HttpStatusCode.OK, LoginResponseDTO(token))
    }
}