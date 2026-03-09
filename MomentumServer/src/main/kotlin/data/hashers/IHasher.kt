package com.example.data.hashers

interface IHasher{
    fun hash(toHash: String): String
    fun compareWithHashed(notHashed: String, hashed: String): Boolean
}