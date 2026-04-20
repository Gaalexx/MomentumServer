package com.example

import com.example.routing.*
import com.example.tokens.JwtService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable


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
                ReactionsRootes(jwtService)
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
