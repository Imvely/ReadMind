package com.readmind.document

import com.readmind.document.ai.AiParseClient
import com.readmind.document.ai.ParseResult
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import kotlin.test.assertEquals

/** 비동기 파싱 러너 — AI 결과 반영(READY) / 실패 시 FAILED 전이. */
class DocumentParseRunnerTest {

    private val documents = mock<DocumentRepository>()
    private val aiClient = mock<AiParseClient>()
    private val runner = DocumentParseRunner(documents, aiClient)

    private fun doc() = Document(
        userId = 10L, title = "t", format = "PDF", storageKey = "k", fileSize = 1,
    ).also { it.id = 1L; it.parseStatus = ParseStatus.PARSING }

    @Test
    fun `파싱 성공 - READY + page_count, language 반영, parse_error 초기화`() {
        val d = doc().also { it.parseError = "이전 실패 사유" }
        whenever(documents.findById(1L)).doReturn(Optional.of(d))
        whenever(aiClient.parse(1L, "k", "PDF")).doReturn(ParseResult(chunkCount = 12, language = "ko", pageCount = 8))

        runner.run(1L)

        assertEquals(ParseStatus.READY, d.parseStatus)
        assertEquals(8, d.pageCount)
        assertEquals("ko", d.language)
        assertEquals(null, d.parseError)
        verify(documents).save(d)
    }

    @Test
    fun `파싱 실패 - FAILED 전이 + parse_error에 사유 기록(사유 없는 실패 금지)`() {
        val d = doc()
        whenever(documents.findById(1L)).doReturn(Optional.of(d))
        whenever(aiClient.parse(any(), any(), any())).doThrow(RuntimeException("AI down"))

        runner.run(1L)

        assertEquals(ParseStatus.FAILED, d.parseStatus)
        assertEquals("RuntimeException: AI down", d.parseError)
        verify(documents).save(d)
    }

    @Test
    fun `파싱 실패 - 사유가 길면 절단(컬럼,응답 보호)`() {
        val d = doc()
        whenever(documents.findById(1L)).doReturn(Optional.of(d))
        whenever(aiClient.parse(any(), any(), any())).doThrow(RuntimeException("x".repeat(2000)))

        runner.run(1L)

        assertEquals(ParseStatus.FAILED, d.parseStatus)
        assertEquals(500, d.parseError!!.length)
    }

    @Test
    fun `문서 없으면 조용히 종료(저장 호출 없음)`() {
        whenever(documents.findById(1L)).doReturn(Optional.empty())

        runner.run(1L)

        verify(documents, org.mockito.kotlin.never()).save(any())
    }
}
