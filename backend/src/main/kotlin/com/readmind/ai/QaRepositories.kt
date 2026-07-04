package com.readmind.ai

import org.springframework.data.jpa.repository.JpaRepository

interface QaSessionRepository : JpaRepository<QaSession, Long> {

    /** 세션 소유권 검증 — 세션이 해당 사용자·문서에 속할 때만 반환(§3). */
    fun findByIdAndUserIdAndDocumentId(id: Long, userId: Long, documentId: Long): QaSession?

    /** 문서의 최근 세션 — 이력 복원(§4.4 qa/history). Phase 0 웹은 문서당 단일 대화 UX. */
    fun findTopByUserIdAndDocumentIdOrderByIdDesc(userId: Long, documentId: Long): QaSession?
}

interface QaMessageRepository : JpaRepository<QaMessage, Long> {

    /** 대화 이력(시간순) — AI 호출 시 history로 전달. */
    fun findBySessionIdOrderByIdAsc(sessionId: Long): List<QaMessage>
}
