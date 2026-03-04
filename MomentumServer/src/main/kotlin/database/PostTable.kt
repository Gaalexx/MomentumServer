package com.example.database


import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

data class PostModel(
    val id: UUID,
    val userId: UUID,
    val title: String,
    val inUse: Boolean,
)

object PostsTable : Table("posts") {

    private val id = uuid("id")
    private val userId = uuid("user_id")
    private val createdAt = timestampWithTimeZone("created_at")
    private val text = varchar("text", 120).nullable()
    private val inUse = bool("in_use").default(true)

    override val primaryKey = PrimaryKey(id)

    fun insertNewPost(postModel: PostModel) {
        transaction {
            PostsTable.insert {
                it[id] = UUID.randomUUID()
                it[userId] = postModel.userId
                it[text] = postModel.title
                it[inUse] = postModel.inUse
            }
        }
    }
}