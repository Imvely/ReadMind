package com.readmind.ai

import com.fasterxml.jackson.databind.ObjectMapper
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
    private val objectMapper = ObjectMapper()

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
}
