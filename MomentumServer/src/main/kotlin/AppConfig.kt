package com.example

import com.example.tokens.JwtSettings

data class AppConfig(
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val jwt: JwtSettings,
    val firebaseCredentialsPath: String?,
    val vkClientId: String? = null,
    val migrationsLocation: String = "filesystem:../db/migration"
)