package com.example.database

import com.example.Models.AvatarsModel
import com.example.s3Client.S3Client
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

suspend fun getAvatarURL(id: UUID): String? {
    val key = AvatarsTable.getObjectKeyOfActiveAvatar(id)
    return key?.let { S3Client.getPresignedObjectUrl(it) }
}

object AvatarsTable : Table(name = "avatars") {
    private val id = uuid("id").uniqueIndex()
    internal val userId = uuid("user_id")
    private val mimeType = varchar("mime_type", length = 50)
    private val status = varchar("status", length = 20).default("UPLOADING")
    internal val isActive = bool("is_active").default(false)
    internal val objectKey = varchar("object_key", length = 1024).uniqueIndex("ok_uq")
    private val sizeBytes = long("size_bytes")

    override val primaryKey = PrimaryKey(id)

    suspend fun insertNewAvatars(avatar: AvatarsModel) = newSuspendedTransaction(Dispatchers.IO) {
        if (avatar.isActive) {
            AvatarsTable.update({
                (AvatarsTable.userId eq avatar.userId) and (AvatarsTable.isActive eq true)
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

    suspend fun reactivateAvatar(avatarId: UUID, userId: UUID) = newSuspendedTransaction(Dispatchers.IO) {
        AvatarsTable.update({
            (AvatarsTable.userId eq userId) and (AvatarsTable.isActive eq true)
        }) {
            it[isActive] = false
        }

        AvatarsTable.update({
            (AvatarsTable.userId eq userId) and (AvatarsTable.id eq avatarId)
        }) {
            it[isActive] = true
        }
    }

    suspend fun changeStatus(avatarId: UUID, status: UploadingStatus) = newSuspendedTransaction(Dispatchers.IO) {
        AvatarsTable.update({
            AvatarsTable.id eq avatarId
        }) {
            it[AvatarsTable.status] = status.value
        }
    }

    suspend fun getObjectKeyOfAvatar(avatarId: UUID): String? = newSuspendedTransaction(Dispatchers.IO) {
        AvatarsTable
            .select(AvatarsTable.objectKey)
            .where { AvatarsTable.id eq avatarId }
            .map { it[AvatarsTable.objectKey] }
            .singleOrNull()
    }

    suspend fun getObjectKeyOfActiveAvatar(userId: UUID): String? = newSuspendedTransaction(Dispatchers.IO) {
        AvatarsTable
            .select(AvatarsTable.objectKey)
            .where { (AvatarsTable.userId eq userId) and (AvatarsTable.isActive eq true) }
            .map { it[AvatarsTable.objectKey] }
            .singleOrNull()
    }

    suspend fun deleteAvatar(avatarId: UUID) = newSuspendedTransaction(Dispatchers.IO)
    {
        AvatarsTable.deleteWhere { AvatarsTable.id eq avatarId }
    }

    suspend fun getAvatarKeys(userId: UUID): List<String> = newSuspendedTransaction(Dispatchers.IO) {
        AvatarsTable.selectAll()
            .where { AvatarsTable.userId eq userId }
            .map { it[AvatarsTable.objectKey] }
    }

    suspend fun deleteAllAvatars(userId: UUID): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        AvatarsTable.deleteWhere { AvatarsTable.userId eq userId } > 0
    }

}