package com.example

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    Database.connect("jdbc:postgresql://localhost:55000/Momentum", driver = "org.postgresql.Driver", user = "app", password = "app")
    configureHTTP()
    configureMonitoring()
    configureSecurity()
    configureSerialization()
    configureRouting()
}
