package com.example

import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    Database.connect("jdbc:postgresql://localhost:55000/Momentum", driver = "org.postgresql.Driver", user = "app", password = "app")
    runMigrations()
    configureHTTP()
    configureMonitoring()
    configureSecurity()
    configureSerialization()
    configureRouting()
}

fun runMigrations() {
    Flyway.configure()
        .dataSource(
            "jdbc:postgresql://localhost:55000/Momentum",
            "app",
            "app"
        )
        .driver("org.postgresql.Driver")
        .load()
        .migrate()
}