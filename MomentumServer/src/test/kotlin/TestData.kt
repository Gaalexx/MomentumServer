package com.example

import com.example.database.UserModel
import com.example.database.SettingsTable
import com.example.database.AvatarsTable
import com.example.database.FriendRequests
import com.example.database.Friendships
import com.example.Models.AvatarsModel
import com.example.tokens.JwtService
import com.example.routing.compareUuidAsPostgres
import kotlinx.coroutines.runBlocking
import java.util.UUID

object TestData {
    val jwtService = JwtService("test", "test", "test-secret")

    fun createUser(email: String, password: String = "secret"): UUID {
        val userId = UserModel.registerNewUserWithEmail(email, password)
        SettingsTable.createDefaultSettings(userId)
        return userId
    }

    fun createAvatar(userId: UUID, isActive: Boolean = true): UUID = runBlocking {
        val avatarId = UUID.randomUUID()
        AvatarsTable.insertNewAvatars(
            AvatarsModel(
                id = avatarId,
                userId = userId,
                mimeType = "image/png",
                isActive = isActive,
                objectKey = "avatars/$userId/$avatarId",
                sizeBytes = 1024
            )
        )
        avatarId
    }

    fun createFriendRequest(fromUserId: UUID, toUserId: UUID) {
        org.jetbrains.exposed.sql.transactions.transaction {
            FriendRequests.createOrProcessRequest(fromUserId, toUserId) { a, b ->
                if (a < b) a to b else b to a
            }
        }
    }

    fun createFriendship(userId1: UUID, userId2: UUID) {
        val (first, second) = if (compareUuidAsPostgres(userId1, userId2) <= 0) {
            userId1 to userId2
        } else {
            userId2 to userId1
        }
        org.jetbrains.exposed.sql.transactions.transaction {
            Friendships.createIfNotExists(first, second)
        }
    }

    fun generateToken(userId: UUID, sessionId: UUID = UUID.randomUUID()): String =
        jwtService.createAccessToken(userId.toString(), sessionId.toString())
}