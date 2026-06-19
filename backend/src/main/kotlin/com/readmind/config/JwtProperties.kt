package com.readmind.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** JWT 설정 (명세서 §10: JWT_SECRET/JWT_ACCESS_TTL/JWT_REFRESH_TTL). */
@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    val secret: String,
    val accessTtlSeconds: Long = 900,
    val refreshTtlSeconds: Long = 1_209_600,
)
