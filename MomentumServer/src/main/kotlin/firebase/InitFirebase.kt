package com.example.firebase

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.FileInputStream

fun initFirebase() {
    if (FirebaseApp.getApps().isNotEmpty()) return

    val credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        ?: error("GOOGLE_APPLICATION_CREDENTIALS is not set")

    FileInputStream(credentialsPath).use { serviceAccount ->
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build()

        FirebaseApp.initializeApp(options)
    }
}