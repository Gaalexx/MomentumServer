package com.example

import com.example.tokens.JwtSettings
import com.example.AppConfig
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.testcontainers.containers.PostgreSQLContainer


class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application {
            module(TestAppConfig.create())
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }
}