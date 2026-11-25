package com.example

import org.testcontainers.containers.PostgreSQLContainer

object TestDatabase {
    val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
        .withInitScript("init-test-db.sql")
        .apply { start() }

    val jdbcUrl: String get() = postgres.jdbcUrl
    val username: String get() = postgres.username
    val password: String get() = postgres.password
}