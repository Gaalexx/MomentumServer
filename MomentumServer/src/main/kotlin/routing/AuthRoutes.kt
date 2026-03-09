package com.example.routing

import com.example.email.EmailSender

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.Models.CheckCodeRequestDTO
import com.example.Models.CheckEmailRequestDTO
import com.example.Models.CheckPhoneNumberRequestDTO
import com.example.Models.CheckResponseDTO
import com.example.Models.LoginResponseDTO
import com.example.Models.LoginUserRequestDTO
import com.example.Models.RegisterUserRequestDTO
import com.example.database.UserModel
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

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

object CodeStorage {
    private val codes = mutableMapOf<String, CodeEntry>()
    private val mutex = Mutex()
    private const val CODE_TTL_MS = 5 * 60 * 1000L // 5 minutes

    private var cleanupJob: Job? = null

    data class CodeEntry(
        val code: String,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - createdAt > CODE_TTL_MS
    }

    suspend fun saveCode(email: String, code: String) {
        mutex.withLock {
            codes[email] = CodeEntry(code)
        }
    }

    suspend fun verifyCode(email: String, inputCode: String): Boolean =
        mutex.withLock {
            val entry = codes[email] ?: return@withLock false

            if (entry.isExpired()) {
                codes.remove(email)
                return@withLock false
            }

            if (entry.code == inputCode) {
                codes.remove(email)
                return@withLock true
            }

            false
        }

    fun startCleanupScheduler(scope: CoroutineScope) {
        if (cleanupJob != null) return

        cleanupJob = scope.launch {
            while (isActive) {
                delay(60_000)
                removeExpiredCodes()
            }
        }
    }

    fun stopCleanup() {
        cleanupJob?.cancel()
        cleanupJob = null
    }

    private suspend fun removeExpiredCodes() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val expiredEmails = codes.filterValues {
                now - it.createdAt > CODE_TTL_MS
            }.keys
            expiredEmails.forEach { codes.remove(it) }
        }
    }

    suspend fun getSize(): Int = mutex.withLock { codes.size }
}


fun Route.authRoutes() {
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
        val token: String
        // сравнить пароли и войти
        // может быть позже положу uuid и hasPremium под jwt
        if (body.email == null && body.phone == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        else if (body.phone == null && body.email != null) {
            val id = UserModel.getIdByEmail(body.email)
            if(id == null){
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            token = JWT.create()
                .withIssuer(jwtDomain)
                .withAudience(jwtAudience)
                .withClaim("email", body.email)
                .sign(Algorithm.HMAC256(jwtSecret))
        }
        else if(body.phone != null && body.email == null) {
            val id = UserModel.getIdByPhone(body.phone)
            if(id == null){
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            token = JWT.create()
                .withIssuer(jwtDomain)
                .withAudience(jwtAudience)
                .withClaim("phone", body.phone)
                .sign(Algorithm.HMAC256(jwtSecret))
        }
        else {
            call.respond(HttpStatusCode.BadRequest)
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
            token = JWT.create()
                .withIssuer(jwtDomain)
                .withAudience(jwtAudience)
                .withClaim("email", body.email)
                .sign(Algorithm.HMAC256(jwtSecret))

            UserModel.registerNewUserWithEmail(body.email, body.password)
        }
        else if (body.email == null && body.phone != null) {
            token = JWT.create()
                .withIssuer(jwtDomain)
                .withAudience(jwtAudience)
                .withClaim("phone", body.phone)
                .sign(Algorithm.HMAC256(jwtSecret))

            UserModel.registerNewUserWithPhone(body.phone, body.password)
        }
        else{
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        call.respond(HttpStatusCode.OK, LoginResponseDTO(token))
    }
}