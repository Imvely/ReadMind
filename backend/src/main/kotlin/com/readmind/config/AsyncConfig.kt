package com.readmind.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * 업로드 완료 후 AI 파싱을 비동기로 트리거하기 위한 설정 (명세서 §4.2 "비동기 파싱").
 * Phase 0은 @Async 스레드풀로 충분. 추후 Redis 큐로 교체 가능하도록 트리거 지점을 포트로 격리.
 */
@Configuration
@EnableAsync
class AsyncConfig {

    @Bean(name = ["parseExecutor"])
    fun parseExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 4
        queueCapacity = 100
        setThreadNamePrefix("parse-")
        initialize()
    }
}
