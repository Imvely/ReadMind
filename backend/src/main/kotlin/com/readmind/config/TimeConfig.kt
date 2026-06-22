package com.readmind.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** 시간 의존성을 주입 가능하게 한다(쿼터 기간 계산 등 테스트 결정성 확보). DB는 UTC(§application.yml). */
@Configuration
class TimeConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
