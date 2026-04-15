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

//Enum imports
import com.example.Models.FriendRequestStatus
import com.example.Models.FriendRequestUpdateStatus
import com.example.database.AvatarsTable
import com.example.s3Client.S3Client

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
                    FriendRequests.createOrProcessRequest(fromUUID, toUserId, ::orderedFriendPair)
                }

                val message = when (result.action) {
                    "ALREADY_SENT" -> "Friend request already sent"
                    "MIRROR_ACCEPTED" -> "Friend request accepted (mutual)"
                    "RESENT" -> "Friend request re-sent successfully"
                    "ALREADY_FRIENDS" -> "You are already friends"
                    else -> "Friend request sent successfully"
                }

                call.respond(
                    HttpStatusCode.Created,
                    FriendRequestActionDTO(true, message, result.requestId.toString())
                )

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    FriendRequestActionDTO(false, "Failed to send friend request: ${e.message}")
                )
            }
        }


        post("/friends/request/by-name") {
            val principal = call.principal<JWTPrincipal>()
            val fromUserId = principal?.payload?.subject
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            val request = call.receive<FriendRequestCreateByEmailDTO>()

            try {
                val toUserId = UserModel.getIdByUserName(request.email)
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
                    FriendRequests.createOrProcessRequest(fromUUID, toUserId, ::orderedFriendPair)
                }

                val message = when (result.action) {
                    "ALREADY_SENT" -> "Friend request already sent"
                    "MIRROR_ACCEPTED" -> "Friend request accepted (mutual)"
                    "RESENT" -> "Friend request re-sent successfully"
                    "ALREADY_FRIENDS" -> "You are already friends"
                    else -> "Friend request sent successfully"
                }

                call.respond(
                    HttpStatusCode.Created,
                    FriendRequestActionDTO(true, message, result.requestId.toString())
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
                    FriendRequests.getIncomingRequests(userUUID)
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
                    FriendRequests.getOutgoingRequests(userUUID)
                }

                call.respond(HttpStatusCode.OK, outgoingRequests)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    FriendRequestActionDTO(false, "Failed to get outgoing requests: ${e.message}")
                )
            }
        }

        get("/friends/requests/rejected") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            val userUUID = UUID.fromString(userId)
            val asSender = call.parameters["asSender"]?.toBoolean() ?: true

            try {
                val rejectedRequests = transaction {
                    FriendRequests.getRejectedOrCancelledRequests(userUUID, asSender)
                }

                call.respond(HttpStatusCode.OK, rejectedRequests)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    FriendRequestActionDTO(false, "Failed to get rejected requests: ${e.message}")
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
                    FriendRequests.deleteRequest(requestUUID, userUUID)
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
                    FriendRequests.acceptRequest(requestUUID, userUUID, ::orderedFriendPair)
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
                    FriendRequests.rejectRequest(requestUUID, userUUID)
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
                    Friendships.deleteFriendshipAndUpdateRequest(userUUID, friendUUID)
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
                    Friendships.getFriendsWithDetails(userUUID)
                }

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
