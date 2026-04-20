package com.example.database

import com.example.data.hashers.IHasher
import com.example.data.hashers.PasswordHasher
import org.jetbrains.exposed.sql.Alias
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import org.mindrot.jbcrypt.BCrypt
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll


data class User(
    val id: UUID,
    val username: String?,
    val password: String,
    val email: String,
    val registerDate: LocalDateTime,
    val phoneNumber: String?,
    val hasPremium: Boolean,
)

object UserModel : Table("users") {
    val id = UserModel.uuid("id").uniqueIndex()
    private val password = varchar("password", 300)
    private val hasPremium = bool("has_premium").default(false)
    private val registered_at = datetime("registered_at").default(LocalDateTime.now())
    private val telephone = varchar("telephone", 20).nullable()
    private val email = varchar("email", 255)
    private val username = varchar("username", 50).nullable()
    override val primaryKey = PrimaryKey(UserModel.id)


    private val passwordHasher = PasswordHasher()

    fun getUsernameColumn() = username
    fun getEmailColumn() = email

    fun ResultRow.getDisplayName(): String {
        val username = this[UserModel.username]
        return username ?: this[UserModel.email]
    }

    fun ResultRow.getDisplayNameWithEmailFallback(): String {
        return this[UserModel.username] ?: this[UserModel.email]
    }

    fun extractUsername(row: ResultRow, alias: Alias<UserModel>? = null): String? =
        row[alias?.get(username) ?: username]

    fun extractEmail(row: ResultRow, alias: Alias<UserModel>? = null): String =
        row[alias?.get(email) ?: email]

    fun extractPhoneNumber(row: ResultRow, alias: Alias<UserModel>? = null): String? =
        row[alias?.get(telephone) ?: telephone]

    fun extractHasPremium(row: ResultRow, alias: Alias<UserModel>? = null): Boolean =
        row[alias?.get(hasPremium) ?: hasPremium]

    fun getDisplayNameFromRow(row: ResultRow, alias: Alias<UserModel>? = null): String =
        if (alias == null) {
            row[username] ?: row[email]
        } else {
            row[alias[username]] ?: row[alias[email]]
        }

    fun existsUserById(userId: UUID): Boolean {
        return transaction {
            !UserModel
                .selectAll().where { UserModel.id eq userId }
                .empty()
        }
    }

    fun findIdByEmail(email: String): UUID? {
        return transaction {
            UserModel
                .selectAll()
                .where { UserModel.email eq email }
                .map { it[UserModel.id] }
                .singleOrNull()
        }
    }

    fun registerNewUserWithEmail(userEmail: String, userPassword: String): UUID {
        val userId = UUID.randomUUID()
        transaction {
            UserModel.insert {
                it[id] = userId
                it[password] = passwordHasher.hash(userPassword)
                it[email] = userEmail
            }
        }
        return userId
    }

    fun registerNewUserWithPhone(userPhone: String, userPassword: String): UUID {
        val userId = UUID.randomUUID()
        transaction {
            UserModel.insert {
                it[id] = userId
                it[password] = passwordHasher.hash(userPassword)
                it[telephone] = userPhone
            }
        }
        return userId
    }

    fun getUsername(userId: UUID): String? {
        return transaction {
            UserModel
                .selectAll()
                .where { UserModel.id eq userId }
                .map { it[UserModel.username] }
                .singleOrNull()
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
                .selectAll()
                .where { UserModel.id eq userId }
                .map { it[UserModel.password] }
                .single()
        }
        return passwordHasher.compareWithHashed(password, hashedPassword)
    }

    fun getIdByEmail(email: String): UUID? {
        return transaction {
            UserModel
                .selectAll()
                .where { UserModel.email eq email }
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

    fun getIdByUserName(username: String): UUID? {
        return transaction {
            UserModel
                .selectAll()
                .where { UserModel.username eq username }
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

    fun updateFullUser(userId: UUID, login: String?, email: String?, phoneNumber: String?) {
        transaction {
            UserModel.update({ UserModel.id eq userId }) {
                if (!login.isNullOrBlank()) it[this.username] = login
                if (!email.isNullOrBlank()) it[this.email] = email
                if (!phoneNumber.isNullOrBlank()) it[this.telephone] = phoneNumber
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

    fun deleteUser(userId: UUID) {
        transaction {
            UserModel.deleteWhere { UserModel.id eq userId }
        }
    }
}
