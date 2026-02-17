package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.codahale.metrics.*
import com.example.routing.authRoutes
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
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit
import org.slf4j.event.*

@Serializable
data class Respond(
    val text: String
)

fun Application.configureRouting() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause" , status = HttpStatusCode.InternalServerError)
        }
    }
    routing {
        route("/api"){
            route("/momentum"){
                authRoutes()
                get("/hello") {
                    call.respond(Respond("Hello World!"))
                }
                get("/w"){
                    call.respond(listOf(Respond("WWW"), Respond("WW"), Respond("W")))
                }
            }
        }
        get("/"){
            call.respond("Hello Momentum!")
        }
    }
}
