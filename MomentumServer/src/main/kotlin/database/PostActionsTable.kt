package com.example.database

import com.example.Models.PostActionModel
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object PostActionsTable : Table(name = "post_actions") {
    private val id = uuid("id").uniqueIndex()
    private val userId = uuid("user_id")
    private val postId = uuid("post_id")
    private val actionType = varchar("action_type", length = 50)
    private val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)

    fun insertNewAction(action: PostActionModel) {
        transaction {
            PostActionsTable.insert {
                it[id] = action.id
                it[userId] = action.userId
                it[postId] = action.postId
                it[actionType] = action.actionType
            }
        }
    }

    fun getMyPostReactions(postId: UUID): List<PostActionModel> = transaction {
            PostActionsTable
                .selectAll()
                .where { (PostActionsTable.postId eq postId) and
                        (PostActionsTable.actionType neq "HIDE")
                }
                .asSequence()
                .map { row ->
                    PostActionModel(
                        id = row[PostActionsTable.id],
                        userId = row[PostActionsTable.userId],
                        postId = row[PostActionsTable.postId],
                        actionType = row[PostActionsTable.actionType],
                        createdAt = row[PostActionsTable.createdAt].toString(),
                    )
                }
                .toList()
        }

    fun getAllPostReactions(userId: UUID, postId: UUID): List<PostActionModel> = transaction {
            PostActionsTable
                .selectAll()
                .where { (PostActionsTable.postId eq postId) and
                        (PostActionsTable.userId eq userId) and
                        (PostActionsTable.actionType neq "HIDE")
                }
                .asSequence()
                .map { row ->
                    PostActionModel(
                        id = row[PostActionsTable.id],
                        userId = row[PostActionsTable.userId],
                        postId = row[PostActionsTable.postId],
                        actionType = row[PostActionsTable.actionType],
                        createdAt = row[PostActionsTable.createdAt].toString(),
                    )
                }
                .toList()
        }

    fun getHiddenPosts(userId: UUID) : List<String> = transaction {
        PostActionsTable
            .select(PostActionsTable.postId)
            .where{
                (PostActionsTable.userId eq userId) and (PostActionsTable.actionType eq "HIDE")
            }
            .map{ row ->
                row[PostActionsTable.postId].toString()
            }
    }

    fun deleteAction(userId: UUID, postId: UUID, actionType: String) {
        transaction {
            PostActionsTable.deleteWhere {
                (PostActionsTable.userId eq userId) and
                (PostActionsTable.postId eq postId) and
                (PostActionsTable.actionType eq actionType)
            }
        }
    }

    fun deleteAllActions(userId: UUID): Boolean {
        return transaction {
            PostActionsTable.deleteWhere { PostActionsTable.userId eq userId }
        } > 0
    }

    fun deleteActionsOnPosts(postIds: List<UUID>): Boolean {
        if (postIds.isEmpty()) return true

        return transaction {
            PostActionsTable.deleteWhere { PostActionsTable.postId inList postIds }
        } > 0
    }
}
