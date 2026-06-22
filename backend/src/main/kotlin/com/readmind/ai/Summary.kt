package com.readmind.ai

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * summaries 테이블 — 캐시된 AI 요약 (명세서 §3, §4.4 "구조화 요약(캐시)").
 * content/scope_ref는 jsonb. 요약 JSON 스키마는 AI 서비스(M1) 소유이므로
 * 백엔드는 raw JSON 문자열로 보관·통과한다(재모델링하지 않음, CLAUDE.md §3 계약 보존).
 * Phase 0은 scope=DOCUMENT만(전체 문서 요약). SECTION/PAGE_RANGE는 추후 확장.
 */
@Entity
@Table(name = "summaries")
class Summary(
    @Column(name = "document_id", nullable = false)
    val documentId: Long,

    @Column(nullable = false)
    val scope: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope_ref", columnDefinition = "jsonb")
    val scopeRef: String? = null,

    @Column(nullable = false)
    val style: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val content: String,

    @Column
    val model: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null
}
