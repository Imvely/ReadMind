package com.readmind.annotation

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
 * highlights 테이블 (명세서 §3/§4.3). 스키마는 Flyway 소유 — JPA는 생성/검증하지 않는다.
 * location 은 포맷 무관 JSONB(§3, PDF=page+rects / EPUB=cfi) — raw JSON 문자열로 통과·보관한다.
 * deleted_at 톰스톤 + version 은 동기화(LWW)용(§4.5) — 삭제는 소프트 삭제.
 */
@Entity
@Table(name = "highlights")
class Highlight(
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: Long,

    @Column(name = "document_id", nullable = false, updatable = false)
    var documentId: Long,

    @Column(name = "page_no")
    var pageNo: Int? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var location: String,

    @Column(name = "selected_text", nullable = false)
    var selectedText: String,

    @Column(nullable = false)
    var color: String = "yellow",

    @Column
    var note: String? = null,

    @Column(name = "ai_suggested", nullable = false, updatable = false)
    var aiSuggested: Boolean = false,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    var tags: Array<String>? = null,

    @Column(name = "client_updated_at")
    var clientUpdatedAt: Instant? = null,

    @Column(nullable = false)
    var version: Int = 1,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null
}
