package com.readmind.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.readmind.common.ApiException
import com.readmind.common.ErrorCode
import com.readmind.document.DocumentService
import com.readmind.document.ParseStatus
import com.readmind.quota.QuotaGate
import com.readmind.quota.QuotaKind
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * AI 위임 서비스 (명세서 §4.4). AI 호출을 ai/ 모듈에 격리하고 다음 순서를 강제한다(CLAUDE.md §5):
 *   소유권 검증 → 쿼터 게이트(검사) → 캐시 조회 → AI 호출 → 캐시 저장 → 쿼터 차감.
 * 캐시 히트 시 AI 미호출·쿼터 미차감(변동비 0). 근거(sources)는 항상 통과·저장(§3).
 */
@Service
class AiService(
    private val documents: DocumentService,
    private val quota: QuotaGate,
    private val ai: AiContentClient,
    private val summaries: SummaryRepository,
    private val qaSessions: QaSessionRepository,
    private val qaMessages: QaMessageRepository,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun summarize(userId: Long, documentId: Long, req: SummarizeRequest): SummarizeResponse {
        requireReadyDocument(userId, documentId)
        val scope = normalizeScope(req.scope)
        val style = normalizeStyle(req.style)

        // 1) 쿼터 게이트(검사만 — 차감은 실제 AI 호출 후).
        quota.ensureWithin(userId, QuotaKind.SUMMARY)

        // 2) 캐시 조회 → 히트면 AI 미호출·쿼터 미차감.
        findCachedSummary(documentId, scope, style)?.let {
            return it.toResponse(cached = true)
        }

        // 3) AI 호출.
        val content = ai.summarize(documentId, style)

        // 4) 캐시 저장.
        val saved = summaries.save(
            Summary(
                documentId = documentId,
                scope = scope,
                scopeRef = null, // Phase 0 DOCUMENT 범위는 scopeRef 없음.
                style = style,
                content = objectMapper.writeValueAsString(content),
            ),
        )

        // 5) 쿼터 차감(변동비 발생).
        quota.record(userId, QuotaKind.SUMMARY)

        return saved.toResponse(cached = false)
    }

    @Transactional
    fun qa(userId: Long, documentId: Long, req: QaRequest): QaResponse {
        requireReadyDocument(userId, documentId)

        // 쿼터 게이트(검사). QA는 대화형이라 답변 캐시는 두지 않는다 — 단, AI 서비스가
        // 이미 캐싱된 document_chunks를 재사용하므로 "같은 문서 재처리 금지"(§3)는 충족된다.
        quota.ensureWithin(userId, QuotaKind.QA)

        val session = resolveSession(userId, documentId, req.sessionId)
        val sessionId = session.id!!
        val history = qaMessages.findBySessionIdOrderByIdAsc(sessionId)
            .map { AiQaTurn(role = it.role.lowercase(), content = it.content) }

        val result = ai.qa(documentId, req.question, history)

        // 대화 로그 저장(질문 + 근거 포함 답변).
        qaMessages.save(QaMessage(sessionId = sessionId, role = ROLE_USER, content = req.question))
        qaMessages.save(
            QaMessage(
                sessionId = sessionId,
                role = ROLE_ASSISTANT,
                content = result.answer,
                sources = objectMapper.writeValueAsString(result.sources),
            ),
        )

        quota.record(userId, QuotaKind.QA)

        return QaResponse(
            sessionId = sessionId,
            answer = result.answer,
            sources = result.sources.map { QaSourceDto(page = it.pageNo, snippet = it.snippet) },
        )
    }

    // ── 내부 ──

    /** 소유권 + 파싱 완료 검증. 미소유=NOT_FOUND(DocumentService), 미완료=VALIDATION. */
    private fun requireReadyDocument(userId: Long, documentId: Long) {
        val doc = documents.get(userId, documentId) // 소유권 검증(미소유 시 NOT_FOUND).
        if (doc.parseStatus != ParseStatus.READY) {
            throw ApiException(ErrorCode.VALIDATION, "문서 파싱이 끝나지 않았습니다(현재: ${doc.parseStatus}).")
        }
    }

    private fun resolveSession(userId: Long, documentId: Long, sessionId: Long?): QaSession {
        if (sessionId == null) {
            return qaSessions.save(QaSession(userId = userId, documentId = documentId))
        }
        return qaSessions.findByIdAndUserIdAndDocumentId(sessionId, userId, documentId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "대화 세션을 찾을 수 없습니다.")
    }

    /** Phase 0은 scopeRef=null(DOCUMENT)이라 동일 (문서,범위,스타일)에 최대 1행. */
    private fun findCachedSummary(documentId: Long, scope: String, style: String): Summary? =
        summaries.findByDocumentIdAndScopeAndStyle(documentId, scope, style)
            .firstOrNull { it.scopeRef == null }

    private fun Summary.toResponse(cached: Boolean) = SummarizeResponse(
        summaryId = id!!,
        scope = scope,
        style = style,
        content = objectMapper.readTree(content),
        cached = cached,
    )

    private fun normalizeScope(raw: String): String {
        val scope = raw.trim().uppercase()
        if (scope != SCOPE_DOCUMENT) {
            // SECTION/PAGE_RANGE는 추후 확장(AI는 현재 전체 문서 요약만).
            throw ApiException(ErrorCode.VALIDATION, "Phase 0 요약은 DOCUMENT 범위만 지원합니다: $raw")
        }
        return scope
    }

    private fun normalizeStyle(raw: String): String {
        val style = raw.trim().uppercase()
        if (style !in SUPPORTED_STYLES) {
            throw ApiException(ErrorCode.VALIDATION, "지원하지 않는 요약 스타일입니다: $raw")
        }
        return style
    }

    private companion object {
        const val SCOPE_DOCUMENT = "DOCUMENT"
        const val ROLE_USER = "USER"
        const val ROLE_ASSISTANT = "ASSISTANT"
        val SUPPORTED_STYLES = setOf("PAPER", "PLAIN")
    }
}
