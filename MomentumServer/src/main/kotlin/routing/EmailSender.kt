package com.example.email

import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

data class SmtpConfig(
    val host: String,
    val port: String,
    val username: String,
    val password: String,
    val fromEmail: String
)

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

object EmailSender {
    suspend fun sendVerificationCode(recipientEmail: String, code: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val config = EmailConfig.smtpConfig

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", config.host)
                put("mail.smtp.port", config.port)
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(config.username, config.password)
                }
            })

            return@withContext try {
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(config.fromEmail))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail))
                    subject = "Код подтверждения"
                    setText("Ваш код подтверждения: $code")
                }

                Transport.send(message)
                Result.success(Unit)
            } catch (e: MessagingException) {
                Result.failure(e)
            }
        }
}