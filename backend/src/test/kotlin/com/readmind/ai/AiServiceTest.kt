package com.readmind.ai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.readmind.common.ApiException
import com.readmind.common.ErrorCode
import com.readmind.document.DocumentDto
import com.readmind.document.DocumentService
import com.readmind.document.ParseStatus
import com.readmind.quota.QuotaGate
import com.readmind.quota.QuotaKind
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AI 위임 서비스 단위 (명세서 §4.4, §3).
 * 강제 순서(소유권→쿼터→캐시→AI→저장→차감)와 캐시 히트 시 미차감, 근거 통과를 검증.
 */
class AiServiceTest {

    private val documents = mock<DocumentService>()
    private val quota = mock<QuotaGate>()
    private val ai = mock<AiContentClient>()
    private val summaries = mock<SummaryRepository>()
    private val qaSessions = mock<QaSessionRepository>()
    private val qaMessages = mock<QaMessageRepository>()
    // 운영(Spring Boot) 매퍼와 동일하게 Kotlin 모듈 포함 — data class(AiSource) 역직렬화에 필요.
    private val objectMapper = jacksonObjectMapper()

    private val service = AiService(documents, quota, ai, summaries, qaSessions, qaMessages, objectMapper)

    private val userId = 10L
    private val docId = 7L

    private fun readyDoc(status: ParseStatus = ParseStatus.READY) =
        DocumentDto(docId, "논문", "PDF", 123, 8, "ko", status, null)

    // ── 요약 ──

    @Test
    fun `summarize 해피 - AI 호출, 캐시 저장, 쿼터 차감, cached=false`() {
        whenever(documents.get(userId, docId)).doReturn(readyDoc())
        whenever(summaries.findByDocumentIdAndScopeAndStyle(docId, "DOCUMENT", "PAPER"))
            .doReturn(emptyList())
        whenever(ai.summarize(docId, "PAPER")).doReturn(objectMapper.readTree("""{"tldr":"요지"}"""))
        whenever(summaries.save(any())).doReturn(
            Summary(docId, "DOCUMENT", null, "PAPER", """{"tldr":"요지"}""").apply { id = 1 },
        )

        val res = service.summarize(userId, docId, SummarizeRequest())

        assertFalse(res.cached)
        assertEquals("요지", res.content.get("tldr").asText())
        verify(ai).summarize(docId, "PAPER")
        verify(quota).record(userId, QuotaKind.SUMMARY)
    }

    @Test
    fun `summarize 캐시 히트 - AI 미호출, 쿼터 미차감, cached=true`() {
        whenever(documents.get(userId, docId)).doReturn(readyDoc())
        whenever(summaries.findByDocumentIdAndScopeAndStyle(docId, "DOCUMENT", "PAPER")).doReturn(
            listOf(Summary(docId, "DOCUMENT", null, "PAPER", """{"tldr":"캐시"}""").apply { id = 5 }),
        )

        val res = service.summarize(userId, docId, SummarizeRequest())

        assertTrue(res.cached)
        assertEquals("캐시", res.content.get("tldr").asText())
        verify(ai, never()).summarize(any(), any())
        verify(quota, never()).record(any(), any())
    }

    @Test
    fun `summarize 소유권 실패 - DocumentService NOT_FOUND 전파`() {
        whenever(documents.get(userId, docId))
            .doThrow(ApiException(ErrorCode.NOT_FOUND, "문서를 찾을 수 없습니다."))

        val ex = assertThrows<ApiException> { service.summarize(userId, docId, SummarizeRequest()) }
        assertEquals(ErrorCode.NOT_FOUND, ex.code)
        verify(ai, never()).summarize(any(), any())
    }

    @Test
    fun `summarize 파싱 미완료 - VALIDATION, AI 미호출`() {
        whenever(documents.get(userId, docId)).doReturn(readyDoc(ParseStatus.PARSING))

        val ex = assertThrows<ApiException> { service.summarize(userId, docId, SummarizeRequest()) }
        assertEquals(ErrorCode.VALIDATION, ex.code)
        verify(ai, never()).summarize(any(), any())
    }

    @Test
    fun `summarize 쿼터 초과 - QUOTA_EXCEEDED, AI 미호출`() {
        whenever(documents.get(userId, docId)).doReturn(readyDoc())
        doThrow(ApiException(ErrorCode.QUOTA_EXCEEDED, "한도 초과"))
            .whenever(quota).ensureWithin(userId, QuotaKind.SUMMARY)

        val ex = assertThrows<ApiException> { service.summarize(userId, docId, SummarizeRequest()) }
        assertEquals(ErrorCode.QUOTA_EXCEEDED, ex.code)
        verify(ai, never()).summarize(any(), any())
    }

    @Test
    fun `summarize 잘못된 스타일 - VALIDATION`() {
        whenever(documents.get(userId, docId)).doReturn(readyDoc())

        val ex = assertThrows<ApiException> {
            service.summarize(userId, docId, SummarizeRequest(style = "WEIRD"))
        }
        assertEquals(ErrorCode.VALIDATION, ex.code)
        verify(ai, never()).summarize(any(), any())
    }

    @Test
    fun `summarize SECTION 범위 거부 - VALIDATION`() {
        whenever(documents.get(userId, docId)).doReturn(readyDoc())

        val ex = assertThrows<ApiException> {
            service.summarize(userId, docId, SummarizeRequest(scope = "SECTION"))
        }
        assertEquals(ErrorCode.VALIDATION, ex.code)
    }

    // ── Q&A ──

    @Test
    fun `qa 해피 - 새 세션, 근거 page와 snippet 반환, USER+ASSISTANT 저장, 차감`() {
        whenever(documents.get(userId, docId)).doReturn(readyDoc())
        whenever(qaSessions.save(any())).doReturn(QaSession(userId, docId).apply { id = 20 })
        whenever(qaMessages.findBySessionIdOrderByIdAsc(20)).doReturn(emptyList())
        whenever(ai.qa(eq(docId), eq("질문?"), any())).doReturn(
            AiQaResult(answer = "답", sources = listOf(AiSource(chunkIndex = 0, pageNo = 3, snippet = "근거문장"))),
        )
        whenever(qaMessages.save(any())).doReturn(mock())

        val res = service.qa(userId, docId, QaRequest(question = "질문?"))

        assertEquals(20, res.sessionId)
        assertEquals("답", res.answer)
        assertEquals(1, res.sources.size)
        assertEquals(3, res.sources[0].page)
        assertEquals("근거문장", res.sources[0].snippet)

        // USER 질문 + ASSISTANT 답변(근거 jsonb) 2건 저장.
        val captor = argumentCaptor<QaMessage>()
        verify(qaMessages, org.mockito.kotlin.times(2)).save(captor.capture())
        assertEquals("USER", captor.firstValue.role)
        assertEquals("ASSISTANT", captor.secondValue.role)
        assertTrue(captor.secondValue.sources!!.contains("근거문장"))
        verify(quota).record(userId, QuotaKind.QA)
    }

    @Test
    fun `qa 세션 소유권 실패 - 남의 세션이면 NOT_FOUND`() {
        whenever(documents.get(userId, docId)).doReturn(readyDoc())
        whenever(qaSessions.findByIdAndUserIdAndDocumentId(99L, userId, docId)).doReturn(null)

        val ex = assertThrows<ApiException> {
            service.qa(userId, docId, QaRequest(sessionId = 99L, question = "q"))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.code)
        verify(ai, never()).qa(any(), any(), any())
    }

    @Test
    fun `qa 쿼터 초과 - QUOTA_EXCEEDED, AI 미호출`() {
        whenever(documents.get(userId, docId)).doReturn(readyDoc())
        doThrow(ApiException(ErrorCode.QUOTA_EXCEEDED, "한도 초과"))
            .whenever(quota).ensureWithin(userId, QuotaKind.QA)

        val ex = assertThrows<ApiException> {
            service.qa(userId, docId, QaRequest(question = "q"))
        }
        assertEquals(ErrorCode.QUOTA_EXCEEDED, ex.code)
        verify(ai, never()).qa(any(), any(), any())
    }

    @Test
    fun `qa 기존 세션 - 이력을 history로 전달`() {
        whenever(documents.get(userId, docId)).doReturn(readyDoc())
        whenever(qaSessions.findByIdAndUserIdAndDocumentId(20L, userId, docId))
            .doReturn(QaSession(userId, docId).apply { id = 20 })
        whenever(qaMessages.findBySessionIdOrderByIdAsc(20L)).doReturn(
            listOf(QaMessage(20L, "USER", "이전질문")),
        )
        whenever(ai.qa(eq(docId), eq("후속?"), any())).doReturn(AiQaResult(answer = "답2"))
        whenever(qaMessages.save(any())).doReturn(mock())

        service.qa(userId, docId, QaRequest(sessionId = 20L, question = "후속?"))

        val historyCaptor = argumentCaptor<List<AiQaTurn>>()
        verify(ai).qa(eq(docId), eq("후속?"), historyCaptor.capture())
        assertEquals(1, historyCaptor.firstValue.size)
        assertEquals("user", historyCaptor.firstValue[0].role) // 소문자로 정규화
        assertEquals("이전질문", historyCaptor.firstValue[0].content)
    }

    // ── Q&A 이력 (§4.4 qa/history) ──

    @Test
    fun `qaHistory 해피 - 최근 세션의 메시지를 시간순 반환, ASSISTANT sources 변환(pageNo→page)`() {
        whenever(documents.get(userId, docId)).doReturn(readyDoc())
        val session = QaSession(userId = userId, documentId = docId).apply { id = 3 }
        whenever(qaSessions.findTopByUserIdAndDocumentIdOrderByIdDesc(userId, docId)).doReturn(session)
        whenever(qaMessages.findBySessionIdOrderByIdAsc(3)).doReturn(
            listOf(
                QaMessage(sessionId = 3, role = "USER", content = "질문1"),
                QaMessage(
                    sessionId = 3, role = "ASSISTANT", content = "답변1",
                    sources = """[{"chunkIndex":0,"pageNo":2,"snippet":"근거 발췌"}]""",
                ),
            ),
        )

        val res = service.qaHistory(userId, docId)

        assertEquals(3L, res.sessionId)
        assertEquals(2, res.messages.size)
        assertEquals("USER", res.messages[0].role)
        assertEquals(null, res.messages[0].sources)
        val srcs = res.messages[1].sources!!
        assertEquals(2, srcs[0].page) // 저장형 pageNo → 계약형 page
        assertEquals("근거 발췌", srcs[0].snippet)
        verify(quota, never()).record(any(), any()) // 조회 전용 — 쿼터 미차감
    }

    @Test
    fun `qaHistory 대화 없음 - sessionId null, 빈 배열`() {
        whenever(documents.get(userId, docId)).doReturn(readyDoc())
        whenever(qaSessions.findTopByUserIdAndDocumentIdOrderByIdDesc(userId, docId)).doReturn(null)

        val res = service.qaHistory(userId, docId)

        assertEquals(null, res.sessionId)
        assertTrue(res.messages.isEmpty())
    }

    @Test
    fun `qaHistory 미소유 문서 - NOT_FOUND`() {
        whenever(documents.get(userId, docId))
            .doThrow(ApiException(ErrorCode.NOT_FOUND, "문서를 찾을 수 없습니다."))

        val ex = assertThrows<ApiException> { service.qaHistory(userId, docId) }
        assertEquals(ErrorCode.NOT_FOUND, ex.code)
    }

    // ── 번역 ──

    @Test
    fun `translate 해피 - 소유권+쿼터 후 AI 호출, 원문대조 반환, 차감`() {
        whenever(documents.get(userId, docId)).doReturn(readyDoc())
        whenever(ai.translate("이것은 테스트다.", "en"))
            .doReturn(AiTranslateResult("This is a test.", "이것은 테스트다.", "en"))

        val res = service.translate(
            userId, docId, TranslateRequest(text = "이것은 테스트다.", targetLang = "en"),
        )

        assertEquals("This is a test.", res.translated)
        assertEquals("이것은 테스트다.", res.sourceExcerpt) // 원문대조
        assertEquals("en", res.targetLang)
        verify(quota).record(userId, QuotaKind.TRANSLATE)
    }

    @Test
    fun `translate 쿼터 초과 - QUOTA_EXCEEDED, AI 미호출·미차감`() {
        whenever(documents.get(userId, docId)).doReturn(readyDoc())
        doThrow(ApiException(ErrorCode.QUOTA_EXCEEDED, "한도 초과"))
            .whenever(quota).ensureWithin(userId, QuotaKind.TRANSLATE)

        val ex = assertThrows<ApiException> {
            service.translate(userId, docId, TranslateRequest(text = "hi"))
        }
        assertEquals(ErrorCode.QUOTA_EXCEEDED, ex.code)
        verify(ai, never()).translate(any(), any())
        verify(quota, never()).record(any(), eq(QuotaKind.TRANSLATE))
    }

    @Test
    fun `translate 미소유 문서 - NOT_FOUND, AI 미호출`() {
        whenever(documents.get(userId, docId))
            .doThrow(ApiException(ErrorCode.NOT_FOUND, "문서를 찾을 수 없습니다."))

        val ex = assertThrows<ApiException> {
            service.translate(userId, docId, TranslateRequest(text = "hi"))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.code)
        verify(ai, never()).translate(any(), any())
    }
}
