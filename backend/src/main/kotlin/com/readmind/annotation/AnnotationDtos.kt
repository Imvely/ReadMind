package com.readmind.annotation

import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant

// ── 하이라이트 ──
data class CreateHighlightRequest(
    val pageNo: Int? = null,
    /** 포맷 무관 위치(§3): PDF={type,page,rects[]} / EPUB={type,cfi}. 그대로 보관·통과. */
    @field:NotNull val location: JsonNode? = null,
    @field:NotBlank val selectedText: String = "",
    val color: String? = null,
    val note: String? = null,
    val tags: List<String>? = null,
)

/** 부분 수정 — 지정한 필드만 갱신(색/메모/태그). */
data class UpdateHighlightRequest(
    val color: String? = null,
    val note: String? = null,
    val tags: List<String>? = null,
)

data class HighlightDto(
    val id: Long,
    val documentId: Long,
    val pageNo: Int?,
    val location: JsonNode,
    val selectedText: String,
    val color: String,
    val note: String?,
    val aiSuggested: Boolean,
    val tags: List<String>,
    val version: Int,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

// ── 메모 ──
data class CreateNoteRequest(
    val pageNo: Int? = null,
    @field:NotBlank val body: String = "",
)

data class UpdateNoteRequest(
    val pageNo: Int? = null,
    val body: String? = null,
)

data class NoteDto(
    val id: Long,
    val documentId: Long,
    val pageNo: Int?,
    val body: String,
    val version: Int,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

// ── 북마크 ──
data class CreateBookmarkRequest(
    @field:NotNull val location: JsonNode? = null,
    val label: String? = null,
)

data class BookmarkDto(
    val id: Long,
    val documentId: Long,
    val location: JsonNode,
    val label: String?,
    val version: Int,
    val createdAt: Instant?,
)

// ── 하이라이트 통합 검색 (§4.3, 유료 핵심) ──
data class HighlightSearchItem(
    val id: Long,
    val documentId: Long,
    val documentTitle: String,
    val pageNo: Int?,
    val selectedText: String,
    val color: String,
    val note: String?,
    val tags: List<String>,
    val createdAt: Instant?,
)

data class HighlightSearchResponse(
    val items: List<HighlightSearchItem>,
    val totalElements: Long,
    val hasNext: Boolean,
)

// ── 진행률 ──
data class UpdateProgressRequest(
    @field:NotNull val location: JsonNode? = null,
    @field:NotNull
    @field:DecimalMin("0.0")
    @field:DecimalMax("100.0")
    val percent: BigDecimal? = null,
)

data class ProgressDto(
    val documentId: Long,
    val location: JsonNode,
    val percent: BigDecimal,
    val updatedAt: Instant?,
)
