package com.example.s3Client

import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.http.Method
import java.io.ByteArrayInputStream
import java.time.Duration

class MinioStorage(
    private val endpoint: String = System.getenv("S3_HOST") ?: "http://localhost:8080",
    private val accessKey: String = System.getenv("S3_ACCESS_KEY") ?: "minio-access-key",
    private val secretKey: String = System.getenv("S3_SECRET_KEY") ?: "minio-secret-key",
    private val bucket: String = System.getenv("S3_ID") ?: "minio-bucket",
    private val region: String = System.getenv("S3_REGION") ?: "ru-1",
){
    /*private*/ val minio: MinioClient = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .region(region)
        .build()

    fun putObjectBytes(key: String, bytes: ByteArray, contentType: String = "application/octet-stream") {
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

    fun presignPutUrl(
        key: String,
        expires: Duration = Duration.ofMinutes(2)
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
}