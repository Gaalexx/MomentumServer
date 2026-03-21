package com.example.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.Models.*
import com.example.data.codestorage.CodeStorage
import com.example.data.emailsender.EmailSender
import com.example.database.SessionTable
import com.example.database.UserModel
import com.example.database.FriendRequests
import com.example.database.Friendships
import com.example.tokens.JwtService
import com.example.tokens.RefreshTokenGenerator
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal

// Exposed imports
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

private fun compareUuidAsPostgres(a: UUID, b: UUID): Int {
    val msbCompare = java.lang.Long.compareUnsigned(a.mostSignificantBits, b.mostSignificantBits)
    if (msbCompare != 0) return msbCompare
    return java.lang.Long.compareUnsigned(a.leastSignificantBits, b.leastSignificantBits)
}

private fun orderedFriendPair(a: UUID, b: UUID): Pair<UUID, UUID> {
    return if (compareUuidAsPostgres(a, b) <= 0) a to b else b to a
}

fun Route.friendsRoutes(jwtService: JwtService) {
    val jwtConfig = environment.config.config("jwt")
    val jwtAudience = jwtConfig.property("audience").getString()
    val jwtDomain = jwtConfig.property("domain").getString()
    val jwtRealm = jwtConfig.property("realm").getString()
    val jwtSecret = jwtConfig.property("secret").getString()

    authenticate("jwt") {

        post("/friends/request/by-email") {
            val principal = call.principal<JWTPrincipal>()
            val fromUserId = principal?.payload?.subject
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            val request = call.receive<FriendRequestCreateByEmailDTO>()

            try {
                val toUserId = UserModel.findIdByEmail(request.email)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        FriendRequestActionDTO(false, "User with this email not found")
                    )

                val fromUUID = UUID.fromString(fromUserId)

                if (fromUUID == toUserId) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        FriendRequestActionDTO(false, "Cannot send friend request to yourself")
                    )
                }

                val result = transaction {
                    val outgoingPending = FriendRequests.selectAll()
                        .where {
                            (FriendRequests.fromUserId eq fromUUID) and
                                    (FriendRequests.toUserId eq toUserId) and
                                    (FriendRequests.status eq "pending")
                        }
                        .singleOrNull()

                    if (outgoingPending != null) {
                        return@transaction Pair("ALREADY_SENT", outgoingPending[FriendRequests.id])
                    }

                    val incomingPending = FriendRequests.selectAll()
                        .where {
                            (FriendRequests.fromUserId eq toUserId) and
                                    (FriendRequests.toUserId eq fromUUID) and
                                    (FriendRequests.status eq "pending")
                        }
                        .singleOrNull()

                    if (incomingPending != null) {
                        val (userId1, userId2) = orderedFriendPair(fromUUID, toUserId)

                        FriendRequests.update({ FriendRequests.id eq incomingPending[FriendRequests.id] }) {
                            it[status] = "accepted"
                            it[updatedAt] = LocalDateTime.now()
                        }

                        Friendships.insertIgnore {
                            it[Friendships.userId1] = userId1
                            it[Friendships.userId2] = userId2
                            it[Friendships.createdAt] = LocalDateTime.now()
                        }

                        return@transaction Pair("MIRROR_ACCEPTED", incomingPending[FriendRequests.id])
                    }

                    val existingRejected = FriendRequests.selectAll()
                        .where {
                            (FriendRequests.fromUserId eq fromUUID) and
                                    (FriendRequests.toUserId eq toUserId) and
                                    (FriendRequests.status inList listOf("rejected", "cancelled"))
                        }
                        .singleOrNull()

                    if (existingRejected != null) {
                        FriendRequests.update({ FriendRequests.id eq existingRejected[FriendRequests.id] }) {
                            it[status] = "pending"
                            it[updatedAt] = LocalDateTime.now()
                        }
                        return@transaction Pair("RESENT", existingRejected[FriendRequests.id])
                    }

                    val requestId = UUID.randomUUID()
                    val now = LocalDateTime.now()

                    FriendRequests.insert {
                        it[FriendRequests.id] = requestId
                        it[FriendRequests.fromUserId] = fromUUID
                        it[FriendRequests.toUserId] = toUserId
                        it[FriendRequests.status] = "pending"
                        it[FriendRequests.createdAt] = now
                        it[FriendRequests.updatedAt] = now
                    }

                    return@transaction Pair("CREATED", requestId)
                }

                val (action, requestId) = result

                val message = when (action) {
                    "ALREADY_SENT" -> "Friend request already sent"
                    "MIRROR_ACCEPTED" -> "Friend request accepted (mutual)"
                    "RESENT" -> "Friend request re-sent successfully"
                    else -> "Friend request sent successfully"
                }

                call.respond(
                    HttpStatusCode.Created,
                    FriendRequestActionDTO(true, message, requestId.toString())
                )

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    FriendRequestActionDTO(false, "Failed to send friend request: ${e.message}")
                )
            }
        }

        get("/friends/requests/incoming") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            val userUUID = UUID.fromString(userId)

            try {
                val incomingRequests = transaction {
                    FriendRequests
                        .join(UserModel, JoinType.INNER,
                            onColumn = FriendRequests.fromUserId,
                            otherColumn = UserModel.id)
                        .selectAll()
                        .where {
                            (FriendRequests.toUserId eq userUUID) and
                                    (FriendRequests.status eq "pending")
                        }
                        .orderBy(FriendRequests.createdAt to SortOrder.DESC)
                        .map { row ->
                            FriendRequestWithUserDetailsDTO(
                                id = row[FriendRequests.id].toString(),
                                fromUserId = row[FriendRequests.fromUserId].toString(),
                                toUserId = row[FriendRequests.toUserId].toString(),
                                status = row[FriendRequests.status],
                                createdAt = row[FriendRequests.createdAt].toInstant(ZoneOffset.UTC).toString(),
                                updatedAt = row[FriendRequests.updatedAt].toInstant(ZoneOffset.UTC).toString(),
                                fromUserUsername = UserModel.extractUsername(row),
                                toUserUsername = null
                            )
                        }
                }

                call.respond(HttpStatusCode.OK, incomingRequests)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    FriendRequestActionDTO(false, "Failed to get incoming requests: ${e.message}")
                )
            }
        }

        get("/friends/requests/outgoing") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            val userUUID = UUID.fromString(userId)

            try {
                val outgoingRequests = transaction {
                    FriendRequests
                        .join(UserModel, JoinType.INNER,
                            onColumn = FriendRequests.toUserId,
                            otherColumn = UserModel.id)
                        .selectAll()
                        .where {
                            (FriendRequests.fromUserId eq userUUID) and
                                    (FriendRequests.status eq "pending")
                        }
                        .orderBy(FriendRequests.createdAt to SortOrder.DESC)
                        .map { row ->
                            FriendRequestWithUserDetailsDTO(
                                id = row[FriendRequests.id].toString(),
                                fromUserId = row[FriendRequests.fromUserId].toString(),
                                toUserId = row[FriendRequests.toUserId].toString(),
                                status = row[FriendRequests.status],
                                createdAt = row[FriendRequests.createdAt].toInstant(ZoneOffset.UTC).toString(),
                                updatedAt = row[FriendRequests.updatedAt].toInstant(ZoneOffset.UTC).toString(),
                                fromUserUsername = null,
                                toUserUsername = UserModel.extractUsername(row)
                            )
                        }
                }

                call.respond(HttpStatusCode.OK, outgoingRequests)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    FriendRequestActionDTO(false, "Failed to get outgoing requests: ${e.message}")
                )
            }
        }

        delete("/friends/request/{requestId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            val requestId = call.parameters["requestId"]
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    FriendRequestActionDTO(false, "Request ID is required")
                )

            val userUUID = UUID.fromString(userId)
            val requestUUID = UUID.fromString(requestId)

            try {
                val deleted = transaction {
                    val request = FriendRequests.selectAll()
                        .where { FriendRequests.id eq requestUUID }
                        .singleOrNull() ?: return@transaction 0

                    if (request[FriendRequests.fromUserId] != userUUID) {
                        return@transaction -1
                    }

                    if (request[FriendRequests.status] != "pending") {
                        return@transaction -2
                    }

                    FriendRequests.deleteWhere { FriendRequests.id eq requestUUID }
                }

                when (deleted) {
                    0 -> call.respond(
                        HttpStatusCode.NotFound,
                        FriendRequestActionDTO(false, "Friend request not found")
                    )
                    -1 -> call.respond(
                        HttpStatusCode.Forbidden,
                        FriendRequestActionDTO(false, "You can only delete your own requests")
                    )
                    -2 -> call.respond(
                        HttpStatusCode.BadRequest,
                        FriendRequestActionDTO(false, "Can only delete pending requests")
                    )
                    else -> call.respond(
                        HttpStatusCode.OK,
                        FriendRequestActionDTO(true, "Friend request deleted successfully", requestId)
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    FriendRequestActionDTO(false, "Failed to delete: ${e.message}")
                )
            }
        }

        patch("/friends/request/{requestId}/accept") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
                ?: return@patch call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            val requestId = call.parameters["requestId"]
                ?: return@patch call.respond(
                    HttpStatusCode.BadRequest,
                    FriendRequestActionDTO(false, "Request ID is required")
                )

            val userUUID = UUID.fromString(userId)
            val requestUUID = UUID.fromString(requestId)

            try {
                val result = transaction {
                    val request = FriendRequests.selectAll()
                        .where { FriendRequests.id eq requestUUID }
                        .singleOrNull() ?: return@transaction "NOT_FOUND"

                    val fromUserId = request[FriendRequests.fromUserId]
                    val toUserId = request[FriendRequests.toUserId]
                    val currentStatus = request[FriendRequests.status]

                    if (userUUID != toUserId) {
                        return@transaction "FORBIDDEN"
                    }

                    if (currentStatus != "pending") {
                        return@transaction "INVALID_STATUS"
                    }

                    FriendRequests.update({ FriendRequests.id eq requestUUID }) {
                        it[status] = "accepted"
                        it[updatedAt] = LocalDateTime.now()
                    }

                    val (userId1, userId2) = orderedFriendPair(fromUserId, toUserId)
                    Friendships.insertIgnore {
                        it[Friendships.userId1] = userId1
                        it[Friendships.userId2] = userId2
                        it[Friendships.createdAt] = LocalDateTime.now()
                    }

                    return@transaction "SUCCESS"
                }

                when (result) {
                    "NOT_FOUND" -> call.respond(
                        HttpStatusCode.NotFound,
                        FriendRequestActionDTO(false, "Friend request not found")
                    )
                    "FORBIDDEN" -> call.respond(
                        HttpStatusCode.Forbidden,
                        FriendRequestActionDTO(false, "Only the recipient can accept the request")
                    )
                    "INVALID_STATUS" -> call.respond(
                        HttpStatusCode.Conflict,
                        FriendRequestActionDTO(false, "Request is not in pending status")
                    )
                    "SUCCESS" -> call.respond(
                        HttpStatusCode.OK,
                        FriendRequestActionDTO(true, "Friend request accepted successfully", requestId)
                    )
                    else -> call.respond(
                        HttpStatusCode.InternalServerError,
                        FriendRequestActionDTO(false, "Unknown error")
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    FriendRequestActionDTO(false, "Failed to accept friend request: ${e.message}")
                )
            }
        }

        patch("/friends/request/{requestId}/reject") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
                ?: return@patch call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            val requestId = call.parameters["requestId"]
                ?: return@patch call.respond(
                    HttpStatusCode.BadRequest,
                    FriendRequestActionDTO(false, "Request ID is required")
                )

            val userUUID = UUID.fromString(userId)
            val requestUUID = UUID.fromString(requestId)

            try {
                val result = transaction {
                    val request = FriendRequests.selectAll()
                        .where { FriendRequests.id eq requestUUID }
                        .singleOrNull() ?: return@transaction "NOT_FOUND"

                    val toUserId = request[FriendRequests.toUserId]
                    val currentStatus = request[FriendRequests.status]

                    if (userUUID != toUserId) {
                        return@transaction "FORBIDDEN"
                    }

                    if (currentStatus != "pending") {
                        return@transaction "INVALID_STATUS"
                    }

                    FriendRequests.update({ FriendRequests.id eq requestUUID }) {
                        it[status] = "rejected"
                        it[updatedAt] = LocalDateTime.now()
                    }

                    "SUCCESS"
                }

                when (result) {
                    "NOT_FOUND" -> call.respond(
                        HttpStatusCode.NotFound,
                        FriendRequestActionDTO(false, "Friend request not found")
                    )
                    "FORBIDDEN" -> call.respond(
                        HttpStatusCode.Forbidden,
                        FriendRequestActionDTO(false, "Only the recipient can reject the request")
                    )
                    "INVALID_STATUS" -> call.respond(
                        HttpStatusCode.Conflict,
                        FriendRequestActionDTO(false, "Request is not in pending status")
                    )
                    "SUCCESS" -> call.respond(
                        HttpStatusCode.OK,
                        FriendRequestActionDTO(true, "Friend request rejected successfully", requestId)
                    )
                    else -> call.respond(
                        HttpStatusCode.InternalServerError,
                        FriendRequestActionDTO(false, "Unknown error")
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    FriendRequestActionDTO(false, "Failed to reject friend request: ${e.message}")
                )
            }
        }

        delete("/friends/{friendId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            val friendId = call.parameters["friendId"]
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    FriendRequestActionDTO(false, "Friend ID is required")
                )

            val userUUID = UUID.fromString(userId)
            val friendUUID = UUID.fromString(friendId)

            try {
                val result = transaction {
                    val (id1, id2) = orderedFriendPair(userUUID, friendUUID)

                    val friendship = Friendships.selectAll()
                        .where {
                            (Friendships.userId1 eq id1) and (Friendships.userId2 eq id2)
                        }
                        .singleOrNull()

                    if (friendship == null) {
                        return@transaction "NOT_FOUND"
                    }

                    val friendRequest = FriendRequests.selectAll()
                        .where {
                            ((FriendRequests.fromUserId eq userUUID) and (FriendRequests.toUserId eq friendUUID)) or
                                    ((FriendRequests.fromUserId eq friendUUID) and (FriendRequests.toUserId eq userUUID))
                        }
                        .singleOrNull()

                    Friendships.deleteWhere {
                        (Friendships.userId1 eq id1) and (Friendships.userId2 eq id2)
                    }

                    if (friendRequest != null) {
                        FriendRequests.update({ FriendRequests.id eq friendRequest[FriendRequests.id] }) {
                            it[status] = "cancelled"
                            it[updatedAt] = LocalDateTime.now()
                        }
                    }

                    return@transaction "SUCCESS"
                }

                when (result) {
                    "NOT_FOUND" -> call.respond(
                        HttpStatusCode.NotFound,
                        FriendRequestActionDTO(false, "Friendship not found")
                    )
                    "SUCCESS" -> call.respond(
                        HttpStatusCode.OK,
                        FriendRequestActionDTO(true, "Friend removed successfully")
                    )
                    else -> call.respond(
                        HttpStatusCode.InternalServerError,
                        FriendRequestActionDTO(false, "Unknown error")
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    FriendRequestActionDTO(false, "Failed to remove friend: ${e.message}")
                )
            }
        }

        get("/friends") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            val userUUID = UUID.fromString(userId)

            try {
                val friends = transaction {
                    val friends1 = Friendships
                        .join(UserModel, JoinType.INNER,
                            onColumn = Friendships.userId2,
                            otherColumn = UserModel.id)
                        .selectAll()
                        .where { Friendships.userId1 eq userUUID }
                        .map { row ->
                            FriendshipResponseDTO(
                                userId = row[UserModel.id].toString(),
                                username = UserModel.extractUsername(row),
                                friendsSince = row[Friendships.createdAt].toInstant(ZoneOffset.UTC).toString()
                            )
                        }

                    val friends2 = Friendships
                        .join(UserModel, JoinType.INNER,
                            onColumn = Friendships.userId1,
                            otherColumn = UserModel.id)
                        .selectAll()
                        .where { Friendships.userId2 eq userUUID }
                        .map { row ->
                            FriendshipResponseDTO(
                                userId = row[UserModel.id].toString(),
                                username = UserModel.extractUsername(row),
                                friendsSince = row[Friendships.createdAt].toInstant(ZoneOffset.UTC).toString()
                            )
                        }

                    friends1 + friends2
                }.sortedByDescending { it.friendsSince }

                call.respond(HttpStatusCode.OK, FriendshipListDTO(friends))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    FriendRequestActionDTO(false, "Failed to get friends list: ${e.message}")
                )
            }
        }

        get("/hello2") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            call.respond("Hello Momentum! User: $userId")
        }
    }
}
