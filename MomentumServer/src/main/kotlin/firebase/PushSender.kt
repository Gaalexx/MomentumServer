package com.example.firebase

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification

object PushSender {
    fun sendToToken(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): String {
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

        return FirebaseMessaging.getInstance().send(message)
    }
}