package com.example.data.emailsender

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

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