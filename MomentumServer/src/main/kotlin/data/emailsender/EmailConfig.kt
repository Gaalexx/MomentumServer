package com.example.data.emailsender


object EmailConfig {
    val smtpConfig by lazy {
        SmtpConfig(
            host = System.getenv("SMTP_HOST") ?: "smtp.gmail.com",
            port = System.getenv("SMTP_PORT") ?: "587",
            username = System.getenv("SMTP_USERNAME") ?: error("SMTP_USERNAME not set"),
            password = System.getenv("SMTP_PASSWORD") ?: error("SMTP_PASSWORD not set"),
            fromEmail = System.getenv("SMTP_FROM") ?: System.getenv("SMTP_USERNAME") ?: error("SMTP_FROM not set")
        )
    }
}