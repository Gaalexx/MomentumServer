package com.example.firebase

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory

data class PushSendResult(
    val isSuccess: Boolean,
    val messageId: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val shouldInvalidateToken: Boolean = false
)

object PushSender {
    private val logger = LoggerFactory.getLogger(PushSender::class.java)

    fun sendToToken(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): PushSendResult {
        val message = Message.builder()
            .setToken(token)
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .putAllData(data)
            .build()

        return try {
            val messageId = FirebaseMessaging.getInstance().send(message)
            PushSendResult(isSuccess = true, messageId = messageId)
        } catch (e: FirebaseMessagingException) {
            val errorCode = e.messagingErrorCode?.name ?: e.errorCode.toString()
            val shouldInvalidateToken = e.messagingErrorCode == MessagingErrorCode.UNREGISTERED ||
                e.messagingErrorCode == MessagingErrorCode.INVALID_ARGUMENT

            logger.warn(
                "Failed to send push to token {} with code {}",
                maskToken(token),
                errorCode,
                e
            )

            PushSendResult(
                isSuccess = false,
                errorCode = errorCode,
                errorMessage = e.message,
                shouldInvalidateToken = shouldInvalidateToken
            )
        } catch (e: Exception) {
            logger.error("Unexpected push sending failure for token {}", maskToken(token), e)
            PushSendResult(
                isSuccess = false,
                errorMessage = e.message
            )
        }
    }

    private fun maskToken(token: String): String {
        if (token.length <= 8) {
            return "***"
        }

        return "***${token.takeLast(8)}"
    }
}
