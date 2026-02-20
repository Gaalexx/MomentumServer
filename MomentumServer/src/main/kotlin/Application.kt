package com.example

import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {

    val url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:55000/Momentum"
    val user = System.getenv("DB_USER") ?: "app"
    val pass = System.getenv("DB_PASSWORD") ?: "app"

    Database.connect(url, driver = "org.postgresql.Driver", user = user, password = pass)
    runMigrations(url, user, pass)
    configureHTTP()
    configureMonitoring()
    configureSecurity()
    configureSerialization()
    configureRouting()
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