package com.example.s3Client

import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.http.Method
import java.io.ByteArrayInputStream
import java.time.Duration
import java.util.concurrent.TimeUnit

interface Storage{
    fun putObjectBytes(key: String, bytes: ByteArray, contentType: String = "application/octet-stream")
    fun presignPutUrl(key: String, expires: Duration = Duration.ofMinutes(2)): String
    fun getPresignedObjectUrl(objectKey: String, expiresInMinutes: Int = 15): String
    fun deleteObject(key: String)
}

class MinioStorage(
    private val endpoint: String = System.getenv("S3_HOST") ?: "http://localhost:8080",
    private val accessKey: String = System.getenv("S3_ACCESS_KEY") ?: "minio-access-key",
    private val secretKey: String = System.getenv("S3_SECRET_KEY") ?: "minio-secret-key",
    private val bucket: String = System.getenv("S3_ID") ?: "minio-bucket",
    private val region: String = System.getenv("S3_REGION") ?: "ru-1",
) : Storage {
    /*private*/ val minio: MinioClient = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .region(region)
        .build()

    override fun putObjectBytes(key: String, bytes: ByteArray, contentType: String) {
        ByteArrayInputStream(bytes).use { input ->
            minio.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(key)
                    .stream(input, bytes.size.toLong(), -1)
                    .contentType(contentType)
                    .build()
            )
        }
    }

    override fun presignPutUrl(
        key: String,
        expires: Duration
    ): String {
        return minio.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(bucket)
                .`object`(key)
                .expiry(expires.toSeconds().toInt())
                .build()
        )
    }

    override fun getPresignedObjectUrl(objectKey: String, expiresInMinutes: Int): String {
        return minio.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .`object`(objectKey)
                .expiry(expiresInMinutes, TimeUnit.MINUTES)
                .build()
        )
    }

    override fun deleteObject(key: String) {
        try {
            minio.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(key)
                    .build()
            )
        } catch (e: Exception) {
            throw StorageException("Failed to delete object from S3: ${e.message}", e)
        }
    }
}

class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
object S3Client : Storage by MinioStorage()
