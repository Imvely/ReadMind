package com.readmind.annotation

import org.springframework.data.jpa.repository.JpaRepository

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
