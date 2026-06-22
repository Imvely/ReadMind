package com.readmind.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** S3 호환 스토리지 설정 (명세서 §10 S3_*). MinIO 로컬/운영 S3 공용. */
@ConfigurationProperties(prefix = "app.s3")
data class S3Properties(
    val endpoint: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val region: String = "us-east-1",
    val pathStyle: Boolean = true,
    val presignTtlSeconds: Long = 900,
)
