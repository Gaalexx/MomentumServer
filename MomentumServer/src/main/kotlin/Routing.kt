package com.example

import com.example.routing.*
import com.example.tokens.JwtService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory


@Serializable
data class Respond(
    val text: String
)

fun Application.configureRouting(jwtService: JwtService) {
    val logger = LoggerFactory.getLogger("com.example.Routing")

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error(
                "Unhandled exception for {} {}",
                call.request.httpMethod.value,
                call.request.uri,
                cause
            )
            call.respondText(text = "500: ${cause::class.simpleName}" , status = HttpStatusCode.InternalServerError)
        }
    }
    routing {
        route("/api"){
            route("/momentum"){
                authRoutes(jwtService)
                s3Routes(jwtService)
                reactionsRoutes(jwtService)
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


                route("/users"){
                    usersInfoRouting(jwtService)
                }

                route("/settings"){
                    settingsRoutes()
                }
            }
        }
        get("/"){
            call.respond("Hello Momentum!")
        }
    }
}
