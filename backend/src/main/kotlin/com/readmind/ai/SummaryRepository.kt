package com.readmind.ai

import org.springframework.data.jpa.repository.JpaRepository

interface SummaryRepository : JpaRepository<Summary, Long> {

    /**
     * 캐시 조회. Phase 0은 scope=DOCUMENT·scopeRef=null이라 (문서,범위,스타일)당 최대 1행.
     * jsonb scope_ref를 WHERE에 직접 걸지 않고 코드에서 매칭한다(바인딩 단순화).
     */
    fun findByDocumentIdAndScopeAndStyle(documentId: Long, scope: String, style: String): List<Summary>
}
