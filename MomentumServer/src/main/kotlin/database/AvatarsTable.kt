package com.example.database

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

data class AvatarsModel(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val mimeType: String,
    val status: UploadingStatus? = null,
    val isActive: Boolean = false,
    val objectKey: String,
    val sizeBytes: Long,
)

object AvatarsTable : Table(name = "avatars") {
    private val id = uuid("id").uniqueIndex()
    private val userId = uuid("user_id")
    private val mimeType = varchar("mime_type", length = 50)
    private val status = varchar("status", length = 20).default("UPLOADING")
    private val isActive = bool("is_active").default(false)
    private val objectKey = varchar("object_key", length = 1024).uniqueIndex("ok_uq")
    private val sizeBytes = long("size_bytes")

    override val primaryKey = PrimaryKey(id)

    fun insertNewAvatars(avatar: AvatarsModel) {
        transaction {
            if (avatar.isActive) {
                AvatarsTable.update({
                    (AvatarsTable.userId) eq userId and (AvatarsTable.isActive eq true)
                }) {
                    it[isActive] = false
                }
            }

            AvatarsTable.insert {
                it[id] = avatar.id
                it[userId] = avatar.userId
                it[mimeType] = avatar.mimeType
                it[isActive] = avatar.isActive
                it[objectKey] = avatar.objectKey
                it[sizeBytes] = avatar.sizeBytes
            }
        }
    }

    fun reactivateAvatar(avatarId: UUID, userId: UUID) {
        transaction {
            AvatarsTable.update({
                (AvatarsTable.userId) eq userId and (AvatarsTable.isActive eq true)
            }) {
                it[isActive] = false
            }

            AvatarsTable.update({
                (AvatarsTable.userId) eq userId and (AvatarsTable.id eq avatarId)
            }) {
                it[isActive] = true
            }
        }
    }

    fun changeStatus(avatarId: UUID, status: UploadingStatus) {
        transaction {
            AvatarsTable.update({
                AvatarsTable.id eq avatarId
            }) {
                it[AvatarsTable.status] = status.value
            }
        }
    }

    fun getObjectKeyOfAvatar(avatarId: UUID): String? = transaction {
        AvatarsTable
            .select(AvatarsTable.objectKey)
            .where { AvatarsTable.id eq avatarId }
            .map{it[AvatarsTable.objectKey]}
            .singleOrNull()
    }

    fun getObjectKeyOfActiveAvatar(userId: UUID): String? = transaction {
        AvatarsTable
            .select(AvatarsTable.objectKey)
            .where { (AvatarsTable.userId eq userId) and (AvatarsTable.isActive eq true) }
            .map{it[AvatarsTable.objectKey]}
            .singleOrNull()
    }

    fun deleteAvatar(avatarId: UUID) {
        transaction {
            AvatarsTable.deleteWhere { AvatarsTable.id eq avatarId }
        }
    }
}