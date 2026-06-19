package com.readmind.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 무료 티어 한도 (명세서 §10 FREE_*). 상세 쿼터 게이트는 be-quota-tiers에서 확장. */
@ConfigurationProperties(prefix = "app.quota")
data class QuotaProperties(
    val freeSummaryPerMonth: Int = 10,
    val freeQaPerMonth: Int = 20,
    val freeTranslatePerMonth: Int = 10,
    val freeStorageMb: Int = 200,
)
