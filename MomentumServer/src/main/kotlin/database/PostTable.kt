package com.example.database


import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

data class PostModel(
    val id: UUID,
    val userId: UUID,
    val title: String,
    val inUse: Boolean,
    val createdAt: String? = null,
    val mediaId: UUID
)

object PostsTable : Table("posts") {

    private val id = uuid("id")
    private val userId = uuid("user_id")
    private val createdAt = timestampWithTimeZone("created_at")
    private val text = varchar("text", 120).nullable()
    private val inUse = bool("in_use").default(true)
    private val mediaId = uuid("mediaId")

    override val primaryKey = PrimaryKey(id)

    fun insertNewPost(postModel: PostModel) {
        transaction {
            PostsTable.insert {
                it[id] = postModel.id
                it[userId] = postModel.userId
                it[text] = postModel.title
                it[inUse] = postModel.inUse
                it[mediaId] = postModel.mediaId
            }
        }
    }

    fun getPostsOfUser(userId: UUID): List<PostModel> = transaction {
            PostsTable.selectAll()
                .where { (PostsTable.userId eq userId) and (PostsTable.inUse eq true) }
                .map{ row ->
                    PostModel(
                        row[PostsTable.id],
                        row[PostsTable.userId],
                        row[PostsTable.text] ?: "",
                        row[PostsTable.inUse],
                        row[PostsTable.createdAt].toString(),
                        row[PostsTable.mediaId]
                    )
                }
        }

}