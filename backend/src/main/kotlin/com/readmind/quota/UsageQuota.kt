package com.readmind.quota

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * usage_quotas 테이블 (명세서 §3). user당 1행, 월 단위 사용량 누적.
 * 스키마는 Flyway 소유 — JPA는 생성/검증하지 않는다(ddl-auto:none).
 * 전면 티어 쿼터 시스템은 be-quota-tiers(Phase 2)에서 확장한다.
 */
@Entity
@Table(name = "usage_quotas")
class UsageQuota(
    @Id
    @Column(name = "user_id")
    val userId: Long,

    /** 현재 집계 기간의 시작일(매월 1일). 달이 바뀌면 카운터를 리셋한다. */
    @Column(name = "period_start", nullable = false)
    var periodStart: LocalDate,

    @Column(name = "ai_summary_used", nullable = false)
    var aiSummaryUsed: Int = 0,

    @Column(name = "ai_qa_used", nullable = false)
    var aiQaUsed: Int = 0,

    @Column(name = "ai_translate_used", nullable = false)
    var aiTranslateUsed: Int = 0,

    @Column(name = "storage_bytes", nullable = false)
    var storageBytes: Long = 0,
) {
    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null
}
