package com.example.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.javatime.*
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.ZoneOffset
import com.example.Models.FriendRequestWithUserDetailsDTO
import com.example.Models.FriendshipResponseDTO

//Enum imports
import com.example.Models.FriendRequestStatus
import com.example.Models.FriendRequestUpdateStatus
import org.jetbrains.exposed.sql.selectAll

data class FriendRequestResult(
    val action: String,  // "CREATED", "ALREADY_SENT", "MIRROR_ACCEPTED", "RESENT"
    val requestId: UUID
)

object FriendRequests : Table("friend_requests") {
    val id = uuid("id").autoGenerate()
    val fromUserId = reference("from_user_id", UserModel.id)
    val toUserId = reference("to_user_id", UserModel.id)
    val status = varchar("status", 20).default("pending")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)

    fun ResultRow.getStatusEnum(): FriendRequestStatus {
        return FriendRequestStatus.fromStringOrThrow(this[status])
    }

    val fromUserIndex = index("idx_friend_requests_from_user", false, fromUserId)
    val toUserIndex = index("idx_friend_requests_to_user", false, toUserId)
    val toUserStatusIndex = index("idx_friend_requests_to_user_status", false, toUserId, status)
    val fromUserStatusIndex = index("idx_friend_requests_from_user_status", false, fromUserId, status)
    val createdAtIndex = index("idx_friend_requests_created_at", false, createdAt)

    init {
        uniqueIndex("unique_friend_request", fromUserId, toUserId)
    }

    fun createOrProcessRequest(
        fromUserId: UUID,
        toUserId: UUID,
        orderedPairFunc: (UUID, UUID) -> Pair<UUID, UUID>
    ): FriendRequestResult {
        val outgoingRequest = selectAll()
            .where {
                (FriendRequests.fromUserId eq fromUserId) and
                        (FriendRequests.toUserId eq toUserId)
            }
            .singleOrNull()

        if (outgoingRequest != null) {
            val currentStatus = outgoingRequest.getStatusEnum()

            when (currentStatus) {
                FriendRequestStatus.PENDING -> {
                    return FriendRequestResult("ALREADY_SENT", outgoingRequest[FriendRequests.id])
                }
                FriendRequestStatus.REJECTED, FriendRequestStatus.CANCELLED -> {
                    update({ FriendRequests.id eq outgoingRequest[FriendRequests.id] }) {
                        it[status] = FriendRequestStatus.PENDING.name.lowercase()
                        it[updatedAt] = LocalDateTime.now()
                    }
                    return FriendRequestResult("RESENT", outgoingRequest[FriendRequests.id])
                }
                FriendRequestStatus.ACCEPTED -> {
                    return FriendRequestResult("ALREADY_FRIENDS", outgoingRequest[FriendRequests.id])
                }
            }
        }

        val incomingRequest = selectAll()
            .where {
                (FriendRequests.fromUserId eq toUserId) and
                        (FriendRequests.toUserId eq fromUserId)
            }
            .singleOrNull()

        if (incomingRequest != null) {
            val currentStatus = incomingRequest.getStatusEnum()

            when (currentStatus) {
                FriendRequestStatus.PENDING -> {
                    val (userId1, userId2) = orderedPairFunc(fromUserId, toUserId)

                    update({ FriendRequests.id eq incomingRequest[FriendRequests.id] }) {
                        it[status] = FriendRequestStatus.ACCEPTED.name.lowercase()
                        it[updatedAt] = LocalDateTime.now()
                    }

                    Friendships.createIfNotExists(userId1, userId2)

                    return FriendRequestResult("MIRROR_ACCEPTED", incomingRequest[FriendRequests.id])
                }
                FriendRequestStatus.REJECTED, FriendRequestStatus.CANCELLED -> {
                    deleteWhere { FriendRequests.id eq incomingRequest[FriendRequests.id] }
                    // Continue to create new request
                }
                FriendRequestStatus.ACCEPTED -> {
                    return FriendRequestResult("ALREADY_FRIENDS", incomingRequest[FriendRequests.id])
                }
            }
        }

        val requestId = UUID.randomUUID()
        val now = LocalDateTime.now()

        insert {
            it[FriendRequests.id] = requestId
            it[FriendRequests.fromUserId] = fromUserId
            it[FriendRequests.toUserId] = toUserId
            it[FriendRequests.status] = FriendRequestStatus.PENDING.name.lowercase()
            it[FriendRequests.createdAt] = now
            it[FriendRequests.updatedAt] = now
        }

        return FriendRequestResult("CREATED", requestId)
    }

    fun getIncomingRequests(userId: UUID): List<FriendRequestWithUserDetailsDTO> {
        val recipientUser = UserModel.selectAll()
            .where { UserModel.id eq userId }
            .singleOrNull()

        val recipientDisplayName = recipientUser?.let {
            UserModel.getDisplayNameFromRow(it)
        } ?: "Unknown User"

        return join(UserModel, JoinType.INNER,
            onColumn = FriendRequests.fromUserId,
            otherColumn = UserModel.id)
            .selectAll()
            .where {
                (FriendRequests.toUserId eq userId) and
                        (FriendRequests.status eq FriendRequestStatus.PENDING.name.lowercase())
            }
            .orderBy(FriendRequests.createdAt to SortOrder.DESC)
            .map { row ->
                FriendRequestWithUserDetailsDTO(
                    id = row[FriendRequests.id].toString(),
                    fromUserId = row[FriendRequests.fromUserId].toString(),
                    toUserId = row[FriendRequests.toUserId].toString(),
                    status = row.getStatusEnum(),
                    createdAt = row[FriendRequests.createdAt].toInstant(ZoneOffset.UTC).toString(),
                    updatedAt = row[FriendRequests.updatedAt].toInstant(ZoneOffset.UTC).toString(),
                    fromUserUsername = UserModel.getDisplayNameFromRow(row),
                    fromUserAvatarUrl = getAvatarURL(row[FriendRequests.fromUserId]),
                    toUserUsername = recipientDisplayName
                )
            }
    }

    fun getOutgoingRequests(userId: UUID): List<FriendRequestWithUserDetailsDTO> {
        val senderUser = UserModel.selectAll()
            .where { UserModel.id eq userId }
            .singleOrNull()

        val senderDisplayName = senderUser?.let {
            UserModel.getDisplayNameFromRow(it)
        } ?: "Unknown User"

        return join(UserModel, JoinType.INNER,
            onColumn = FriendRequests.toUserId,
            otherColumn = UserModel.id)
            .selectAll()
            .where {
                (FriendRequests.fromUserId eq userId) and
                        (FriendRequests.status eq FriendRequestStatus.PENDING.name.lowercase())
            }
            .orderBy(FriendRequests.createdAt to SortOrder.DESC)
            .map { row ->
                FriendRequestWithUserDetailsDTO(
                    id = row[FriendRequests.id].toString(),
                    fromUserId = row[FriendRequests.fromUserId].toString(),
                    toUserId = row[FriendRequests.toUserId].toString(),
                    status = row.getStatusEnum(),
                    createdAt = row[FriendRequests.createdAt].toInstant(ZoneOffset.UTC).toString(),
                    updatedAt = row[FriendRequests.updatedAt].toInstant(ZoneOffset.UTC).toString(),
                    fromUserUsername = senderDisplayName,
                    fromUserAvatarUrl = getAvatarURL(row[FriendRequests.fromUserId]),
                    toUserUsername = UserModel.getDisplayNameFromRow(row)
                )
            }
    }

    fun getRejectedOrCancelledRequests(
        userId: UUID,
        asSender: Boolean = true
    ): List<FriendRequestWithUserDetailsDTO> {
        val statuses = listOf(
            FriendRequestStatus.REJECTED.name.lowercase(),
            FriendRequestStatus.CANCELLED.name.lowercase()
        )

        val whereCondition = if (asSender) {
            FriendRequests.fromUserId eq userId
        } else {
            FriendRequests.toUserId eq userId
        }

        // Get the current user's details once
        val currentUser = UserModel.selectAll()
            .where { UserModel.id eq userId }
            .singleOrNull()

        val currentUserDisplayName = currentUser?.let {
            UserModel.getDisplayNameFromRow(it)
        } ?: "Unknown User"

        return if (asSender) {
            // For outgoing rejected/cancelled - current user is the sender
            join(UserModel, JoinType.INNER,
                onColumn = FriendRequests.toUserId,
                otherColumn = UserModel.id)
                .selectAll()
                .where { whereCondition and (FriendRequests.status inList statuses) }
                .orderBy(FriendRequests.createdAt to SortOrder.DESC)
                .map { row ->
                    FriendRequestWithUserDetailsDTO(
                        id = row[FriendRequests.id].toString(),
                        fromUserId = row[FriendRequests.fromUserId].toString(),
                        toUserId = row[FriendRequests.toUserId].toString(),
                        status = row.getStatusEnum(),
                        createdAt = row[FriendRequests.createdAt].toInstant(ZoneOffset.UTC).toString(),
                        updatedAt = row[FriendRequests.updatedAt].toInstant(ZoneOffset.UTC).toString(),
                        fromUserUsername = currentUserDisplayName,
                        fromUserAvatarUrl = getAvatarURL(row[FriendRequests.fromUserId]),
                        toUserUsername = UserModel.getDisplayNameFromRow(row)
                    )
                }
        } else {
            // For incoming rejected/cancelled - current user is the recipient
            join(UserModel, JoinType.INNER,
                onColumn = FriendRequests.fromUserId,
                otherColumn = UserModel.id)
                .selectAll()
                .where { whereCondition and (FriendRequests.status inList statuses) }
                .orderBy(FriendRequests.createdAt to SortOrder.DESC)
                .map { row ->
                    FriendRequestWithUserDetailsDTO(
                        id = row[FriendRequests.id].toString(),
                        fromUserId = row[FriendRequests.fromUserId].toString(),
                        toUserId = row[FriendRequests.toUserId].toString(),
                        status = row.getStatusEnum(),
                        createdAt = row[FriendRequests.createdAt].toInstant(ZoneOffset.UTC).toString(),
                        updatedAt = row[FriendRequests.updatedAt].toInstant(ZoneOffset.UTC).toString(),
                        fromUserUsername = UserModel.getDisplayNameFromRow(row),
                        fromUserAvatarUrl = getAvatarURL(row[FriendRequests.fromUserId]),
                        toUserUsername = currentUserDisplayName
                    )
                }
        }
    }

    fun deleteRequest(requestId: UUID, userId: UUID): Int {
        val request = selectAll()
            .where { FriendRequests.id eq requestId }
            .singleOrNull() ?: return 0

        if (request[FriendRequests.fromUserId] != userId) {
            return -1
        }

        if (request.getStatusEnum() != FriendRequestStatus.PENDING) {
            return -2
        }

        return deleteWhere { FriendRequests.id eq requestId }
    }

    fun acceptRequest(
        requestId: UUID,
        userId: UUID,
        orderedPairFunc: (UUID, UUID) -> Pair<UUID, UUID>
    ): String {
        val request = selectAll()
            .where { FriendRequests.id eq requestId }
            .singleOrNull() ?: return "NOT_FOUND"

        val fromUserId = request[FriendRequests.fromUserId]
        val toUserId = request[FriendRequests.toUserId]
        val currentStatus = request.getStatusEnum()

        if (userId != toUserId) {
            return "FORBIDDEN"
        }

        if (currentStatus != FriendRequestStatus.PENDING) {
            return "INVALID_STATUS"
        }

        update({ FriendRequests.id eq requestId }) {
            it[status] = FriendRequestStatus.ACCEPTED.name.lowercase()
            it[updatedAt] = LocalDateTime.now()
        }

        val (userId1, userId2) = orderedPairFunc(fromUserId, toUserId)
        Friendships.createIfNotExists(userId1, userId2)

        return "SUCCESS"
    }

    fun rejectRequest(requestId: UUID, userId: UUID): String {
        val request = selectAll()
            .where { FriendRequests.id eq requestId }
            .singleOrNull() ?: return "NOT_FOUND"

        val toUserId = request[FriendRequests.toUserId]
        val currentStatus = request.getStatusEnum()

        if (userId != toUserId) {
            return "FORBIDDEN"
        }

        if (currentStatus != FriendRequestStatus.PENDING) {
            return "INVALID_STATUS"
        }

        update({ FriendRequests.id eq requestId }) {
            it[status] = FriendRequestStatus.REJECTED.name.lowercase()
            it[updatedAt] = LocalDateTime.now()
        }

        return "SUCCESS"
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

    fun createIfNotExists(userId1: UUID, userId2: UUID) {
        insertIgnore {
            it[Friendships.userId1] = userId1
            it[Friendships.userId2] = userId2
            it[Friendships.createdAt] = LocalDateTime.now()
        }
    }

    fun getFriendsWithDetails(userId: UUID): List<FriendshipResponseDTO> {
//        val friends1 = join(UserModel, JoinType.INNER,
//            onColumn = Friendships.userId2,
//            otherColumn = UserModel.id)
//            .selectAll()
//            .where { Friendships.userId1 eq userId }
//            .map { row ->
//                val friendId = row[UserModel.id]
//                FriendshipResponseDTO(
//                    userId = friendId.toString(),
//                    username = UserModel.getDisplayNameFromRow(row),
//                    friendsSince = row[Friendships.createdAt].toInstant(ZoneOffset.UTC).toString(),
//                    userAvatarUrl = getAvatarURL(friendId)
//                )
//            }
//
//        val friends2 = join(UserModel, JoinType.INNER,
//            onColumn = Friendships.userId1,
//            otherColumn = UserModel.id)
//            .selectAll()
//            .where { Friendships.userId2 eq userId }
//            .map { row ->
//                val friendId = row[UserModel.id]
//                FriendshipResponseDTO(
//                    userId = friendId.toString(),
//                    username = UserModel.getDisplayNameFromRow(row),
//                    friendsSince = row[Friendships.createdAt].toInstant(ZoneOffset.UTC).toString(),
//                    userAvatarUrl = getAvatarURL(friendId)
//                )
//            }
//
//        return (friends1 + friends2).sortedByDescending { it.friendsSince }

        val friendProfile = UserModel.alias("friend_profile")

        val friends = Friendships
            .join(
                UserModel,
                JoinType.INNER,
                onColumn =
                    ((userId1 eq userId) and (userId2 eq friendProfile[UserModel.id])) or
                    ((userId2 eq userId) and (userId1 eq friendProfile[UserModel.id]))
            )
            .selectAll().where { (userId1 eq userId) or (userId2 eq userId) }
            .orderBy(createdAt to SortOrder.DESC)
            .map { row ->
                val friendId = row[friendProfile[UserModel.id]]
                FriendshipResponseDTO(
                    userId = friendId.toString(),
                    username = UserModel.getDisplayNameFromRow(row, friendProfile),
                    email = UserModel.extractEmail(row, friendProfile),
                    phoneNumber = UserModel.extractPhoneNumber(row, friendProfile),
                    friendsSince = row[createdAt].toInstant(ZoneOffset.UTC).toString(),
                    userAvatarUrl = getAvatarURL(friendId),
                    hasPremium = UserModel.extractHasPremium(row, friendProfile)
                )
            }


        return friends
    }

    fun deleteFriendshipAndUpdateRequest(userUUID: UUID, friendUUID: UUID): String {
        val (id1, id2) = if (userUUID < friendUUID) userUUID to friendUUID else friendUUID to userUUID

        val friendship = selectAll()
            .where { (Friendships.userId1 eq id1) and (Friendships.userId2 eq id2) }
            .singleOrNull() ?: return "NOT_FOUND"

        val friendRequest = FriendRequests.selectAll()
            .where {
                ((FriendRequests.fromUserId eq userUUID) and (FriendRequests.toUserId eq friendUUID)) or
                        ((FriendRequests.fromUserId eq friendUUID) and (FriendRequests.toUserId eq userUUID))
            }
            .singleOrNull()

        deleteWhere {
            (Friendships.userId1 eq id1) and (Friendships.userId2 eq id2)
        }

        if (friendRequest != null) {
            FriendRequests.update({ FriendRequests.id eq friendRequest[FriendRequests.id] }) {
                it[FriendRequests.status] = FriendRequestStatus.CANCELLED.name.lowercase()
                it[FriendRequests.updatedAt] = LocalDateTime.now()
            }
        }

        return "SUCCESS"
    }
}