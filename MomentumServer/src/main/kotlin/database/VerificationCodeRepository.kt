package com.example.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import java.time.Instant

enum class CodeType(val dbValue: String) {
    EMAIL("email"),
    PHONE("phone");

    companion object {
        fun fromDb(value: String): CodeType? = entries.firstOrNull { it.dbValue == value }
    }
}

object VerificationCodeRepository : Table("verification_codes") {
    private val identifier = varchar("identifier", 255)
    private val codeType = varchar("code_type", 10)
    private val code = varchar("code", 10)
    private val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    private val expiresAt = timestamp("expires_at")
    private val attempts = integer("attempts").default(0)

    override val primaryKey = PrimaryKey(identifier)
    private const val CODE_TTL_SECONDS = 5 * 60L

    fun saveCode(identifierValue: String, type: CodeType, codeValue: String) {
        val now = Instant.now()
        val expires = now.plusSeconds(CODE_TTL_SECONDS)
        transaction {
            val updated = VerificationCodeRepository.update({ identifier eq identifierValue }) {
                it[codeType] = type.dbValue
                it[code] = codeValue
                it[createdAt] = now
                it[expiresAt] = expires
                it[attempts] = 0
            }
            if (updated == 0) {
                VerificationCodeRepository.insert {
                    it[identifier] = identifierValue
                    it[codeType] = type.dbValue
                    it[code] = codeValue
                    it[createdAt] = now
                    it[expiresAt] = expires
                }
            }
        }
    }

    fun verifyCode(identifierValue: String, type: CodeType, inputCode: String): Boolean {
        return transaction {
            val deleted = VerificationCodeRepository.deleteWhere {
                (identifier eq identifierValue) and
                        (codeType eq type.dbValue) and
                        (code eq inputCode) and
                        (expiresAt greater Instant.now())
            }
            deleted > 0
        }
    }

    fun removeExpired(): Int {
        return transaction {
            VerificationCodeRepository.deleteWhere {
                expiresAt less Instant.now()
            }
        }
    }
}