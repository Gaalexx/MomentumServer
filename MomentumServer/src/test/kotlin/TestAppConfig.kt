package com.example

import com.example.tokens.JwtSettings

object TestAppConfig {
    fun create(
        dbUrl: String = TestDatabase.jdbcUrl,
        dbUser: String = TestDatabase.username,
        dbPassword: String = TestDatabase.password
    ) = AppConfig(
        dbUrl = dbUrl,
        dbUser = dbUser,
        dbPassword = dbPassword,
        jwt = JwtSettings("test", "test", "test", "test-secret"),
        firebaseCredentialsPath = null,
        migrationsLocation = "filesystem:../db/migration"
    )
}