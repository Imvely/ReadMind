package com.readmind.annotation

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 주석 리포지토리 — 모든 조회는 user_id 스코프 + soft-delete 제외를 강제한다(§3 데이터 격리).
 * id 단독 조회는 두지 않는다 — 항상 userId 와 함께.
 */
interface HighlightRepository : JpaRepository<Highlight, Long> {
    fun findByIdAndUserIdAndDeletedAtIsNull(id: Long, userId: Long): Highlight?
    fun findByDocumentIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(
        documentId: Long,
        userId: Long,
    ): List<Highlight>

    /**
     * 전 문서 횡단 하이라이트 검색 (명세서 §4.3, 유료 핵심). 본인 소유만.
     * q: selected_text/note 부분일치(대소문자 무시, ILIKE). tag: tags 배열 원소 일치.
     * 둘 다 null/blank 면 전체(사용자 스코프). ILIKE·ANY 는 Postgres 전용이라 네이티브.
     */
    @Query(
        value = """
            SELECT h.* FROM highlights h
            JOIN documents d ON d.id = h.document_id AND d.user_id = h.user_id
                 AND d.deleted_at IS NULL
            WHERE h.user_id = :userId AND h.deleted_at IS NULL
              AND (CAST(:q AS text) IS NULL
                   OR h.selected_text ILIKE CONCAT('%', CAST(:q AS text), '%')
                   OR h.note ILIKE CONCAT('%', CAST(:q AS text), '%'))
              AND (CAST(:tag AS text) IS NULL OR CAST(:tag AS text) = ANY(h.tags))
            ORDER BY h.created_at DESC
        """,
        countQuery = """
            SELECT count(*) FROM highlights h
            JOIN documents d ON d.id = h.document_id AND d.user_id = h.user_id
                 AND d.deleted_at IS NULL
            WHERE h.user_id = :userId AND h.deleted_at IS NULL
              AND (CAST(:q AS text) IS NULL
                   OR h.selected_text ILIKE CONCAT('%', CAST(:q AS text), '%')
                   OR h.note ILIKE CONCAT('%', CAST(:q AS text), '%'))
              AND (CAST(:tag AS text) IS NULL OR CAST(:tag AS text) = ANY(h.tags))
        """,
        nativeQuery = true,
    )
    fun search(
        @Param("userId") userId: Long,
        @Param("q") q: String?,
        @Param("tag") tag: String?,
        pageable: Pageable,
    ): Page<Highlight>
}

interface NoteRepository : JpaRepository<Note, Long> {
    fun findByIdAndUserIdAndDeletedAtIsNull(id: Long, userId: Long): Note?
    fun findByDocumentIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(
        documentId: Long,
        userId: Long,
    ): List<Note>
}

interface BookmarkRepository : JpaRepository<Bookmark, Long> {
    fun findByIdAndUserIdAndDeletedAtIsNull(id: Long, userId: Long): Bookmark?
    fun findByDocumentIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(
        documentId: Long,
        userId: Long,
    ): List<Bookmark>
}

interface ReadingProgressRepository : JpaRepository<ReadingProgress, ReadingProgressId> {
    fun findByUserIdAndDocumentId(userId: Long, documentId: Long): ReadingProgress?
}
