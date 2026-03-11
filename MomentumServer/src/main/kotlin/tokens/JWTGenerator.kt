package com.example.tokens

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import java.util.UUID

class JwtService(
    private val issuer: String,
    private val audience: String,
    private val secret: String
) {
    private val algorithm = Algorithm.HMAC256(secret)

    fun createAccessToken(
        userId: String,
        sessionId: String,
        tokenVersion: Int = 1,
        ttlMillis: Long = 15 * 60 * 1000 // 15 минут
    ): String {
        val now = System.currentTimeMillis()

        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId)
            .withClaim("sid", sessionId)
            .withClaim("ver", tokenVersion)
            .withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + ttlMillis))
            .sign(algorithm)
    }
}