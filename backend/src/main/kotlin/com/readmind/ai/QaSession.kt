package com.readmind.ai

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** qa_sessions 테이블 (명세서 §3). 대화 단위. 소유권은 user_id로 검증. */
@Entity
@Table(name = "qa_sessions")
class QaSession(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "document_id", nullable = false)
    val documentId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null
}
