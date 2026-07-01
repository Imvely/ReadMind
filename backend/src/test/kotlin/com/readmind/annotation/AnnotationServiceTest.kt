package com.readmind.annotation

import com.fasterxml.jackson.databind.ObjectMapper
import com.readmind.common.ApiException
import com.readmind.common.ErrorCode
import com.readmind.document.Document
import com.readmind.document.DocumentRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** AnnotationService 단위 테스트 — 소유권 검증 + location JSONB 통과 + 소프트삭제/version. DB 없이 mock. */
class AnnotationServiceTest {

    private val documents = mock<DocumentRepository>()
    private val highlights = mock<HighlightRepository>()
    private val notes = mock<NoteRepository>()
    private val bookmarks = mock<BookmarkRepository>()
    private val progresses = mock<ReadingProgressRepository>()
    private val mapper = ObjectMapper()
    private val service = AnnotationService(documents, highlights, notes, bookmarks, progresses, mapper)

    private val USER = 10L
    private val DOC = 1L

    private fun ownedDoc() = Document(
        userId = USER, title = "t", format = "PDF", storageKey = "k", fileSize = 1,
    ).also { it.id = DOC }

    private fun loc(json: String) = mapper.readTree(json)

    private fun ownDocument() =
        whenever(documents.findByIdAndUserIdAndDeletedAtIsNull(DOC, USER)).doReturn(ownedDoc())

    // ── 해피패스 ──
    @Test
    fun `createHighlight - 소유 문서면 저장하고 location·tags 통과`() {
        ownDocument()
        // save 는 id 를 부여한 엔티티를 반환한다고 가정.
        whenever(highlights.save(any())).doReturn(
            Highlight(
                userId = USER, documentId = DOC, pageNo = 3,
                location = """{"type":"pdf","page":3}""",
                selectedText = "abc", color = "green", tags = arrayOf("t1", "t2"),
            ).also { it.id = 7L },
        )

        val req = CreateHighlightRequest(
            pageNo = 3, location = loc("""{"type":"pdf","page":3}"""),
            selectedText = "abc", color = "green", tags = listOf("t1", "t2"),
        )
        val dto = service.createHighlight(USER, DOC, req)

        assertEquals(7L, dto.id)
        assertEquals("green", dto.color)
        assertEquals(listOf("t1", "t2"), dto.tags)
        assertEquals(3, dto.location.get("page").asInt()) // JSONB 원형 통과
    }

    @Test
    fun `listHighlights - 소유 문서의 하이라이트만 매핑`() {
        ownDocument()
        whenever(
            highlights.findByDocumentIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(DOC, USER),
        ).doReturn(
            listOf(
                Highlight(
                    userId = USER, documentId = DOC, location = "{}", selectedText = "s",
                ).also { it.id = 1L },
            ),
        )
        val list = service.listHighlights(USER, DOC)
        assertEquals(1, list.size)
        assertEquals(1L, list[0].id)
    }

    // ── 소유권 실패 ──
    @Test
    fun `createHighlight - 미소유 문서면 NOT_FOUND, 저장 안 함`() {
        whenever(documents.findByIdAndUserIdAndDeletedAtIsNull(DOC, USER)).doReturn(null)
        val ex = assertThrows<ApiException> {
            service.createHighlight(
                USER, DOC,
                CreateHighlightRequest(location = loc("{}"), selectedText = "s"),
            )
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.code)
        verify(highlights, never()).save(any())
    }

    @Test
    fun `updateHighlight - 남의(또는 없는) 하이라이트면 NOT_FOUND`() {
        whenever(highlights.findByIdAndUserIdAndDeletedAtIsNull(99L, USER)).doReturn(null)
        val ex = assertThrows<ApiException> {
            service.updateHighlight(USER, 99L, UpdateHighlightRequest(color = "red"))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.code)
        verify(highlights, never()).save(any())
    }

    @Test
    fun `deleteHighlight - 소프트 삭제 + version 증가`() {
        val h = Highlight(
            userId = USER, documentId = DOC, location = "{}", selectedText = "s",
        ).also { it.id = 5L; it.version = 1 }
        whenever(highlights.findByIdAndUserIdAndDeletedAtIsNull(5L, USER)).doReturn(h)
        whenever(highlights.save(any())).doReturn(h)

        service.deleteHighlight(USER, 5L)

        assertNotNull(h.deletedAt) // 실제 삭제가 아니라 톰스톤
        assertEquals(2, h.version)
    }

    // ── 진행률 upsert ──
    @Test
    fun `putProgress - 기존 없으면 새로 저장`() {
        ownDocument()
        whenever(progresses.findByUserIdAndDocumentId(USER, DOC)).doReturn(null)
        whenever(progresses.save(any())).doAnswer { it.arguments[0] as ReadingProgress }

        val dto = service.putProgress(
            USER, DOC,
            UpdateProgressRequest(location = loc("""{"cfi":"/6/4"}"""), percent = BigDecimal("42.5")),
        )
        assertEquals(BigDecimal("42.5"), dto.percent)
        assertEquals("/6/4", dto.location.get("cfi").asText())
    }

    @Test
    fun `getProgress - 없으면 null`() {
        ownDocument()
        whenever(progresses.findByUserIdAndDocumentId(USER, DOC)).doReturn(null)
        assertNull(service.getProgress(USER, DOC))
    }

    @Test
    fun `createNote - 미소유 문서면 NOT_FOUND`() {
        whenever(documents.findByIdAndUserIdAndDeletedAtIsNull(DOC, USER)).doReturn(null)
        val ex = assertThrows<ApiException> {
            service.createNote(USER, DOC, CreateNoteRequest(body = "메모"))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.code)
        verify(notes, never()).save(any())
    }
}
