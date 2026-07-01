package com.readmind.annotation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.readmind.common.ApiException
import com.readmind.common.ErrorCode
import com.readmind.document.DocumentRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

/**
 * 주석/진행률 CRUD (명세서 §4.3). 모든 접근은 소유권(user_id) 검증 후 수행(§3).
 * - documents/{id} 하위(목록·생성·진행률): 대상 문서 소유권을 먼저 검증(미소유 → NOT_FOUND).
 * - /highlights|notes|bookmarks/{id} : 주석 자체를 user_id 스코프로 조회(미소유 → NOT_FOUND).
 * location 은 포맷 무관 JSONB — 재모델링 없이 raw JSON 으로 통과·보관한다(§3).
 * 삭제는 소프트 삭제(deleted_at) + version 증가(동기화 LWW·톰스톤 전파, §4.5).
 */
@Service
class AnnotationService(
    private val documents: DocumentRepository,
    private val highlights: HighlightRepository,
    private val notes: NoteRepository,
    private val bookmarks: BookmarkRepository,
    private val progresses: ReadingProgressRepository,
    private val mapper: ObjectMapper,
) {

    // ── 하이라이트 ──
    @Transactional(readOnly = true)
    fun listHighlights(userId: Long, documentId: Long): List<HighlightDto> {
        requireOwnedDocument(userId, documentId)
        return highlights
            .findByDocumentIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(documentId, userId)
            .map { it.toDto() }
    }

    @Transactional
    fun createHighlight(userId: Long, documentId: Long, req: CreateHighlightRequest): HighlightDto {
        requireOwnedDocument(userId, documentId)
        val saved = highlights.save(
            Highlight(
                userId = userId,
                documentId = documentId,
                pageNo = req.pageNo,
                location = toJson(req.location!!),
                selectedText = req.selectedText,
                color = req.color ?: "yellow",
                note = req.note,
                tags = req.tags?.toTypedArray(),
            ),
        )
        return saved.toDto()
    }

    @Transactional
    fun updateHighlight(userId: Long, id: Long, req: UpdateHighlightRequest): HighlightDto {
        val h = highlights.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            ?: throw notFound("하이라이트")
        req.color?.let { h.color = it }
        req.note?.let { h.note = it }
        req.tags?.let { h.tags = it.toTypedArray() }
        h.version += 1
        return highlights.save(h).toDto()
    }

    @Transactional
    fun deleteHighlight(userId: Long, id: Long) {
        val h = highlights.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            ?: throw notFound("하이라이트")
        h.deletedAt = Instant.now()
        h.version += 1
        highlights.save(h)
    }

    /**
     * 전 문서 횡단 하이라이트 검색 (§4.3, 유료 핵심). 본인 소유만.
     * q/tag 는 blank 면 무시(둘 다 없으면 전체 하이라이트). 결과에 문서 제목을 붙인다.
     */
    @Transactional(readOnly = true)
    fun searchHighlights(
        userId: Long,
        q: String?,
        tag: String?,
        pageable: Pageable,
    ): HighlightSearchResponse {
        val page = highlights.search(
            userId,
            q?.trim()?.takeIf { it.isNotEmpty() },
            tag?.trim()?.takeIf { it.isNotEmpty() },
            pageable,
        )
        val titles = documents
            .findByIdInAndUserId(page.content.map { it.documentId }.toSet(), userId)
            .associate { it.id to it.title }
        val items = page.content.map { h ->
            HighlightSearchItem(
                id = h.id!!,
                documentId = h.documentId,
                documentTitle = titles[h.documentId] ?: "",
                pageNo = h.pageNo,
                selectedText = h.selectedText,
                color = h.color,
                note = h.note,
                tags = h.tags?.toList() ?: emptyList(),
                createdAt = h.createdAt,
            )
        }
        return HighlightSearchResponse(items, page.totalElements, page.hasNext())
    }

    // ── 메모 ──
    @Transactional(readOnly = true)
    fun listNotes(userId: Long, documentId: Long): List<NoteDto> {
        requireOwnedDocument(userId, documentId)
        return notes
            .findByDocumentIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(documentId, userId)
            .map { it.toDto() }
    }

    @Transactional
    fun createNote(userId: Long, documentId: Long, req: CreateNoteRequest): NoteDto {
        requireOwnedDocument(userId, documentId)
        val saved = notes.save(
            Note(userId = userId, documentId = documentId, pageNo = req.pageNo, body = req.body),
        )
        return saved.toDto()
    }

    @Transactional
    fun updateNote(userId: Long, id: Long, req: UpdateNoteRequest): NoteDto {
        val n = notes.findByIdAndUserIdAndDeletedAtIsNull(id, userId) ?: throw notFound("메모")
        req.body?.let { n.body = it }
        req.pageNo?.let { n.pageNo = it }
        n.version += 1
        return notes.save(n).toDto()
    }

    @Transactional
    fun deleteNote(userId: Long, id: Long) {
        val n = notes.findByIdAndUserIdAndDeletedAtIsNull(id, userId) ?: throw notFound("메모")
        n.deletedAt = Instant.now()
        n.version += 1
        notes.save(n)
    }

    // ── 북마크 ──
    @Transactional(readOnly = true)
    fun listBookmarks(userId: Long, documentId: Long): List<BookmarkDto> {
        requireOwnedDocument(userId, documentId)
        return bookmarks
            .findByDocumentIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(documentId, userId)
            .map { it.toDto() }
    }

    @Transactional
    fun createBookmark(userId: Long, documentId: Long, req: CreateBookmarkRequest): BookmarkDto {
        requireOwnedDocument(userId, documentId)
        val saved = bookmarks.save(
            Bookmark(
                userId = userId,
                documentId = documentId,
                location = toJson(req.location!!),
                label = req.label,
            ),
        )
        return saved.toDto()
    }

    @Transactional
    fun deleteBookmark(userId: Long, id: Long) {
        val b = bookmarks.findByIdAndUserIdAndDeletedAtIsNull(id, userId) ?: throw notFound("북마크")
        b.deletedAt = Instant.now()
        b.version += 1
        bookmarks.save(b)
    }

    // ── 진행률 (문서당 1개, upsert) ──
    @Transactional(readOnly = true)
    fun getProgress(userId: Long, documentId: Long): ProgressDto? {
        requireOwnedDocument(userId, documentId)
        return progresses.findByUserIdAndDocumentId(userId, documentId)?.toDto()
    }

    @Transactional
    fun putProgress(userId: Long, documentId: Long, req: UpdateProgressRequest): ProgressDto {
        requireOwnedDocument(userId, documentId)
        val existing = progresses.findByUserIdAndDocumentId(userId, documentId)
        val entity = existing?.apply {
            location = toJson(req.location!!)
            percent = req.percent!!
        } ?: ReadingProgress(
            userId = userId,
            documentId = documentId,
            location = toJson(req.location!!),
            percent = req.percent!!,
        )
        return progresses.save(entity).toDto()
    }

    // ── 내부 ──
    private fun requireOwnedDocument(userId: Long, documentId: Long) {
        documents.findByIdAndUserIdAndDeletedAtIsNull(documentId, userId)
            ?: throw notFound("문서")
    }

    private fun notFound(what: String) = ApiException(ErrorCode.NOT_FOUND, "$what 를 찾을 수 없습니다.")

    private fun toJson(node: JsonNode): String = mapper.writeValueAsString(node)
    private fun toNode(json: String): JsonNode = mapper.readTree(json)

    private fun Highlight.toDto() = HighlightDto(
        id = id!!,
        documentId = documentId,
        pageNo = pageNo,
        location = toNode(location),
        selectedText = selectedText,
        color = color,
        note = note,
        aiSuggested = aiSuggested,
        tags = tags?.toList() ?: emptyList(),
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun Note.toDto() = NoteDto(
        id = id!!,
        documentId = documentId,
        pageNo = pageNo,
        body = body,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun Bookmark.toDto() = BookmarkDto(
        id = id!!,
        documentId = documentId,
        location = toNode(location),
        label = label,
        version = version,
        createdAt = createdAt,
    )

    private fun ReadingProgress.toDto() = ProgressDto(
        documentId = documentId,
        location = toNode(location),
        percent = percent,
        updatedAt = updatedAt,
    )
}
