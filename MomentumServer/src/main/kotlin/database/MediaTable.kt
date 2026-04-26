package com.example.database

import org.jetbrains.exposed.sql.*
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
)

object MediaTable : Table(name = "media") {
    private val id = uuid("id")
    private val userId = uuid("user_id")
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
            changeStatus(media.id, media.status)
        }
    }

    fun changeStatus(mediaId: UUID, status: UploadingStatus) {
        transaction {
            MediaTable.update({ MediaTable.id eq mediaId }) {
                it[MediaTable.status] = status.value
            }
        }
    }

    fun getObjectKeyOfPost(mediaId: UUID): String? = transaction {
            MediaTable
                .select(MediaTable.objectKey)
                .where { MediaTable.id eq mediaId }
                .map{it[MediaTable.objectKey]}
                .singleOrNull()
        }

    fun deleteMedia(mediaId: UUID): Boolean = transaction {
        val rowsDeleted = MediaTable.deleteWhere { MediaTable.id eq mediaId }
        rowsDeleted > 0
    }

    fun getMediaById(mediaId: UUID): MediaModel? = transaction {
        MediaTable
            .selectAll()
            .where { MediaTable.id eq mediaId }
            .map { row ->
                MediaModel(
                    id = row[MediaTable.id],
                    userId = row[MediaTable.userId],
                    mediaType = MediaType.valueOf(row[MediaTable.mediaType]),
                    mimeType = row[MediaTable.mimeType],
                    status = UploadingStatus.valueOf(row[MediaTable.status].uppercase()),
                    objectKey = row[MediaTable.objectKey],
                    sizeBytes = row[MediaTable.sizeBytes],
                    duration = row[MediaTable.durationMs]
                )
            }
            .singleOrNull()
    }
}
