package com.example.database

import com.example.Models.ServerSettingsStateDTO
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
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
                it[inAppNotifications] = newValue
            }
        } > 0
    }

    fun changePublicationsEnabled(curUserId: UUID, newValue: Boolean): Boolean {
        return transaction {
            update({ userId eq curUserId }) {
                it[publicationsEnabled] = newValue
            }
        } > 0
    }

    fun changeReactionsEnabled(curUserId: UUID, newValue: Boolean): Boolean {
        return transaction {
            update({ userId eq curUserId }) {
                it[reactionsEnabled] = newValue
            }
        } > 0
    }

    fun changeFriendRequestEnabled(curUserId: UUID, newValue: Boolean): Boolean {
        return transaction {
            update({ userId eq curUserId }) {
                it[friendRequestEnabled] = newValue
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
