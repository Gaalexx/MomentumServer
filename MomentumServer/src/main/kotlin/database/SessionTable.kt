package com.example.database

import com.example.data.hashers.TokenHasher
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.time.Duration


data class SessionInfo(
    val userId: UUID,
    val sessionId: UUID,
    val refreshToken: String,
    val deviceInfo: String
)

object SessionTable : Table("sessions") {
    private val sessionId = uuid("session_id")
    private val userId = uuid("user_id")
    private val refreshTokenHash = varchar("refrash_token_hash", 72)
    private val expiresAt = timestamp("expires_at")
    private val revokedAt = timestamp("revoked_at")
    private val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    private val deviceInfo = varchar("device_info", 255)

    override val primaryKey = PrimaryKey(sessionId)

    private val tokenHasher = TokenHasher()

    init {
        foreignKey(userId to UserModel.id, name = "user_id_fk")
    }

    fun addNewSession(userId: UUID, token: String, deviceInfo: String) {
        transaction {
            SessionTable.insert {
                it[sessionId] = UUID.randomUUID()
                it[this.userId] = userId
                it[refreshTokenHash] = tokenHasher.hash(token)
                it[this.deviceInfo] = deviceInfo
                // TODO подумать, что во время положить
            }
        }
    }

    fun checkToken(token: String): Boolean { // TODO подумать как сделать лучше
        val sessionId = transaction {
            SessionTable.selectAll()
                .where { SessionTable.refreshTokenHash eq tokenHasher.hash(token) }
                .map{
                    it[SessionTable.sessionId] }
                .singleOrNull()
        }
        return sessionId != null
    }

}
