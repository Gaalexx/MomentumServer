package com.example.routing


import com.example.Models.*
import com.example.database.SettingsTable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

suspend fun ApplicationCall.safeUserId(): UUID? {
    val principal = principal<JWTPrincipal>() ?: run{
            respond(
                HttpStatusCode.Unauthorized,
                SettingsActionDTO(false, "Invalid token")
            )
            return null
        }

    return try {
        UUID.fromString(principal.subject)
    } catch (e: Exception) {
        respond(
            HttpStatusCode.BadRequest,
            SettingsActionDTO(false, "Invalid user ID format")
        )
        return null
    }
}


fun Route.settingsRoutes() {

    authenticate("jwt") {

        post("/change-in-app-notifications") {

            val userId = call.safeUserId() ?: return@post

            val body = try {
                call.receive<ChangeInAppNotificationsDTO>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    SettingsActionDTO(false, "Invalid request body")
                )
            }

            val updated = try {
                SettingsTable.changeInAppNotifications(userId, body.inAppNotifications)
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    SettingsActionDTO(false, "Database error: ${e.message}")
                )
            }

            if (!updated) {
                return@post call.respond(
                    HttpStatusCode.NotFound,
                    SettingsActionDTO(false, "Settings not found")
                )
            }

            val newState = SettingsTable.getServerSettingsInfo(userId)
                ?: return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    SettingsActionDTO(false, "Failed to load updated settings")
                )

            call.respond(
                HttpStatusCode.OK,
                SettingsActionDTO(true, "Updated successfully", newState)
            )
        }

        post("/change-publications-enabled") {
            val userId = call.safeUserId() ?: return@post

            val body = try {
                call.receive<ChangePublicationsEnabledDTO>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    SettingsActionDTO(false, "Invalid request body")
                )
            }

            val updated = try {
                SettingsTable.changePublicationsEnabled(userId, body.publicationsEnabled)
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    SettingsActionDTO(false, "Database error: ${e.message}")
                )
            }

            if (!updated) {
                return@post call.respond(
                    HttpStatusCode.NotFound,
                    SettingsActionDTO(false, "Settings not found")
                )
            }

            val newState = SettingsTable.getServerSettingsInfo(userId)
                ?: return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    SettingsActionDTO(false, "Failed to load updated settings")
                )

            call.respond(
                HttpStatusCode.OK,
                SettingsActionDTO(true, "Updated successfully", newState)
            )
        }

        post("/change-reactions-enabled") {
            val userId = call.safeUserId() ?: return@post

            val body = try {
                call.receive<ChangeReactionsEnabledDTO>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    SettingsActionDTO(false, "Invalid request body")
                )
            }

            val updated = try {
                SettingsTable.changeReactionsEnabled(userId, body.reactionsEnabled)
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    SettingsActionDTO(false, "Database error: ${e.message}")
                )
            }

            if (!updated) {
                return@post call.respond(
                    HttpStatusCode.NotFound,
                    SettingsActionDTO(false, "Settings not found")
                )
            }

            val newState = SettingsTable.getServerSettingsInfo(userId)
                ?: return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    SettingsActionDTO(false, "Failed to load updated settings")
                )

            call.respond(
                HttpStatusCode.OK,
                SettingsActionDTO(true, "Updated successfully", newState)
            )
        }

        post("/change-recommend-to-contacts") {
            val userId = call.safeUserId() ?: return@post

            val body = try {
                call.receive<ChangeRecommendToContactsDTO>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    SettingsActionDTO(false, "Invalid request body")
                )
            }

            val updated = try {
                SettingsTable.changeRecommendToContacts(userId, body.recommendToContacts)
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    SettingsActionDTO(false, "Database error: ${e.message}")
                )
            }

            if (!updated) {
                return@post call.respond(
                    HttpStatusCode.NotFound,
                    SettingsActionDTO(false, "Settings not found")
                )
            }

            val newState = SettingsTable.getServerSettingsInfo(userId)
                ?: return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    SettingsActionDTO(false, "Failed to load updated settings")
                )

            call.respond(
                HttpStatusCode.OK,
                SettingsActionDTO(true, "Updated successfully", newState)
            )
        }

        post("/change-allow-add-from-anyone") {
            val userId = call.safeUserId() ?: return@post

            val body = try {
                call.receive<ChangeAllowAddFromAnyoneDTO>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    SettingsActionDTO(false, "Invalid request body")
                )
            }

            val updated = try {
                SettingsTable.changeAllowAddFromAnyone(userId, body.allowAddFromAnyone)
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    SettingsActionDTO(false, "Database error: ${e.message}")
                )
            }

            if (!updated) {
                return@post call.respond(
                    HttpStatusCode.NotFound,
                    SettingsActionDTO(false, "Settings not found")
                )
            }

            val newState = SettingsTable.getServerSettingsInfo(userId)
                ?: return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    SettingsActionDTO(false, "Failed to load updated settings")
                )

            call.respond(
                HttpStatusCode.OK,
                SettingsActionDTO(true, "Updated successfully", newState)
            )
        }

        post("/get-settings-info") {
            val userId = call.safeUserId() ?: return@post

            val settings = SettingsTable.getServerSettingsInfo(userId)
                ?: return@post call.respond(
                    HttpStatusCode.NotFound,
                    SettingsActionDTO(false, "Settings not found")
                )

            call.respond(
                HttpStatusCode.OK,
                SettingsActionDTO(true, "Settings loaded successfully", settings)
            )
        }

    }
}