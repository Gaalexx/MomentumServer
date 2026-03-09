package com.example.data.hashers

import org.mindrot.jbcrypt.BCrypt

class PasswordHasher: IHasher {
    override fun hash(toHash: String): String {
        return BCrypt.hashpw(toHash, BCrypt.gensalt())
    }

    override fun compareWithHashed(notHashed: String, hashed: String): Boolean {
        return BCrypt.checkpw(notHashed, hashed)
    }
}