package com.readmind.sync

import com.fasterxml.jackson.databind.ObjectMapper
import com.readmind.annotation.Bookmark
import com.readmind.annotation.BookmarkRepository
import com.readmind.annotation.Highlight
import com.readmind.annotation.HighlightRepository
import com.readmind.annotation.Note
import com.readmind.annotation.NoteRepository
import com.readmind.annotation.ReadingProgress
import com.readmind.annotation.ReadingProgressRepository
import com.readmind.common.ApiException
import com.readmind.common.ErrorCode
import com.readmind.document.DocumentRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 증분 동기화 (명세서 §4.5). LWW: 클라 version ≥ 서버 → 적용(version=서버+1),
 * 서버가 크면 clientUpdatedAt 비교 — 클라가 최신이면 적용, 아니면 conflicts로 서버 상태 반환.
 * 삭제는 tombstone(deleted_at) — 물리 삭제 금지. 모든 항목 소유권(user_id) 강제(§3).
 * 게이트: 스펙상 유료 전용이나 be-quota-tiers(P2) 전까지 SYNC_REQUIRE_PRO=false로 전 티어 허용.
 */
@Service
class SyncService(
    private val highlights: HighlightRepository,
    private val notes: NoteRepository,
    private val bookmarks: BookmarkRepository,
    private val progress: ReadingProgressRepository,
    private val documents: DocumentRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${app.sync.require-pro:false}") private val requirePro: Boolean,
) {

    @Transactional(readOnly = true)
    fun changes(userId: Long, since: Instant?): SyncChangesResponse {
        ensureAllowed()
        val cursor = Instant.now()
        return SyncChangesResponse(
            cursor = cursor,
            changes = SyncChanges(
                highlights = (since?.let { highlights.findByUserIdAndUpdatedAtAfter(userId, it) }
                    ?: highlights.findByUserId(userId)).map { it.toSyncDto() },
                notes = (since?.let { notes.findByUserIdAndUpdatedAtAfter(userId, it) }
                    ?: notes.findByUserId(userId)).map { it.toSyncDto() },
                bookmarks = (since?.let { bookmarks.findByUserIdAndUpdatedAtAfter(userId, it) }
                    ?: bookmarks.findByUserId(userId)).map { it.toSyncDto() },
                progress = (since?.let { progress.findByUserIdAndUpdatedAtAfter(userId, it) }
                    ?: progress.findByUserId(userId)).map { it.toSyncDto() },
            ),
        )
    }

    @Transactional
    fun push(userId: Long, req: SyncPushRequest): SyncPushResponse {
        ensureAllowed()
        val applied = mutableListOf<AppliedDto>()
        val conflicts = mutableListOf<ConflictDto>()

        req.changes.highlights.forEach { pushHighlight(userId, it, applied, conflicts) }
        req.changes.notes.forEach { pushNote(userId, it, applied, conflicts) }
        req.changes.bookmarks.forEach { pushBookmark(userId, it, applied, conflicts) }
        req.changes.progress.forEach { pushProgress(userId, it, applied) }

        return SyncPushResponse(applied = applied, conflicts = conflicts, cursor = Instant.now())
    }

    // ── 엔티티별 push ──

    private fun pushHighlight(
        userId: Long,
        p: PushHighlight,
        applied: MutableList<AppliedDto>,
        conflicts: MutableList<ConflictDto>,
    ) {
        val docId = requireOwnedDocument(userId, p.documentId)
        val existing = p.id?.let {
            highlights.findByIdAndUserId(it, userId)
                ?: throw ApiException(ErrorCode.NOT_FOUND, "하이라이트를 찾을 수 없습니다: ${p.id}")
        }
        if (existing == null) {
            val saved = highlights.save(
                Highlight(
                    userId = userId,
                    documentId = docId,
                    pageNo = p.pageNo,
                    location = (p.location ?: missing("location")).toString(),
                    selectedText = p.selectedText ?: missing("selectedText"),
                    color = p.color ?: "yellow",
                    note = p.note,
                    tags = p.tags?.toTypedArray(),
                    clientUpdatedAt = p.clientUpdatedAt,
                    version = maxOf(1, p.version),
                    deletedAt = if (p.deleted) Instant.now() else null,
                ),
            )
            applied += AppliedDto("highlight", p.clientId, saved.id!!, saved.version)
            return
        }
        if (!clientWins(p.version, existing.version, p.clientUpdatedAt, existing.clientUpdatedAt)) {
            conflicts += ConflictDto("highlight", existing.id!!, existing.toSyncDto())
            return
        }
        existing.apply {
            p.location?.let { location = it.toString() }
            p.selectedText?.let { selectedText = it }
            p.color?.let { color = it }
            note = p.note ?: note
            p.tags?.let { tags = it.toTypedArray() }
            pageNo = p.pageNo ?: pageNo
            clientUpdatedAt = p.clientUpdatedAt
            version += 1
            if (p.deleted) deletedAt = Instant.now()
        }
        applied += AppliedDto("highlight", p.clientId, existing.id!!, existing.version)
    }

    private fun pushNote(
        userId: Long,
        p: PushNote,
        applied: MutableList<AppliedDto>,
        conflicts: MutableList<ConflictDto>,
    ) {
        val docId = requireOwnedDocument(userId, p.documentId)
        val existing = p.id?.let {
            notes.findByIdAndUserId(it, userId)
                ?: throw ApiException(ErrorCode.NOT_FOUND, "메모를 찾을 수 없습니다: ${p.id}")
        }
        if (existing == null) {
            val saved = notes.save(
                Note(
                    userId = userId,
                    documentId = docId,
                    pageNo = p.pageNo,
                    body = p.body ?: missing("body"),
                    clientUpdatedAt = p.clientUpdatedAt,
                    version = maxOf(1, p.version),
                    deletedAt = if (p.deleted) Instant.now() else null,
                ),
            )
            applied += AppliedDto("note", p.clientId, saved.id!!, saved.version)
            return
        }
        if (!clientWins(p.version, existing.version, p.clientUpdatedAt, existing.clientUpdatedAt)) {
            conflicts += ConflictDto("note", existing.id!!, existing.toSyncDto())
            return
        }
        existing.apply {
            p.body?.let { body = it }
            pageNo = p.pageNo ?: pageNo
            clientUpdatedAt = p.clientUpdatedAt
            version += 1
            if (p.deleted) deletedAt = Instant.now()
        }
        applied += AppliedDto("note", p.clientId, existing.id!!, existing.version)
    }

    private fun pushBookmark(
        userId: Long,
        p: PushBookmark,
        applied: MutableList<AppliedDto>,
        conflicts: MutableList<ConflictDto>,
    ) {
        val docId = requireOwnedDocument(userId, p.documentId)
        val existing = p.id?.let {
            bookmarks.findByIdAndUserId(it, userId)
                ?: throw ApiException(ErrorCode.NOT_FOUND, "북마크를 찾을 수 없습니다: ${p.id}")
        }
        if (existing == null) {
            val saved = bookmarks.save(
                Bookmark(
                    userId = userId,
                    documentId = docId,
                    location = (p.location ?: missing("location")).toString(),
                    label = p.label,
                    clientUpdatedAt = p.clientUpdatedAt,
                    version = maxOf(1, p.version),
                    deletedAt = if (p.deleted) Instant.now() else null,
                ),
            )
            applied += AppliedDto("bookmark", p.clientId, saved.id!!, saved.version)
            return
        }
        if (!clientWins(p.version, existing.version, p.clientUpdatedAt, existing.clientUpdatedAt)) {
            conflicts += ConflictDto("bookmark", existing.id!!, existing.toSyncDto())
            return
        }
        existing.apply {
            p.location?.let { location = it.toString() }
            label = p.label ?: label
            clientUpdatedAt = p.clientUpdatedAt
            version += 1
            if (p.deleted) deletedAt = Instant.now()
        }
        applied += AppliedDto("bookmark", p.clientId, existing.id!!, existing.version)
    }

    /** progress: (user,document) 업서트, clientUpdatedAt LWW만(§4.5 예외). 충돌 반환 없음 — 최신이 이긴다. */
    private fun pushProgress(userId: Long, p: PushProgress, applied: MutableList<AppliedDto>) {
        val docId = requireOwnedDocument(userId, p.documentId)
        val existing = progress.findByUserIdAndDocumentId(userId, docId)
        if (existing == null) {
            progress.save(
                ReadingProgress(
                    userId = userId,
                    documentId = docId,
                    location = (p.location ?: missing("location")).toString(),
                    percent = p.percent,
                    clientUpdatedAt = p.clientUpdatedAt,
                ),
            )
            applied += AppliedDto("progress", null, docId, 1)
            return
        }
        val incoming = p.clientUpdatedAt ?: Instant.now()
        val current = existing.clientUpdatedAt
        if (current == null || incoming.isAfter(current)) {
            existing.apply {
                location = (p.location ?: missing("location")).toString()
                percent = p.percent
                clientUpdatedAt = p.clientUpdatedAt
            }
            applied += AppliedDto("progress", null, docId, 1)
        }
        // 서버가 더 최신이면 조용히 무시 — 클라는 changes 로 최신을 받는다.
    }

    // ── 공통 ──

    /**
     * LWW 판정(§4.5): 클라 version ≥ 서버 → 적용. 서버가 크면 clientUpdatedAt 비교 —
     * 클라 쪽이 더 최근 수정이면 적용(늦게 쓴 쪽 승리), 아니면 충돌.
     */
    private fun clientWins(
        clientVersion: Int,
        serverVersion: Int,
        clientUpdatedAt: Instant?,
        serverClientUpdatedAt: Instant?,
    ): Boolean {
        if (clientVersion >= serverVersion) return true
        if (clientUpdatedAt == null) return false
        return serverClientUpdatedAt == null || clientUpdatedAt.isAfter(serverClientUpdatedAt)
    }

    /** documentId 소유권 검증(§3) — 미소유/미존재는 NOT_FOUND(존재 여부 노출 금지). */
    private fun requireOwnedDocument(userId: Long, documentId: Long?): Long {
        val id = documentId ?: throw ApiException(ErrorCode.VALIDATION, "documentId는 필수입니다.")
        documents.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "문서를 찾을 수 없습니다: $id")
        return id
    }

    private fun ensureAllowed() {
        // P2(be-quota-tiers)에서 tier 검사로 대체 — 지금은 플래그가 켜지면 전면 차단(§4.5 게이트 자리).
        if (requirePro) {
            throw ApiException(ErrorCode.FORBIDDEN, "클라우드 동기화는 유료 플랜 전용입니다.")
        }
    }

    private fun missing(field: String): Nothing =
        throw ApiException(ErrorCode.VALIDATION, "신규 항목에 $field 가 없습니다.")

    // ── DTO 매핑 ──

    private fun Highlight.toSyncDto() = SyncHighlightDto(
        id = id!!,
        documentId = documentId,
        pageNo = pageNo,
        location = objectMapper.readTree(location),
        selectedText = selectedText,
        color = color,
        note = note,
        tags = tags?.toList() ?: emptyList(),
        version = version,
        clientUpdatedAt = clientUpdatedAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun Note.toSyncDto() = SyncNoteDto(
        id = id!!,
        documentId = documentId,
        pageNo = pageNo,
        body = body,
        version = version,
        clientUpdatedAt = clientUpdatedAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun Bookmark.toSyncDto() = SyncBookmarkDto(
        id = id!!,
        documentId = documentId,
        location = objectMapper.readTree(location),
        label = label,
        version = version,
        clientUpdatedAt = clientUpdatedAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun ReadingProgress.toSyncDto() = SyncProgressDto(
        documentId = documentId,
        location = objectMapper.readTree(location),
        percent = percent,
        clientUpdatedAt = clientUpdatedAt,
        updatedAt = updatedAt,
    )
}
