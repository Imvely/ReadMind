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
 * qa_messages 테이블 (명세서 §3). role=USER|ASSISTANT.
 * ASSISTANT 메시지에는 근거 sources(jsonb)를 함께 저장한다 — 근거 없는 답변 금지(§3).
 */
@Entity
@Table(name = "qa_messages")
class QaMessage(
    @Column(name = "session_id", nullable = false)
    val sessionId: Long,

    @Column(nullable = false)
    val role: String,

    @Column(nullable = false)
    val content: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val sources: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null
}
