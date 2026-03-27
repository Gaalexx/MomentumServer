package com.example.Models

import kotlinx.serialization.Serializable

@Serializable
enum class FriendRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED;

    companion object {
        fun fromString(value: String): FriendRequestStatus? {
            return values().find { it.name.equals(value, ignoreCase = true) }
        }

        fun fromStringOrThrow(value: String): FriendRequestStatus {
            return fromString(value)
                ?: throw IllegalArgumentException("Invalid friend request status: $value")
        }
    }
}

@Serializable
enum class FriendRequestUpdateStatus {
    ACCEPTED,
    REJECTED,
    CANCELLED;

    companion object {
        fun fromString(value: String): FriendRequestUpdateStatus? {
            return values().find { it.name.equals(value, ignoreCase = true) }
        }
    }
}