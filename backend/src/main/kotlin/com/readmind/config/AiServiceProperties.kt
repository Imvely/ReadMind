package com.readmind.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** AI 서비스(M1) 호출 설정 (명세서 §10 AI_SERVICE_*). 내부 전용 X-Service-Token. */
@ConfigurationProperties(prefix = "app.ai")
data class AiServiceProperties(
    val baseUrl: String,
    /** 설정 시 AI 호출에 X-Service-Token 헤더로 전달. 빈 값이면 헤더 생략. */
    val serviceToken: String = "",
    val parseTimeoutSeconds: Long = 120,
    /** 파싱 호출 총 시도 횟수(최초 1회 포함). AI 서비스 콜드스타트·재배포 창 대응 (§4.2). */
    val parseRetryMaxAttempts: Int = 4,
    /** 재시도 간 대기 시작값. 시도마다 2배로 늘린다(5s→10s→20s). */
    val parseRetryInitialBackoffSeconds: Long = 5,
)
