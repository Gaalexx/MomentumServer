package com.example.database

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import org.mindrot.jbcrypt.BCrypt



data class User(
    val id: UUID,
    val username: String,
    val password: String,
    val email: String,
    val registerDate: LocalDateTime,
    val phoneNumber: String,
    val hasPremium: Boolean,
)

object UserModel : Table("users") {
    private val id = UserModel.uuid("id").uniqueIndex()
    private val password = varchar("password", 300)
    private val hasPremium = bool("hasPremium").default(false)
    private val registered_at = datetime("registered_at").default(LocalDateTime.now())
    private val telephone = varchar("telephone", 20)
    private val email = varchar("email", 255)
    private val username = varchar("username", 50)

    private fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    private fun checkPassword(password: String, hashed: String): Boolean {
        return BCrypt.checkpw(password, hashed)
    }

    fun registerNewUser(userEmail: String, userPassword: String) {
        transaction {
            UserModel.insert {
                it[id] = UUID.randomUUID()
                it[password] = hashPassword(userPassword)
                it[email] = userEmail
            }
        }
    }

    fun getFullUser(userId: UUID): User? {
        return transaction {
            UserModel
                .selectAll()
                .where { UserModel.id eq userId }
                .singleOrNull()
                ?.let { row ->
                    User(
                        id = row[UserModel.id],
                        username = row[UserModel.username],
                        password = row[UserModel.password],
                        email = row[UserModel.email],
                        registerDate = row[UserModel.registered_at],
                        phoneNumber = row[UserModel.telephone],
                        hasPremium = row[UserModel.hasPremium]
                    )
                }
        }
    }

    fun passwordIsValid(userId: UUID, password: String): Boolean {
        val hashedPassword = transaction {
            UserModel
                .select(UserModel.id eq userId)
                .map { it[UserModel.password] }
                .single()
        }
        return checkPassword(password, hashedPassword)
    }

    fun getIdByEmail(email: String): UUID? {
        return transaction {
            UserModel
                .select(UserModel.email eq email)
                .map { it[UserModel.id] }
                .singleOrNull()
        }
    }

    fun getIdByPhone(phone: String): UUID? {
        return transaction {
            UserModel
                .selectAll()
                .where { UserModel.telephone eq phone }
                .map { it[UserModel.id] }
                .singleOrNull()
        }
    }

    fun updatePremium(userId: UUID, premium: Boolean) {
        transaction {
            UserModel.update({ UserModel.id eq userId }) {
                it[hasPremium] = premium
            }
        }
    }

    fun updateTelephoneNumber(userId: UUID, newPhoneNumber: String) {
        transaction {
            UserModel.update({ UserModel.id eq userId }) {
                it[telephone] = newPhoneNumber
            }
        }
    }

    fun updateEmail(userId: UUID, newEmail: String) {
        transaction {
            UserModel.update({ UserModel.id eq userId }) {
                it[email] = newEmail
            }
        }
    }

    fun updateUsername(userId: UUID, newUsername: String) {
        transaction {
            UserModel.update({ UserModel.id eq userId }) {
                it[username] = newUsername
            }
        }
    }
}