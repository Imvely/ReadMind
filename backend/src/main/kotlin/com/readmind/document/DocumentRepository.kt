package com.readmind.document

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 모든 조회는 소유권(user_id) 스코프 + soft-delete 제외를 강제한다 (명세서 §3 사용자 데이터 격리).
 * id 단독 조회 메서드는 의도적으로 두지 않는다 — 항상 userId와 함께 조회.
 */
interface DocumentRepository : JpaRepository<Document, Long> {

    fun findByIdAndUserIdAndDeletedAtIsNull(id: Long, userId: Long): Document?

    fun findByUserIdAndDeletedAtIsNull(userId: Long, pageable: Pageable): Page<Document>
}
