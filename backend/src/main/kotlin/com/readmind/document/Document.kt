package com.readmind.document

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 파싱 상태 머신: 생성(PENDING) → 업로드완료 후 파싱시작(PARSING) → READY | FAILED. */
enum class ParseStatus { PENDING, PARSING, READY, FAILED }

/** documents 테이블 (명세서 §3). 스키마는 Flyway 소유 — JPA는 생성/검증하지 않는다. */
@Entity
@Table(name = "documents")
class Document(
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: Long,

    @Column(nullable = false)
    var title: String,

    /** 대문자 정규화 포맷(PDF/EPUB...). AI 호출 시 소문자로 전달. */
    @Column(nullable = false)
    var format: String,

    @Column(name = "storage_key", nullable = false)
    var storageKey: String,

    @Column(name = "file_size", nullable = false)
    var fileSize: Long,

    @Column(name = "page_count")
    var pageCount: Int? = null,

    @Column(name = "language")
    var language: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false)
    var parseStatus: ParseStatus = ParseStatus.PENDING,

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
