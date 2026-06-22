package com.readmind.document

import com.readmind.common.ApiException
import com.readmind.common.ErrorCode
import com.readmind.document.storage.DocumentStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

/** DocumentService 단위 테스트 — 소유권/상태전이/파싱트리거 규칙. DB·S3 없이 mock. */
class DocumentServiceTest {

    private val documents = mock<DocumentRepository>()
    private val storage = mock<DocumentStorage>()
    private val parseRunner = mock<DocumentParseRunner>()
    private val service = DocumentService(documents, storage, parseRunner)

    private fun doc(id: Long = 1L, userId: Long = 10L, status: ParseStatus = ParseStatus.PENDING) =
        Document(
            userId = userId, title = "t", format = "PDF",
            storageKey = "users/$userId/x.pdf", fileSize = 100,
        ).also { it.id = id; it.parseStatus = status }

    @Test
    fun `create - PENDING row 저장 + presigned PUT URL 반환`() {
        whenever(storage.newStorageKey(eq(10L), any())).doReturn("users/10/abc.pdf")
        whenever(documents.save(any())).doReturn(doc(id = 5L))
        whenever(storage.presignPut("users/10/abc.pdf")).doReturn("https://put-url")

        val res = service.create(10L, CreateDocumentRequest(title = "논문", format = "pdf", fileSize = 100))

        assertEquals(5L, res.documentId)
        assertEquals("https://put-url", res.uploadUrl)
    }

    @Test
    fun `create - 지원하지 않는 포맷은 VALIDATION`() {
        val ex = assertThrows<ApiException> {
            service.create(10L, CreateDocumentRequest(title = "t", format = "mp3", fileSize = 100))
        }
        assertEquals(ErrorCode.VALIDATION, ex.code)
        verify(documents, never()).save(any())
    }

    @Test
    fun `complete - PARSING 전이 + 비동기 파싱 트리거`() {
        whenever(documents.findByIdAndUserIdAndDeletedAtIsNull(1L, 10L)).doReturn(doc())
        whenever(documents.save(any())).doReturn(doc(status = ParseStatus.PARSING))

        val res = service.complete(10L, 1L)

        assertEquals(ParseStatus.PARSING, res.parseStatus)
        verify(parseRunner, times(1)).run(1L)
    }

    @Test
    fun `complete - 이미 READY면 재파싱 안 함(캐싱)`() {
        whenever(documents.findByIdAndUserIdAndDeletedAtIsNull(1L, 10L))
            .doReturn(doc(status = ParseStatus.READY))

        val res = service.complete(10L, 1L)

        assertEquals(ParseStatus.READY, res.parseStatus)
        verify(parseRunner, never()).run(any())
    }

    @Test
    fun `complete - 다른 사용자의 문서면 NOT_FOUND(소유권)`() {
        whenever(documents.findByIdAndUserIdAndDeletedAtIsNull(1L, 99L)).doReturn(null)

        val ex = assertThrows<ApiException> { service.complete(99L, 1L) }
        assertEquals(ErrorCode.NOT_FOUND, ex.code)
        verify(parseRunner, never()).run(any())
    }

    @Test
    fun `content - 소유자면 presigned GET URL`() {
        whenever(documents.findByIdAndUserIdAndDeletedAtIsNull(1L, 10L)).doReturn(doc())
        whenever(storage.presignGet("users/10/x.pdf")).doReturn("https://get-url")

        assertEquals("https://get-url", service.content(10L, 1L).url)
    }

    @Test
    fun `content - 비소유자면 NOT_FOUND이고 presign 안 함`() {
        whenever(documents.findByIdAndUserIdAndDeletedAtIsNull(1L, 99L)).doReturn(null)

        assertThrows<ApiException> { service.content(99L, 1L) }
        verify(storage, never()).presignGet(any())
    }

    @Test
    fun `delete - soft delete(deletedAt 설정)`() {
        val d = doc()
        whenever(documents.findByIdAndUserIdAndDeletedAtIsNull(1L, 10L)).doReturn(d)
        whenever(documents.save(any())).doReturn(d)

        service.delete(10L, 1L)

        assertEquals(true, d.deletedAt != null)
        verify(documents).save(d)
    }

    @Test
    fun `get - 비소유자면 NOT_FOUND`() {
        whenever(documents.findByIdAndUserIdAndDeletedAtIsNull(1L, 99L)).doReturn(null)
        val ex = assertThrows<ApiException> { service.get(99L, 1L) }
        assertEquals(ErrorCode.NOT_FOUND, ex.code)
    }
}
