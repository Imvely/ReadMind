package com.readmind.ai

import org.springframework.data.jpa.repository.JpaRepository

interface QaSessionRepository : JpaRepository<QaSession, Long> {

    /** 세션 소유권 검증 — 세션이 해당 사용자·문서에 속할 때만 반환(§3). */
    fun findByIdAndUserIdAndDocumentId(id: Long, userId: Long, documentId: Long): QaSession?
}

interface QaMessageRepository : JpaRepository<QaMessage, Long> {

    /** 대화 이력(시간순) — AI 호출 시 history로 전달. */
    fun findBySessionIdOrderByIdAsc(sessionId: Long): List<QaMessage>
}
