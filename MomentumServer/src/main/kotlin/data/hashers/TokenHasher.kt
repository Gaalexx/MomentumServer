package com.example.data.hashers

import java.security.MessageDigest

class TokenHasher : IHasher {

    override fun hash(toHash: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toHash.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun compareWithHashed(notHashed: String, hashed: String): Boolean {
        return this.hash(notHashed) == hashed
    }
}