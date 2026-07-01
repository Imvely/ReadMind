package com.readmind.annotation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** notes 테이블 (명세서 §3/§4.3). 페이지 단위 메모. deleted_at 톰스톤 + version(동기화). */
@Entity
@Table(name = "notes")
class Note(
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: Long,

    @Column(name = "document_id", nullable = false, updatable = false)
    var documentId: Long,

    @Column(name = "page_no")
    var pageNo: Int? = null,

    @Column(nullable = false)
    var body: String,

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
