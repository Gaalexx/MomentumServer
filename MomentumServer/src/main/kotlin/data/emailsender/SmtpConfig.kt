package com.example.data.emailsender

data class SmtpConfig(
    val host: String,
    val port: String,
    val username: String,
    val password: String,
    val fromEmail: String
)