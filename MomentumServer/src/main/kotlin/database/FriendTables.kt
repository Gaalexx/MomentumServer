package com.example.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.javatime.*
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.*
import com.example.data.hashers.IHasher
import com.example.data.hashers.PasswordHasher
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import org.mindrot.jbcrypt.BCrypt

object FriendRequests : Table("friend_requests") {
    val id = uuid("id").autoGenerate()
    val fromUserId = reference("from_user_id", UserModel.id)
    val toUserId = reference("to_user_id", UserModel.id)
    val status = varchar("status", 20).default("pending")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)

    val fromUserIndex = index("idx_friend_requests_from_user", false, fromUserId)
    val toUserIndex = index("idx_friend_requests_to_user", false, toUserId)
    val toUserStatusIndex = index("idx_friend_requests_to_user_status", false, toUserId, status)
    val fromUserStatusIndex = index("idx_friend_requests_from_user_status", false, fromUserId, status)
    val createdAtIndex = index("idx_friend_requests_created_at", false, createdAt)

    init {
        uniqueIndex("unique_friend_request", fromUserId, toUserId)
    }
}

object Friendships : Table("friendships") {
    val userId1 = reference("user_id1", UserModel.id)
    val userId2 = reference("user_id2", UserModel.id)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(userId1, userId2)

    val user1Index = index("idx_friendships_user1", false, userId1)
    val user2Index = index("idx_friendships_user2", false, userId2)
    val createdAtIndex = index("idx_friendships_created_at", false, createdAt)

    init {
        check("check_ordered") { userId1 less userId2 }
    }
}