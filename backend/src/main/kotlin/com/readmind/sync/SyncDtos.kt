package com.readmind.sync

import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant

/**
 * 동기화 계약 (명세서 §4.5 상세, 2026-07-05 확정).
 * 서버 상태 DTO(Sync*Dto)는 changes 응답과 conflicts.server 양쪽에 쓰인다.
 * tombstone은 deletedAt non-null 로 표현 — 클라이언트는 로컬에서 해당 항목을 지운다.
 */

// ── 서버 상태 ──
data class SyncHighlightDto(
    val id: Long,
    val documentId: Long,
    val pageNo: Int?,
    val location: JsonNode,
    val selectedText: String,
    val color: String,
    val note: String?,
    val tags: List<String>,
    val version: Int,
    val clientUpdatedAt: Instant?,
    val updatedAt: Instant?,
    val deletedAt: Instant?,
)

data class SyncNoteDto(
    val id: Long,
    val documentId: Long,
    val pageNo: Int?,
    val body: String,
    val version: Int,
    val clientUpdatedAt: Instant?,
    val updatedAt: Instant?,
    val deletedAt: Instant?,
)

data class SyncBookmarkDto(
    val id: Long,
    val documentId: Long,
    val location: JsonNode,
    val label: String?,
    val version: Int,
    val clientUpdatedAt: Instant?,
    val updatedAt: Instant?,
    val deletedAt: Instant?,
)

/** progress는 version/tombstone 없음(§4.5 예외) — clientUpdatedAt LWW만. */
data class SyncProgressDto(
    val documentId: Long,
    val location: JsonNode,
    val percent: BigDecimal,
    val clientUpdatedAt: Instant?,
    val updatedAt: Instant?,
)

data class SyncChanges(
    val highlights: List<SyncHighlightDto> = emptyList(),
    val notes: List<SyncNoteDto> = emptyList(),
    val bookmarks: List<SyncBookmarkDto> = emptyList(),
    val progress: List<SyncProgressDto> = emptyList(),
)

data class SyncChangesResponse(
    /** 다음 /sync/changes 호출의 since 로 쓸 서버 시각. */
    val cursor: Instant,
    val changes: SyncChanges,
)

// ── push 입력 ──
data class PushHighlight(
    /** 클라 로컬 id(uuid 등) — 신규 insert 시 applied 매핑으로 돌려준다. */
    val clientId: String? = null,
    val id: Long? = null,
    @field:NotNull val documentId: Long? = null,
    val pageNo: Int? = null,
    val location: JsonNode? = null,
    val selectedText: String? = null,
    val color: String? = null,
    val note: String? = null,
    val tags: List<String>? = null,
    val version: Int = 1,
    val clientUpdatedAt: Instant? = null,
    val deleted: Boolean = false,
)

data class PushNote(
    val clientId: String? = null,
    val id: Long? = null,
    @field:NotNull val documentId: Long? = null,
    val pageNo: Int? = null,
    val body: String? = null,
    val version: Int = 1,
    val clientUpdatedAt: Instant? = null,
    val deleted: Boolean = false,
)

data class PushBookmark(
    val clientId: String? = null,
    val id: Long? = null,
    @field:NotNull val documentId: Long? = null,
    val location: JsonNode? = null,
    val label: String? = null,
    val version: Int = 1,
    val clientUpdatedAt: Instant? = null,
    val deleted: Boolean = false,
)

data class PushProgress(
    @field:NotNull val documentId: Long? = null,
    @field:NotNull val location: JsonNode? = null,
    val percent: BigDecimal = BigDecimal.ZERO,
    val clientUpdatedAt: Instant? = null,
)

data class PushChanges(
    val highlights: List<PushHighlight> = emptyList(),
    val notes: List<PushNote> = emptyList(),
    val bookmarks: List<PushBookmark> = emptyList(),
    val progress: List<PushProgress> = emptyList(),
)

data class SyncPushRequest(
    val changes: PushChanges = PushChanges(),
)

data class AppliedDto(
    val entity: String, // highlight | note | bookmark | progress
    val clientId: String?,
    val id: Long,
    val version: Int,
)

data class ConflictDto(
    val entity: String,
    val id: Long,
    /** 서버 현재 상태(Sync*Dto) — 클라이언트가 로컬을 이 값으로 교체한다. */
    val server: Any,
)

data class SyncPushResponse(
    val applied: List<AppliedDto>,
    val conflicts: List<ConflictDto>,
    val cursor: Instant,
)
