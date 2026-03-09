package com.example.data.codestorage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object CodeStorage {
    private val codes = mutableMapOf<String, CodeEntry>()
    private val mutex = Mutex()
    private const val CODE_TTL_MS = 5 * 60 * 1000L // 5 minutes

    private var cleanupJob: Job? = null

    data class CodeEntry(
        val code: String,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - createdAt > CODE_TTL_MS
    }

    suspend fun saveCode(email: String, code: String) {
        mutex.withLock {
            codes[email] = CodeEntry(code)
        }
    }

    suspend fun verifyCode(email: String, inputCode: String): Boolean =
        mutex.withLock {
            val entry = codes[email] ?: return@withLock false

            if (entry.isExpired()) {
                codes.remove(email)
                return@withLock false
            }

            if (entry.code == inputCode) {
                codes.remove(email)
                return@withLock true
            }

            false
        }

    fun startCleanupScheduler(scope: CoroutineScope) {
        if (cleanupJob != null) return

        cleanupJob = scope.launch {
            while (isActive) {
                delay(60_000)
                removeExpiredCodes()
            }
        }
    }

    fun stopCleanup() {
        cleanupJob?.cancel()
        cleanupJob = null
    }

    private suspend fun removeExpiredCodes() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val expiredEmails = codes.filterValues {
                now - it.createdAt > CODE_TTL_MS
            }.keys
            expiredEmails.forEach { codes.remove(it) }
        }
    }

    suspend fun getSize(): Int = mutex.withLock { codes.size }
}