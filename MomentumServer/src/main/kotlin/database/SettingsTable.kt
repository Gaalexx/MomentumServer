package com.example.database

import com.example.Models.ServerSettingsStateDTO
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.*


object SettingsTable : Table(name = "settings") {

    private val userId = uuid("user_id")
    private val inAppNotifications = bool("in_app_notifications").default(false)
    private val publicationsEnabled = bool("publications_enabled").default(false)
    private val reactionsEnabled = bool("reactions_enabled").default(false)
    private val recommendToContacts = bool("recommend_to_contacts").default(false)
    private val allowAddFromAnyone = bool("allow_add_from_anyone").default(false)

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

    fun changeRecommendToContacts(curUserId: UUID, newValue: Boolean): Boolean {
        return transaction {
            update({ userId eq curUserId }) {
                it[recommendToContacts] = newValue
            }
        } > 0
    }

    fun changeAllowAddFromAnyone(curUserId: UUID, newValue: Boolean): Boolean {
        return transaction {
            update({ userId eq curUserId }) {
                it[allowAddFromAnyone] = newValue
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
                        it[recommendToContacts],
                        it[allowAddFromAnyone],
                    )
                }
                .singleOrNull()
        }
    }

    fun createDefaultSettings(curUserId: UUID){
        return transaction{
            insert {
                it[userId] = curUserId
                it[inAppNotifications] = false
                it[publicationsEnabled] = false
                it[reactionsEnabled] = false
                it[recommendToContacts] = false
                it[allowAddFromAnyone] = false
            }
        }
    }
}
