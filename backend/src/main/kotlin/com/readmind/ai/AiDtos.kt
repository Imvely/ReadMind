package com.readmind.ai

import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.constraints.NotBlank

// ── 요약 ──
/**
 * 요약 요청 (명세서 §4.4). Phase 0은 scope=DOCUMENT만 지원(전체 문서 map-reduce 요약).
 * SECTION/PAGE_RANGE·scopeRef는 계약 형태만 받아두고 추후 확장한다.
 */
data class SummarizeRequest(
    val scope: String = "DOCUMENT",
    val scopeRef: JsonNode? = null,
    val style: String = "PAPER",
)

data class SummarizeResponse(
    val summaryId: Long,
    val scope: String,
    val style: String,
    /** AI가 생성한 구조화 요약 JSON(스키마는 §5.3, AI 소유). */
    val content: JsonNode,
    /** 캐시 히트 여부 — true면 AI 미호출·쿼터 미차감. */
    val cached: Boolean,
)

// ── Q&A ──
data class QaRequest(
    val sessionId: Long? = null,
    @field:NotBlank(message = "질문은 필수입니다.")
    val question: String,
)

/** 근거 한 건 (명세서 §4.4 sources:[{page,snippet}]). */
data class QaSourceDto(
    val page: Int?,
    val snippet: String,
)

data class QaResponse(
    val sessionId: Long,
    val answer: String,
    /** 근거 — 항상 포함(근거 없는 답변 금지, §3). 근거 0개면 AI가 "찾을 수 없음"으로 강등한다. */
    val sources: List<QaSourceDto>,
)
