package com.readmind.annotation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant

/** reading_progress 복합키 (user_id, document_id). */
data class ReadingProgressId(
    var userId: Long = 0,
    var documentId: Long = 0,
) : Serializable

/**
 * reading_progress 테이블 (명세서 §3/§4.3). 문서당 사용자 하나의 진행률(upsert).
 * location 은 포맷 무관 JSONB(마지막 읽은 위치). 톰스톤 없음 — 진행률은 덮어쓴다.
 */
@Entity
@Table(name = "reading_progress")
@IdClass(ReadingProgressId::class)
class ReadingProgress(
    @Id
    @Column(name = "user_id")
    var userId: Long,

    @Id
    @Column(name = "document_id")
    var documentId: Long,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var location: String,

    @Column(nullable = false)
    var percent: BigDecimal,

    @Column(name = "client_updated_at")
    var clientUpdatedAt: Instant? = null,
) {
    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null
}
