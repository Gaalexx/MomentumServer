package com.example.database

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

enum class UploadingStatus(val value: String) {
    UPLOADING("UPLOADING"),
    READY("READY"),
    FAILED("FAILED")
}

enum class MediaType(val value: String) {
    IMAGE("IMAGE"),
    AUDIO("AUDIO"),
    VIDEO("VIDEO")
}

data class MediaModel(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val mediaType: MediaType,
    val mimeType: String,
    val status: UploadingStatus? = null,
    val objectKey: String,
    val sizeBytes: Long,
    val duration: Long? = null,
    val postId: UUID? = null,
)

object MediaTable : Table(name = "media") {
    private val id = uuid("id")
    private val userId = uuid("user_id")
    private val postId = uuid("post_id").nullable()
    private val mediaType = varchar("media_type", length = 16)
    private val mimeType = varchar("mime_type", length = 150)
    private val status = varchar("status", length = 20).default("UPLOADING")
    private val objectKey = varchar("object_key", length = 1024).uniqueIndex("ok_uq")
    private val sizeBytes = long("size_bytes")
    private val durationMs = long("duration_ms").nullable()

    override val primaryKey = PrimaryKey(id)

    fun insertNewMedia(media: MediaModel) {
        transaction {
            MediaTable.insert {
                it[id] = media.id
                it[userId] = media.userId
                it[mediaType] = media.mediaType.value
                it[mimeType] = media.mimeType
                it[objectKey] = media.objectKey
                it[sizeBytes] = media.sizeBytes
            }
        }
    }

    fun changeStatus(media: MediaModel) {
        if(media.status != null){
            transaction {
                MediaTable.update({ MediaTable.id eq media.userId}) {
                    it[status] = media.status.value
                }
            }
        }
    }

    fun addPostId(mediaId: UUID, postId: UUID) {
        transaction {
            MediaTable.update({ MediaTable.id eq mediaId}) {
                it[this.postId] = postId
            }
        }
    }

    fun getObjectKeyOfPost(postId: UUID): String = transaction {
            MediaTable
                .select(MediaTable.objectKey)
                .where { MediaTable.id eq postId }
                .single()[MediaTable.objectKey]
        }

}