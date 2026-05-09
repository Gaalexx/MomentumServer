package com.example.routing


import com.example.Models.*
import com.example.data.codestorage.CodeStorage
import com.example.data.emailsender.EmailSender
import com.example.database.SessionTable
import com.example.database.SettingsTable
import com.example.database.UserModel
import com.example.database.UserModel.deleteAllUserData
import com.example.tokens.RefreshTokenGenerator
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

        get("/get-settings-info") {
            val userId = call.safeUserId() ?: return@get

            val settings = SettingsTable.getServerSettingsInfo(userId)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    SettingsActionDTO(false, "Settings not found")
                )

            call.respond(
                HttpStatusCode.OK,
                SettingsActionDTO(true, "Settings loaded successfully", settings)
            )
        }

        post("/check-password"){
            val userId = call.safeUserId() ?: return@post

            val body = try { call.receive<SettingsCheckPasswordDTO>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    SettingsBooleanDTO(false, "Invalid request body")
                )
            }

            if(userId == null){
                call.respond(HttpStatusCode.BadRequest,
                    SettingsBooleanDTO(false, "UserId doesn't exist"))
                return@post
            }
            else{
                if (!UserModel.passwordIsValid(userId, body.password)){
                    call.respond(HttpStatusCode.BadRequest,
                        SettingsBooleanDTO(false, "Wrong password"))
                    return@post
                }
            }

            call.respond(HttpStatusCode.OK,
                SettingsBooleanDTO(true, "Password checked successfully"))
            return@post
        }

        post("/send-code"){
            val userId = call.safeUserId() ?: return@post
            val email = UserModel.getEmailById(userId)?: return@post

            val code = (100000..999999).random().toString()
            CodeStorage.saveCode(email, code)
            val sendResult = EmailSender.sendVerificationCode(email, code)
            sendResult.onSuccess {
                call.respond(HttpStatusCode.OK, SettingsBooleanDTO(true, "Successfully sent"))
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, SettingsBooleanDTO(false, "Internal server error"))
            }
        }

        post("/check-code") {
            val body = call.receive<CheckCodeRequestDTO>()
            val userId = call.safeUserId() ?: return@post
            val email = UserModel.getEmailById(userId)?: return@post

            val isValid = CodeStorage.verifyCode(email, body.code)
            if(isValid){
                call.respond(HttpStatusCode.OK, SettingsBooleanDTO(true, "Successfully checked"))
            }
            else{
                call.respond(HttpStatusCode.OK, SettingsBooleanDTO(false, "Invalid code"))
            }
        }

        post("/delete-account") {
            val userId = call.safeUserId() ?: return@post

            val isDeleted = deleteAllUserData(userId)

            if(isDeleted){
                call.respond(HttpStatusCode.OK, SettingsBooleanDTO(true, "Successfully deleted"))
            }
            else{
                call.respond(HttpStatusCode.OK, SettingsBooleanDTO(false, "User not found"))
            }
        }
    }
}