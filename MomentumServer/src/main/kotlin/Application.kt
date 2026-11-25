package com.example

import com.example.database.VerificationCodeRepository
import com.example.firebase.initFirebase
import com.example.tokens.JwtService
import com.example.tokens.JwtSettings
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

fun loadConfigFromEnv(environment: ApplicationEnvironment): AppConfig{
    val jwtConfig = environment.config.config("jwt")
    return AppConfig(
        dbUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:55000/Momentum",
        dbUser = System.getenv("DB_USER") ?: "app",
        dbPassword = System.getenv("DB_PASSWORD") ?: "app",
        jwt = JwtSettings(
            audience = jwtConfig.property("audience").getString(),
            domain = jwtConfig.property("domain").getString(),
            realm = jwtConfig.property("realm").getString(),
            secret = jwtConfig.property("secret").getString()
        ),
        firebaseCredentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS"),
        vkClientId = environment.config.propertyOrNull("vk.client_id")?.getString()
    )
}

fun Application.module(config: AppConfig = loadConfigFromEnv(environment)) {

    if (config.firebaseCredentialsPath != null) {
        initFirebase(config.firebaseCredentialsPath)
    }

    val jwtService = JwtService(config.jwt.domain, config.jwt.audience, config.jwt.secret)

    Database.connect(config.dbUrl, driver = "org.postgresql.Driver", user = config.dbUser, password = config.dbPassword)
    runMigrations(config.dbUrl, config.dbUser, config.dbPassword)

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
    configureSecurity(config.jwt)
    configureSerialization()
    configureRouting(jwtService, config.jwt)
}

fun runMigrations(url: String, user: String, pass: String, locations: String = "filesystem:../db/migration") {
    Flyway.configure()
        .dataSource(
            url,
            user,
            pass
        )
        .locations(locations)
        .driver("org.postgresql.Driver")
        .load()
        .migrate()
}
