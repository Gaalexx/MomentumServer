package com.example.database

import com.example.Models.ReactionsModel
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

object ReactionsTable : Table(name = "reactions") {
    private val id = uuid("id").uniqueIndex()
    private val userId = uuid("user_id")
    private val postId = uuid("post_id")
    private val reactionType = varchar("reaction_type", length = 50)
    private val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)

    fun insertNewReaction(reaction: ReactionsModel) {
        transaction {
            ReactionsTable.insert {
                it[id] = reaction.id
                it[userId] = reaction.userId
                it[postId] = reaction.postId
                it[reactionType] = reaction.reactionType
            }
        }
    }

    fun getMyPostReactions(postId: UUID): List<ReactionsModel> = transaction {
            ReactionsTable
                .selectAll()
                .where { ReactionsTable.postId eq postId }
                .map { row ->
                    ReactionsModel(
                        id = row[ReactionsTable.id],
                        userId = row[ReactionsTable.userId],
                        postId = row[ReactionsTable.postId],
                        reactionType = row[ReactionsTable.reactionType],
                        createdAt = row[ReactionsTable.createdAt].toString(),
                    )
                }
        }

    fun getAllPostReactions(userId: UUID, postId: UUID): List<ReactionsModel> = transaction {
            ReactionsTable
                .selectAll()
                .where { (ReactionsTable.postId eq postId) and (ReactionsTable.userId eq userId) }
                .map { row ->
                    ReactionsModel(
                        id = row[ReactionsTable.id],
                        userId = row[ReactionsTable.userId],
                        postId = row[ReactionsTable.postId],
                        reactionType = row[ReactionsTable.reactionType],
                        createdAt = row[ReactionsTable.createdAt].toString(),
                    )
                }
        }

    fun deleteReaction(reactionId: UUID) {
        transaction {
            ReactionsTable.deleteWhere { ReactionsTable.id eq reactionId }
        }
    }
}