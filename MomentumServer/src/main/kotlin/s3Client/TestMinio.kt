package com.example.s3Client

import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.StatObjectArgs
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

fun testMinioUpload(
    endpoint: String,
    accessKey: String,
    secretKey: String,
    bucket: String,
    file: Path
) {
    val minioClient = MinioStorage(endpoint, accessKey, secretKey, bucket)
    val minio = minioClient.minio

    val key = "debug/test-${UUID.randomUUID()}-${file.fileName}"

    Files.newInputStream(file).use { input ->
        minio.putObject(
            PutObjectArgs.builder()
                .bucket(bucket)
                .`object`(key)
                .stream(input, Files.size(file), -1)
                .contentType("application/octet-stream")
                .build()
        )
    }

    val stat = minio.statObject(
        StatObjectArgs.builder()
            .bucket(bucket)
            .`object`(key)
            .build()
    )

    println("OK uploaded: s3://$bucket/$key size=${stat.size()} etag=${stat.etag()}")
}