package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.codahale.metrics.*
import com.example.routing.accountRoutes
import com.example.routing.authRoutes
import com.example.routing.friendsRoutes
import com.example.routing.s3Routes
import com.example.s3Client.MinioStorage
import com.example.s3Client.testMinioUpload
import com.example.tokens.JwtService
import io.ktor.http.*
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.metrics.dropwizard.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.slf4j.event.*


@Serializable
data class Respond(
    val text: String
)

fun Application.configureRouting(jwtService: JwtService) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause" , status = HttpStatusCode.InternalServerError)
        }
    }
    routing {
        route("/api"){
            route("/momentum"){
                authRoutes(jwtService)
                s3Routes(jwtService)
                accountRoutes(jwtService)
                get("/hello") {
                    call.respond(Respond("Hello World!"))
                }
                get("/w"){
                    call.respond(listOf(Respond("WWW"), Respond("WW"), Respond("W")))
                }
                get("/"){
                    call.respond("Hello Momentum!")
                }
                friendsRoutes(jwtService)
            }
        }
        get("/"){
            call.respond("Hello Momentum!")
        }
    }
}
