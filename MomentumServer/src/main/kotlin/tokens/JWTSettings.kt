package com.example.tokens

data class JwtSettings(
    val audience: String,
    val domain: String,
    val realm: String,
    val secret: String
)