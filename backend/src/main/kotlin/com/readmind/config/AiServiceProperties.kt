package com.readmind.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** AI 서비스(M1) 호출 설정 (명세서 §10 AI_SERVICE_*). 내부 전용 X-Service-Token. */
@ConfigurationProperties(prefix = "app.ai")
data class AiServiceProperties(
    val baseUrl: String,
    /** 설정 시 AI 호출에 X-Service-Token 헤더로 전달. 빈 값이면 헤더 생략. */
    val serviceToken: String = "",
    val parseTimeoutSeconds: Long = 120,
)
