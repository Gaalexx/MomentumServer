package com.example.database


import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(PostsTable::class.java)

    private val id = uuid("id")
    private val userId = uuid("user_id")
    private val createdAt = timestampWithTimeZone("created_at")
    private val text = varchar("text", 120).nullable()
    private val inUse = bool("in_use").default(true)
    private val mediaId = uuid("media_id")

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

    fun deletePost(postId: UUID): Boolean = transaction {
        val rowsDeleted = PostsTable.deleteWhere { PostsTable.id eq postId }
        rowsDeleted > 0
    }

    fun getPostById(postId: UUID): PostModel? = transaction {
        PostsTable
            .selectAll()
            .where { PostsTable.id eq postId }
            .map { row ->
                PostModel(
                    id = row[PostsTable.id],
                    userId = row[PostsTable.userId],
                    title = row[PostsTable.text] ?: "",
                    inUse = row[PostsTable.inUse],
                    createdAt = row[PostsTable.createdAt].toString(),
                    mediaId = row[PostsTable.mediaId]
                )
            }
            .singleOrNull()
    }

    fun getPostByMediaId(mediaId: UUID): PostModel? = transaction {
        val posts = PostsTable
            .selectAll()
            .where { PostsTable.mediaId eq mediaId }
            .map { row ->
                PostModel(
                    id = row[PostsTable.id],
                    userId = row[PostsTable.userId],
                    title = row[PostsTable.text] ?: "",
                    inUse = row[PostsTable.inUse],
                    createdAt = row[PostsTable.createdAt].toString(),
                    mediaId = row[PostsTable.mediaId]
                )
            }
        if (posts.size > 1) {
            logger.warn("Multiple posts found for mediaId {}, using the first one", mediaId)
        }

        posts.firstOrNull()
    }

}
