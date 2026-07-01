package com.readmind.annotation

import com.readmind.common.ApiException
import com.readmind.common.ApiResponse
import com.readmind.common.ErrorCode
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 주석/진행률 API (명세서 §4.3). 하이라이트 통합 검색(/highlights/search)은 be-highlight-search 소관.
 * 모든 엔드포인트는 소유권 검증 후 동작(§3) — 서비스에서 강제.
 */
@RestController
class AnnotationController(
    private val service: AnnotationService,
) {

    // ── 하이라이트 ──
    @GetMapping("/documents/{id}/highlights")
    fun listHighlights(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable id: Long,
    ): ApiResponse<List<HighlightDto>> =
        ApiResponse.ok(service.listHighlights(requireUser(userId), id))

    @PostMapping("/documents/{id}/highlights")
    fun createHighlight(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable id: Long,
        @Valid @RequestBody req: CreateHighlightRequest,
    ): ApiResponse<HighlightDto> =
        ApiResponse.ok(service.createHighlight(requireUser(userId), id, req))

    @PatchMapping("/highlights/{hid}")
    fun updateHighlight(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable hid: Long,
        @Valid @RequestBody req: UpdateHighlightRequest,
    ): ApiResponse<HighlightDto> =
        ApiResponse.ok(service.updateHighlight(requireUser(userId), hid, req))

    @DeleteMapping("/highlights/{hid}")
    fun deleteHighlight(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable hid: Long,
    ): ApiResponse<Unit> {
        service.deleteHighlight(requireUser(userId), hid)
        return ApiResponse.ok(Unit)
    }

    // ── 메모 ──
    @GetMapping("/documents/{id}/notes")
    fun listNotes(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable id: Long,
    ): ApiResponse<List<NoteDto>> =
        ApiResponse.ok(service.listNotes(requireUser(userId), id))

    @PostMapping("/documents/{id}/notes")
    fun createNote(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable id: Long,
        @Valid @RequestBody req: CreateNoteRequest,
    ): ApiResponse<NoteDto> =
        ApiResponse.ok(service.createNote(requireUser(userId), id, req))

    @PatchMapping("/notes/{nid}")
    fun updateNote(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable nid: Long,
        @Valid @RequestBody req: UpdateNoteRequest,
    ): ApiResponse<NoteDto> =
        ApiResponse.ok(service.updateNote(requireUser(userId), nid, req))

    @DeleteMapping("/notes/{nid}")
    fun deleteNote(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable nid: Long,
    ): ApiResponse<Unit> {
        service.deleteNote(requireUser(userId), nid)
        return ApiResponse.ok(Unit)
    }

    // ── 북마크 ──
    @GetMapping("/documents/{id}/bookmarks")
    fun listBookmarks(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable id: Long,
    ): ApiResponse<List<BookmarkDto>> =
        ApiResponse.ok(service.listBookmarks(requireUser(userId), id))

    @PostMapping("/documents/{id}/bookmarks")
    fun createBookmark(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable id: Long,
        @Valid @RequestBody req: CreateBookmarkRequest,
    ): ApiResponse<BookmarkDto> =
        ApiResponse.ok(service.createBookmark(requireUser(userId), id, req))

    @DeleteMapping("/bookmarks/{bid}")
    fun deleteBookmark(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable bid: Long,
    ): ApiResponse<Unit> {
        service.deleteBookmark(requireUser(userId), bid)
        return ApiResponse.ok(Unit)
    }

    // ── 진행률 ──
    @GetMapping("/documents/{id}/progress")
    fun getProgress(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable id: Long,
    ): ApiResponse<ProgressDto?> =
        ApiResponse.ok(service.getProgress(requireUser(userId), id))

    @PutMapping("/documents/{id}/progress")
    fun putProgress(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable id: Long,
        @Valid @RequestBody req: UpdateProgressRequest,
    ): ApiResponse<ProgressDto> =
        ApiResponse.ok(service.putProgress(requireUser(userId), id, req))

    private fun requireUser(userId: Long?): Long =
        userId ?: throw ApiException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.")
}
