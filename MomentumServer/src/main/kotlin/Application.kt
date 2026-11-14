package com.example

import com.example.database.VerificationCodeRepository
import com.example.firebase.initFirebase
import com.example.tokens.JwtService
import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

data class JwtSettings(
    val audience: String,
    val domain: String,
    val realm: String,
    val secret: String
)

fun Application.loadJwtSettings(): JwtSettings {
    val jwtConfig = environment.config.config("jwt")

    return JwtSettings(
        audience = jwtConfig.property("audience").getString(),
        domain = jwtConfig.property("domain").getString(),
        realm = jwtConfig.property("realm").getString(),
        secret = jwtConfig.property("secret").getString()
    )
}

fun Application.module() {

    val url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:55000/Momentum"
    val user = System.getenv("DB_USER") ?: "app"
    val pass = System.getenv("DB_PASSWORD") ?: "app"

    val jwtConfig = loadJwtSettings()

    initFirebase()

    val jwtService = JwtService(jwtConfig.domain, jwtConfig.audience, jwtConfig.secret)

    Database.connect(url, driver = "org.postgresql.Driver", user = user, password = pass)
    runMigrations(url, user, pass)

    // launch of background cleaning
    launch {
        while (isActive) {
            delay(60_000)
            try {
                withContext(Dispatchers.IO) {
                    VerificationCodeRepository.removeExpired()
                }
            } catch (e: Exception) {
                println("[ERROR] Failed to remove expired verification codes: ${e.message}")
            }
        }
    }

    configureHTTP()
    configureMonitoring()
    configureSecurity(jwtConfig)
    configureSerialization()
    configureRouting(jwtService)
}

fun runMigrations(url: String, user: String, pass: String) {
    Flyway.configure()
        .dataSource(
            url,
            user,
            pass
        )
        .driver("org.postgresql.Driver")
        .load()
        .migrate()
}
