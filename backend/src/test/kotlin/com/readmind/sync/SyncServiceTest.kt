package com.readmind.sync

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.readmind.annotation.Bookmark
import com.readmind.annotation.BookmarkRepository
import com.readmind.annotation.Highlight
import com.readmind.annotation.HighlightRepository
import com.readmind.annotation.NoteRepository
import com.readmind.annotation.ReadingProgress
import com.readmind.annotation.ReadingProgressRepository
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
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 동기화 LWW/tombstone/소유권/게이트 단위 (명세서 §4.5, §3). */
class SyncServiceTest {

    private val highlights = mock<HighlightRepository>()
    private val notes = mock<NoteRepository>()
    private val bookmarks = mock<BookmarkRepository>()
    private val progress = mock<ReadingProgressRepository>()
    private val documents = mock<DocumentRepository>()
    private val objectMapper = jacksonObjectMapper()

    private val service = SyncService(
        highlights, notes, bookmarks, progress, documents, objectMapper, requirePro = false,
    )

    private val userId = 10L
    private val docId = 7L
    private val loc = objectMapper.readTree("""{"type":"pdf","page":1,"rects":[]}""")

    private fun ownDoc() {
        whenever(documents.findByIdAndUserIdAndDeletedAtIsNull(docId, userId))
            .doReturn(mock<Document>())
    }

    private fun highlight(version: Int, clientAt: Instant?) = Highlight(
        userId = userId, documentId = docId, pageNo = 1, location = loc.toString(),
        selectedText = "원문", color = "yellow", clientUpdatedAt = clientAt, version = version,
    ).apply { id = 55 }

    // ── changes ──

    @Test
    fun `changes - since 이후 변경(tombstone 포함)과 cursor 반환`() {
        val since = Instant.parse("2026-07-01T00:00:00Z")
        val tombstone = highlight(3, Instant.now()).apply { deletedAt = Instant.now() }
        whenever(highlights.findByUserIdAndUpdatedAtAfter(userId, since)).doReturn(listOf(tombstone))
        whenever(notes.findByUserIdAndUpdatedAtAfter(userId, since)).doReturn(emptyList())
        whenever(bookmarks.findByUserIdAndUpdatedAtAfter(userId, since)).doReturn(emptyList())
        whenever(progress.findByUserIdAndUpdatedAtAfter(userId, since)).doReturn(emptyList())

        val res = service.changes(userId, since)

        assertEquals(1, res.changes.highlights.size)
        assertNotNull(res.changes.highlights[0].deletedAt) // 삭제 전파
        assertNotNull(res.cursor)
        verify(highlights, never()).findByUserId(any())
    }

    // ── push: 신규/갱신/충돌/삭제 ──

    @Test
    fun `push 신규 - insert 후 applied에 clientId와 서버 id 매핑`() {
        ownDoc()
        whenever(highlights.save(any())).doAnswer { (it.arguments[0] as Highlight).apply { id = 100 } }

        val res = service.push(
            userId,
            SyncPushRequest(
                PushChanges(
                    highlights = listOf(
                        PushHighlight(
                            clientId = "local-1", documentId = docId,
                            location = loc, selectedText = "새 하이라이트",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(1, res.applied.size)
        assertEquals("local-1", res.applied[0].clientId)
        assertEquals(100L, res.applied[0].id)
        assertTrue(res.conflicts.isEmpty())
    }

    @Test
    fun `push 갱신 - 클라 version이 서버 이상이면 적용하고 version+1`() {
        ownDoc()
        val server = highlight(version = 2, clientAt = Instant.parse("2026-07-01T00:00:00Z"))
        whenever(highlights.findByIdAndUserId(55, userId)).doReturn(server)

        val res = service.push(
            userId,
            SyncPushRequest(
                PushChanges(
                    highlights = listOf(
                        PushHighlight(
                            id = 55, documentId = docId, color = "green", version = 2,
                            clientUpdatedAt = Instant.parse("2026-07-02T00:00:00Z"),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(3, server.version)
        assertEquals("green", server.color)
        assertEquals(3, res.applied[0].version)
    }

    @Test
    fun `push 충돌 - 서버 version 크고 서버 수정이 최신이면 conflicts에 서버 상태`() {
        ownDoc()
        val server = highlight(version = 5, clientAt = Instant.parse("2026-07-03T00:00:00Z"))
        whenever(highlights.findByIdAndUserId(55, userId)).doReturn(server)

        val res = service.push(
            userId,
            SyncPushRequest(
                PushChanges(
                    highlights = listOf(
                        PushHighlight(
                            id = 55, documentId = docId, color = "red", version = 2,
                            clientUpdatedAt = Instant.parse("2026-07-01T00:00:00Z"), // 서버보다 과거
                        ),
                    ),
                ),
            ),
        )

        assertTrue(res.applied.isEmpty())
        assertEquals(1, res.conflicts.size)
        assertEquals("yellow", (res.conflicts[0].server as SyncHighlightDto).color) // 서버 상태 그대로
        assertEquals("yellow", server.color) // 클라 변경 미적용
    }

    @Test
    fun `push LWW - 서버 version이 커도 클라 수정이 더 최신이면 적용`() {
        ownDoc()
        val server = highlight(version = 5, clientAt = Instant.parse("2026-07-01T00:00:00Z"))
        whenever(highlights.findByIdAndUserId(55, userId)).doReturn(server)

        val res = service.push(
            userId,
            SyncPushRequest(
                PushChanges(
                    highlights = listOf(
                        PushHighlight(
                            id = 55, documentId = docId, color = "red", version = 2,
                            clientUpdatedAt = Instant.parse("2026-07-04T00:00:00Z"), // 서버보다 최신
                        ),
                    ),
                ),
            ),
        )

        assertEquals("red", server.color)
        assertEquals(6, server.version)
        assertTrue(res.conflicts.isEmpty())
    }

    @Test
    fun `push 삭제 - tombstone 세팅(물리 삭제 아님)`() {
        ownDoc()
        val server = Bookmark(
            userId = userId, documentId = docId, location = loc.toString(), version = 1,
        ).apply { id = 9 }
        whenever(bookmarks.findByIdAndUserId(9, userId)).doReturn(server)

        service.push(
            userId,
            SyncPushRequest(
                PushChanges(
                    bookmarks = listOf(
                        PushBookmark(id = 9, documentId = docId, version = 1, deleted = true),
                    ),
                ),
            ),
        )

        assertNotNull(server.deletedAt)
        verify(bookmarks, never()).delete(any())
    }

    // ── progress LWW ──

    @Test
    fun `progress - 서버가 더 최신이면 조용히 무시`() {
        ownDoc()
        val server = ReadingProgress(
            userId = userId, documentId = docId, location = loc.toString(),
            percent = BigDecimal("70.00"),
            clientUpdatedAt = Instant.parse("2026-07-04T00:00:00Z"),
        )
        whenever(progress.findByUserIdAndDocumentId(userId, docId)).doReturn(server)

        val res = service.push(
            userId,
            SyncPushRequest(
                PushChanges(
                    progress = listOf(
                        PushProgress(
                            documentId = docId, location = loc, percent = BigDecimal("10.00"),
                            clientUpdatedAt = Instant.parse("2026-07-01T00:00:00Z"), // 과거
                        ),
                    ),
                ),
            ),
        )

        assertEquals(BigDecimal("70.00"), server.percent) // 롤백 안 됨
        assertTrue(res.applied.isEmpty())
    }

    // ── 실패 케이스: 소유권/게이트 ──

    @Test
    fun `push 미소유 문서 - NOT_FOUND`() {
        whenever(documents.findByIdAndUserIdAndDeletedAtIsNull(docId, userId)).doReturn(null)

        val ex = assertThrows<ApiException> {
            service.push(
                userId,
                SyncPushRequest(
                    PushChanges(
                        highlights = listOf(
                            PushHighlight(documentId = docId, location = loc, selectedText = "x"),
                        ),
                    ),
                ),
            )
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.code)
        verify(highlights, never()).save(any())
    }

    @Test
    fun `게이트 - require-pro가 켜지면 FORBIDDEN (P2 tier 검사 자리)`() {
        val gated = SyncService(
            highlights, notes, bookmarks, progress, documents, objectMapper, requirePro = true,
        )
        val ex = assertThrows<ApiException> { gated.changes(userId, null) }
        assertEquals(ErrorCode.FORBIDDEN, ex.code)
        assertNull(runCatching { gated.push(userId, SyncPushRequest()) }.getOrNull())
    }
}
