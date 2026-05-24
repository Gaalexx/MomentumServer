package com.example.database

import com.example.Models.ServerSettingsStateDTO
import com.example.Models.SettingsBooleanDTO
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.get
import java.util.*


object SettingsTable : Table(name = "settings") {

    private val userId = uuid("user_id")
    private val inAppNotifications = bool("in_app_notifications").default(true)
    private val publicationsEnabled = bool("publications_enabled").default(true)
    private val reactionsEnabled = bool("reactions_enabled").default(true)
    private val friendRequestEnabled = bool("friend_request_enabled").default(true)
    private val defaultThemeEnabled = bool("default_theme_enabled").default(true)

    override val primaryKey = PrimaryKey(userId)

    fun changeInAppNotifications(curUserId: UUID, newValue: Boolean): Boolean {
        return transaction {
            update({ userId eq curUserId }) {
                if (newValue){
                    it[inAppNotifications] = true
                    it[publicationsEnabled] = true
                    it[reactionsEnabled] = true
                    it[friendRequestEnabled] = true
                }
                else{
                    it[inAppNotifications] = false
                    it[publicationsEnabled] = false
                    it[reactionsEnabled] = false
                    it[friendRequestEnabled] = false
                }
            }
        } > 0
    }

    fun changePublicationsEnabled(curUserId: UUID, newValue: Boolean, settings: ServerSettingsStateDTO): Boolean {
        return transaction {

            update({ userId eq curUserId }) {
                if (newValue){
                    it[inAppNotifications] = true
                    it[publicationsEnabled] = true
                }
                else{
                    it[publicationsEnabled] = false
                    if (!settings.reactionsEnabled && !settings.friendRequestEnabled){
                        it[inAppNotifications] = false
                    }
                }
            }
        } > 0
    }

    fun changeReactionsEnabled(curUserId: UUID, newValue: Boolean, settings: ServerSettingsStateDTO): Boolean {
        return transaction {

            update({ userId eq curUserId }) {
                if (newValue){
                    it[inAppNotifications] = true
                    it[reactionsEnabled] = true
                }
                else{
                    it[reactionsEnabled] = false
                    if (!settings.publicationsEnabled && !settings.friendRequestEnabled){
                        it[inAppNotifications] = false
                    }
                }
            }
        } > 0
    }

    fun changeFriendRequestEnabled(curUserId: UUID, newValue: Boolean, settings: ServerSettingsStateDTO): Boolean {
        return transaction {

            update({ userId eq curUserId }) {
                if (newValue){
                    it[inAppNotifications] = true
                    it[friendRequestEnabled] = true
                }
                else{
                    it[friendRequestEnabled] = false
                    if (!settings.publicationsEnabled && !settings.reactionsEnabled){
                        it[inAppNotifications] = false
                    }
                }
            }
        } > 0
    }

    fun changeDefaultThemeEnabled(curUserId: UUID, newValue: Boolean): Boolean {
        return transaction {
            update({ userId eq curUserId }) {
                it[defaultThemeEnabled] = newValue
            }
        } > 0
    }

    fun getServerSettingsInfo(curUserId: UUID): ServerSettingsStateDTO? {
        return transaction {
            SettingsTable.selectAll()
                .where { userId eq curUserId }
                .map {
                    ServerSettingsStateDTO(
                        it[inAppNotifications],
                        it[publicationsEnabled],
                        it[reactionsEnabled],
                        it[friendRequestEnabled],
                        it[defaultThemeEnabled],
                    )
                }
                .singleOrNull()
        }
    }

    fun createDefaultSettings(curUserId: UUID){
        return transaction{
            insert {
                it[userId] = curUserId
                it[inAppNotifications] = true
                it[publicationsEnabled] = true
                it[reactionsEnabled] = true
                it[friendRequestEnabled] = true
                it[defaultThemeEnabled] = true
            }
        }
    }

    fun deleteAllSettings(userId: UUID): Boolean {
        return transaction {
            SettingsTable.deleteWhere { SettingsTable.userId eq userId }
        } > 0
    }
}
