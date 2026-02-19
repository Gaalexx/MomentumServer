package com.example.database

import com.example.Models.User
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object UserModel : Table("users") {
    private val id = UserModel.uuid("id").uniqueIndex()
    private val username = varchar("name", 20)
    private val password = varchar("password", 120)

    fun insert(user: User) {
        transaction {
            UserModel.insert {
                it[id] = UUID.randomUUID()
                it[username] = user.username
                it[password] = user.password
            }
        }
    }
}