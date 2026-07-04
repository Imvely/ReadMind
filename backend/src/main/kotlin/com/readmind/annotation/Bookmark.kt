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

/** bookmarks 테이블 (명세서 §3/§4.3). location 은 포맷 무관 JSONB. deleted_at 톰스톤 + version. */
@Entity
@Table(name = "bookmarks")
class Bookmark(
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: Long,

    @Column(name = "document_id", nullable = false, updatable = false)
    var documentId: Long,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var location: String,

    @Column
    var label: String? = null,

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

    /** V3에서 추가 — sync 커서용. DB(default+트리거) 소유라 read-only 매핑. */
    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null
}
