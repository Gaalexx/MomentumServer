package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.codahale.metrics.*
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
import java.util.concurrent.TimeUnit
import org.slf4j.event.*

fun Application.configureSecurity() {
    val jwtConfig = environment.config.config("jwt")
    val jwtAudience = jwtConfig.property("jwt.audience").getString()
    val jwtDomain = jwtConfig.property("jwt.domain").getString()
    val jwtRealm = jwtConfig.property("jwt.realm").getString()
    val jwtSecret = jwtConfig.property("jwt.secret").getString()
    authentication {
        jwt("jwt") {
            realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtDomain)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) JWTPrincipal(credential.payload) else null
            }
            challenge { defaultScheme, realm -> call.respond(HttpStatusCode.Unauthorized, "Wrong token!") }
        }
    }
}
